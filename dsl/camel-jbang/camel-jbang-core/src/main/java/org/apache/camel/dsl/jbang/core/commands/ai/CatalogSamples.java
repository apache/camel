/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Validated Camel YAML DSL samples per EIP (and per top-level entry such as onException, rest or routeConfiguration),
 * taken from the YAML examples in the Camel documentation, with where each goes in a file.
 * <p>
 * The EIP samples are read from the catalog's documentation at lookup time, so they follow the Camel version in use.
 * The shipped {@code eip-samples.json} is the fallback (the user manual pages the catalog does not bundle, and the EIP
 * pages when the catalog has none): the build generates it from the documentation with the
 * {@code camel-yaml-dsl-validator:generate-doc-samples} goal, which fails when an example does not validate.
 */
public final class CatalogSamples {

    private static final String RESOURCE = "org/apache/camel/dsl/jbang/core/commands/ai/eip-samples.json";
    public static final int DEFAULT_LIMIT = 2;
    public static final int MAX_LIMIT = 5;

    /** The entries that go at the top of a YAML file, as list items next to the route, not inside it. */
    static final Set<String> TOP_LEVEL = Set.of("beans", "dataFormats", "errorHandler", "from", "intercept",
            "interceptFrom", "interceptSendToEndpoint", "onCompletion", "onException", "rest", "restConfiguration",
            "route", "routeConfiguration", "routeTemplate", "templatedRoute", "sslContextParameters", "transformers",
            "validators");

    /** Names that are a part of another EIP, whose samples show them in place. */
    static final Map<String, String> PART_OF = Map.ofEntries(
            Map.entry("onFallback", "circuitBreaker"),
            Map.entry("doCatch", "doTry"),
            Map.entry("doFinally", "doTry"),
            Map.entry("when", "choice"),
            Map.entry("otherwise", "choice"),
            Map.entry("onWhen", "onException"),
            Map.entry("redeliveryPolicy", "onException"),
            Map.entry("deadLetterChannel", "errorHandler"),
            Map.entry("defaultErrorHandler", "errorHandler"),
            Map.entry("get", "rest"),
            Map.entry("post", "rest"),
            Map.entry("aggregationStrategy", "aggregate"));

    /**
     * What a request is about, in the words a person or a model uses, to the EIP that does it. The names and aliases of
     * the EIPs themselves (fan-out, dedup, rate-limit) come from the catalog's EIP models, so only what is not an alias
     * is here: a task (read file, call service), a wording of the outcome (retry, batch) or a technology (json, cron).
     */
    static final Map<String, String> INTENTS = Map.ofEntries(
            Map.entry("read file", "poll"), Map.entry("readfile", "poll"), Map.entry("load file", "poll"),
            Map.entry("read a file", "poll"), Map.entry("read", "poll"), Map.entry("fetch", "poll"),
            Map.entry("consume once", "poll"), Map.entry("poll once", "poll"),
            Map.entry("call service", "enrich"), Map.entry("call", "enrich"), Map.entry("http call", "enrich"),
            Map.entry("lookup", "enrich"),
            Map.entry("batch", "aggregate"), Map.entry("collect", "aggregate"), Map.entry("group", "aggregate"),
            Map.entry("retry", "onException"), Map.entry("error handling", "onException"),
            Map.entry("errorhandling", "onException"), Map.entry("exception", "onException"),
            Map.entry("route by content", "choice"), Map.entry("if", "choice"),
            Map.entry("parallel", "multicast"), Map.entry("resilience", "circuitBreaker"),
            Map.entry("rest api", "rest"), Map.entry("http server", "rest"), Map.entry("endpoint", "rest"),
            Map.entry("convert", "convertBodyTo"), Map.entry("json", "marshal"),
            Map.entry("timer", "from"), Map.entry("schedule", "from"), Map.entry("cron", "from"));

    private static volatile Map<String, List<Map<String, String>>> samples;

    private CatalogSamples() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, List<Map<String, String>>> samples() {
        Map<String, List<Map<String, String>>> answer = samples;
        if (answer == null) {
            synchronized (CatalogSamples.class) {
                answer = samples;
                if (answer == null) {
                    answer = new TreeMap<>();
                    try (InputStream is = CatalogSamples.class.getClassLoader().getResourceAsStream(RESOURCE)) {
                        if (is != null) {
                            JsonObject root
                                    = (JsonObject) Jsoner.deserialize(new InputStreamReader(is, StandardCharsets.UTF_8));
                            for (Map.Entry<String, Object> e : root.entrySet()) {
                                List<Map<String, String>> list = new ArrayList<>();
                                for (Object o : (JsonArray) e.getValue()) {
                                    JsonObject jo = (JsonObject) o;
                                    list.add(Map.of("source", jo.getString("source"), "yaml", jo.getString("yaml")));
                                }
                                answer.put(e.getKey(), List.copyOf(list));
                            }
                        }
                    } catch (Exception e) {
                        throw new IllegalStateException("Cannot load " + RESOURCE, e);
                    }
                    samples = answer;
                }
            }
        }
        return answer;
    }

    /** The names with samples, sorted. */
    public static List<String> names() {
        return new ArrayList<>(samples().keySet());
    }

    /**
     * Samples for the given EIP or entry name (camelCase, kebab-case or any case), with where it goes.
     */
    public static JsonObject sample(String name, int limit) {
        return sample(null, name, limit);
    }

    private static final Pattern YAML_BLOCK = Pattern.compile("\\[source,yaml\\]\\s*\\n----\\n(.*?)\\n----", Pattern.DOTALL);
    private static final Map<String, List<Map<String, String>>> DOC_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The validated YAML examples of the EIP page in the catalog, or an empty list when the catalog has no such page or
     * none of its examples validate. Cached per name for the default catalog.
     */
    static List<Map<String, String>> fromCatalog(CamelCatalog catalog, String key) {
        if (catalog == null) {
            return List.of();
        }
        // the catalog names the pages after the EIP (circuitBreaker-eip, pollEnrich-eip); a few are kebab-case, and the
        // pattern pages have no suffix (dead-letter-channel, intercept)
        boolean cacheable = catalog.getCatalogVersion() != null && catalog.getLoadedVersion() == null;
        if (cacheable && DOC_CACHE.containsKey(key)) {
            return DOC_CACHE.get(key);
        }
        List<Map<String, String>> answer = new ArrayList<>();
        try {
            String page = null;
            String doc = null;
            for (String candidate : List.of(key + "-eip", kebab(key) + "-eip", kebab(key), key)) {
                doc = catalog.asciiDoc(candidate);
                if (doc != null) {
                    page = candidate;
                    break;
                }
            }
            if (doc != null) {
                Matcher m = YAML_BLOCK.matcher(doc);
                while (m.find()) {
                    String yaml = m.group(1).stripTrailing() + "\n";
                    if (!yaml.stripLeading().startsWith("- ")) {
                        continue;
                    }
                    if (SourceValidator.validateCamelYaml(yaml, null).isEmpty()) {
                        answer.add(Map.of("source", page + ".adoc (Camel " + catalog.getCatalogVersion() + ")", "yaml", yaml));
                    }
                }
            }
        } catch (Exception e) {
            // a page that cannot be read or validated: the shipped samples are the fallback
        }
        List<Map<String, String>> result = List.copyOf(answer);
        if (cacheable) {
            DOC_CACHE.put(key, result);
        }
        return result;
    }

    static String kebab(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char ch : camel.toCharArray()) {
            if (Character.isUpperCase(ch)) {
                sb.append('-').append(Character.toLowerCase(ch));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * Samples for the given EIP or entry name (camelCase, kebab-case, any case, or what to do), with where it goes;
     * from the catalog's documentation for the Camel version in use when it has the page, else the shipped set.
     */
    public static JsonObject sample(CamelCatalog catalog, String name, int limit) {
        JsonObject answer = new JsonObject();
        String given = name != null ? name.trim() : "";
        if (given.isEmpty()) {
            answer.put("error", "name is required, e.g. onException, aggregate, circuitBreaker, split, rest");
            return answer;
        }
        int max = Math.max(1, Math.min(MAX_LIMIT, limit <= 0 ? DEFAULT_LIMIT : limit));
        String key = resolve(catalog, given);
        if (key == null) {
            answer.put("error", "No sample for '" + given + "'");
            answer.put("suggestions", new JsonArray(suggest(normalize(given))));
            // the name may still be an EIP (dedup is idempotentConsumer) whose page has no YAML example
            List<String> eips = catalog != null ? catalog.suggestEipNames(given, 1) : List.of();
            if (!eips.isEmpty()) {
                answer.put("eip", eips.get(0));
                answer.put("hint", "'" + given + "' is the " + eips.get(0) + " EIP, which has no YAML sample; "
                                   + "camel_catalog_doc gives its options");
            } else {
                answer.put("hint", "camel_catalog_doc gives the options of an EIP; the sample tool covers the EIPs and file "
                                   + "entries documented with YAML examples");
            }
            return answer;
        }
        String partOf = PART_OF.get(normalize(given));
        answer.put("name", key);
        if (partOf != null) {
            answer.put("partOf", partOf);
            answer.put("note", given + " is a part of " + partOf + "; the sample shows it in place");
        } else if (!key.equalsIgnoreCase(normalize(given))) {
            answer.put("note", "'" + given + "' is done with the " + key + " EIP");
        }
        answer.put("placement", placement(key));
        List<Map<String, String>> list = fromCatalog(catalog, key);
        if (list.isEmpty()) {
            list = samples().getOrDefault(key, List.of());
        }
        JsonArray arr = new JsonArray();
        for (Map<String, String> s : list.subList(0, Math.min(max, list.size()))) {
            JsonObject jo = new JsonObject();
            jo.put("source", s.get("source"));
            jo.put("yaml", s.get("yaml"));
            arr.add(jo);
        }
        answer.put("samples", arr);
        answer.put("count", list.size());
        return answer;
    }

    static String placement(String key) {
        if (TOP_LEVEL.contains(key)) {
            return "top-level entry: a list item at the same level as route or from, not a step inside a route";
        }
        return "a step inside the steps of a route (or of another EIP)";
    }

    /** Resolves a user given name to a sample key: exact, case-insensitive, kebab-case, or a part of another EIP. */
    static String resolve(String given) {
        return resolve(null, given);
    }

    /**
     * Resolves a user given name to a sample key: exact, case-insensitive, kebab-case, a part of another EIP, an
     * intent, or with a catalog an alias of an EIP model (fan-out, dedup, rate-limit) or a word of its title.
     */
    static String resolve(CamelCatalog catalog, String given) {
        String n = normalize(given);
        if (samples().containsKey(n)) {
            return n;
        }
        String intent = INTENTS.get(given.trim().toLowerCase(Locale.ROOT));
        if (intent != null && samples().containsKey(intent)) {
            return intent;
        }
        String partOf = PART_OF.get(n);
        if (partOf != null && samples().containsKey(partOf)) {
            return partOf;
        }
        for (String k : samples().keySet()) {
            if (k.equalsIgnoreCase(n)) {
                return k;
            }
        }
        if (catalog != null) {
            // the aliases of the EIP models, then the EIP an aliased part belongs to (fallback is a part of circuitBreaker)
            for (String eip : catalog.suggestEipNames(given, 3)) {
                if (samples().containsKey(eip)) {
                    return eip;
                }
                String whole = PART_OF.get(eip);
                if (whole != null && samples().containsKey(whole)) {
                    return whole;
                }
            }
        }
        return null;
    }

    static String normalize(String given) {
        String n = given.trim();
        if (n.endsWith("-eip")) {
            n = n.substring(0, n.length() - 4);
        }
        if (n.contains("-")) {
            String[] parts = n.split("-");
            StringBuilder sb = new StringBuilder(parts[0].toLowerCase(Locale.ROOT));
            for (int i = 1; i < parts.length; i++) {
                if (!parts[i].isEmpty()) {
                    sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
                }
            }
            n = sb.toString();
        }
        return n;
    }

    static List<String> suggest(String n) {
        List<String> out = new ArrayList<>();
        String lower = n.toLowerCase(Locale.ROOT);
        for (String k : samples().keySet()) {
            String kl = k.toLowerCase(Locale.ROOT);
            if (kl.contains(lower) || lower.contains(kl) || distance(kl, lower) <= Math.max(2, lower.length() / 3)) {
                out.add(k);
            }
            if (out.size() >= 8) {
                break;
            }
        }
        return out;
    }

    static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }
}
