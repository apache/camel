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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validated Camel YAML DSL samples per EIP (and per top-level entry such as onException, rest or routeConfiguration),
 * per component, data format and language, taken from the YAML examples in the Camel documentation, with where each
 * goes in a file.
 * <p>
 * The samples are read from the catalog's documentation at lookup time, so they follow the Camel version in use: the
 * EIP page, or the component page and its sub-pages (the consumer, producer or examples pages of a component), the data
 * format page or the language page. The shipped {@code eip-samples.json} is the fallback for the EIPs and the file
 * entries (the user manual pages the catalog does not bundle, and the EIP pages when the catalog has none): the build
 * generates it from the documentation with the {@code camel-yaml-dsl-validator:generate-doc-samples} goal, which fails
 * when an example does not validate. The component, data format and language examples have no shipped fallback: they
 * come from the catalog in use only, which is the point of asking for a version.
 */
public final class CatalogSamples {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogSamples.class);

    private static final String RESOURCE = "org/apache/camel/dsl/jbang/core/commands/ai/eip-samples.json";
    public static final int DEFAULT_LIMIT = 2;
    public static final int MAX_LIMIT = 5;

    /** The kinds a sample can be asked for; eip covers the file entries (onException, rest, beans) too. */
    static final List<String> KINDS = List.of("eip", "component", "dataformat", "language");

    private static final List<String> CATALOG_KINDS = List.of("component", "dataformat", "language");

    /** The suffixes of the catalog pages of the four families; the other pages are sub-pages or manual pages. */
    private static final List<String> FAMILY_SUFFIXES = List.of("-component", "-dataformat", "-language", "-eip");

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
     * A protocol or a product (mqtt, s3) is what camel_catalog_find turns into a component, so it is not here.
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
            Map.entry("schedule", "from"), Map.entry("cron", "from"));

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

    /** The names with shipped samples (the EIPs and the file entries), sorted. */
    public static List<String> names() {
        return new ArrayList<>(samples().keySet());
    }

    /**
     * Samples for the given EIP or entry name (camelCase, kebab-case or any case), with where it goes.
     */
    public static JsonObject sample(String name, int limit) {
        return sample(null, null, name, limit);
    }

    /**
     * Samples for the given name of any kind, with where it goes; from the catalog's documentation for the Camel
     * version in use when it has the page, else the shipped set.
     */
    public static JsonObject sample(CamelCatalog catalog, String name, int limit) {
        return sample(catalog, null, name, limit);
    }

    private static final Pattern YAML_BLOCK = Pattern.compile("\\[source,yaml\\]\\s*\\n----\\n(.*?)\\n----", Pattern.DOTALL);
    private static final Map<String, Found> DOC_CACHE = new ConcurrentHashMap<>();

    /** The validated examples of a page family, and for a component whether any of them uses its endpoint. */
    record Found(List<Map<String, String>> samples, boolean endpoint) {
        static final Found NONE = new Found(List.of(), false);
    }

    /**
     * The validated YAML examples of the documentation of the given kind and name in the catalog, or none when the
     * catalog has no such page or none of its examples validate. For an EIP the page named after it
     * (circuitBreaker-eip, pollEnrich-eip; a few are kebab-case, and the pattern pages have no suffix:
     * dead-letter-channel, intercept); for a component its page and sub-pages, the examples using its endpoint first;
     * for a data format or a language its page. Cached per kind and name for the default catalog.
     */
    static Found fromCatalog(CamelCatalog catalog, String kind, String key) {
        if (catalog == null) {
            return Found.NONE;
        }
        boolean cacheable = catalog.getCatalogVersion() != null && catalog.getLoadedVersion() == null;
        String cacheKey = kind + ":" + key;
        if (cacheable) {
            Found cached = DOC_CACHE.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        Found found;
        try {
            found = switch (kind) {
                case "component" -> componentSamples(catalog, key);
                case "dataformat" -> pageSamples(catalog, dataFormatPages(catalog, key), null);
                case "language" -> pageSamples(catalog, List.of(key + "-language"), null);
                default -> eipSamples(catalog, key);
            };
        } catch (Exception e) {
            // a page that cannot be read or validated: the shipped samples are the fallback, and the answer says the
            // page has no example; the cause is in the debug log
            LOG.debug("Cannot read the {} samples of {} from the catalog documentation", kind, key, e);
            found = Found.NONE;
        }
        if (cacheable) {
            DOC_CACHE.put(cacheKey, found);
        }
        return found;
    }

    private static Found eipSamples(CamelCatalog catalog, String key) {
        for (String candidate : List.of(key + "-eip", kebab(key) + "-eip", kebab(key), key)) {
            if (catalog.asciiDoc(candidate) != null) {
                return pageSamples(catalog, List.of(candidate), null);
            }
        }
        return Found.NONE;
    }

    private static Found componentSamples(CamelCatalog catalog, String name) {
        String page = componentPage(catalog, name);
        if (page == null) {
            return Found.NONE;
        }
        List<String> pages = new ArrayList<>();
        pages.add(page + "-component");
        pages.addAll(subPages(catalog, page));
        return pageSamples(catalog, pages, schemePattern(schemesOf(catalog, name, page)));
    }

    /**
     * The validated route examples of the pages, in page order; with a scheme pattern the examples that use the
     * endpoint come first.
     */
    private static Found pageSamples(CamelCatalog catalog, List<String> pages, Pattern scheme) {
        List<Map<String, String>> uses = new ArrayList<>();
        List<Map<String, String>> others = new ArrayList<>();
        for (String page : pages) {
            String doc = catalog.asciiDoc(page);
            if (doc == null) {
                continue;
            }
            Matcher m = YAML_BLOCK.matcher(doc);
            while (m.find()) {
                String yaml = m.group(1).stripTrailing() + "\n";
                if (!yaml.stripLeading().startsWith("- ")) {
                    // a fragment (an option list, a manifest), not a route file
                    continue;
                }
                if (!SourceValidator.validateYamlSchema(yaml, catalog).isEmpty()) {
                    continue;
                }
                Map<String, String> sample
                        = Map.of("source", page + ".adoc (Camel " + catalog.getCatalogVersion() + ")", "yaml", yaml);
                if (scheme != null && scheme.matcher(yaml).find()) {
                    uses.add(sample);
                } else {
                    others.add(sample);
                }
            }
        }
        List<Map<String, String>> all = new ArrayList<>(uses);
        all.addAll(others);
        return new Found(List.copyOf(all), !uses.isEmpty());
    }

    /**
     * The base name of the documentation page of a component: its own name, or the page of its artifact when the
     * component shares a page with its siblings (smtp, imap and pop3 are on the mail page, https on the http page).
     */
    static String componentPage(CamelCatalog catalog, String name) {
        if (catalog.asciiDoc(name + "-component") != null) {
            return name;
        }
        ComponentModel model = componentModel(catalog, name);
        String artifactId = model != null ? model.getArtifactId() : null;
        if (artifactId != null && artifactId.startsWith("camel-")) {
            String page = artifactId.substring("camel-".length());
            if (catalog.asciiDoc(page + "-component") != null) {
                return page;
            }
        }
        return null;
    }

    /**
     * The sub-pages of a component page (aws2-s3-consumer-examples, salesforce-streaming, platform-http-vertx): the
     * catalog pages named with the component's prefix that are not a page of one of the four families, nor of another
     * component with a longer name (mina-sftp-authentication is not a page of mina).
     */
    static List<String> subPages(CamelCatalog catalog, String page) {
        List<String> out = new ArrayList<>();
        String prefix = page + "-";
        List<String> components = catalog.findComponentNames();
        for (String doc : catalog.findDocNames()) {
            if (!doc.startsWith(prefix) || FAMILY_SUFFIXES.stream().anyMatch(doc::endsWith)) {
                continue;
            }
            boolean other = false;
            for (String c : components) {
                if (c.length() > page.length() && c.startsWith(prefix) && (doc.equals(c) || doc.startsWith(c + "-"))) {
                    other = true;
                    break;
                }
            }
            if (!other) {
                out.add(doc);
            }
        }
        Collections.sort(out);
        return out;
    }

    /**
     * The schemes an example of the component would use in an endpoint uri: the component's own and its alternatives
     * (smtp, smtps, imap...), or for a page that groups components the schemes of the components of its artifact.
     */
    private static Set<String> schemesOf(CamelCatalog catalog, String name, String page) {
        Set<String> schemes = new LinkedHashSet<>();
        ComponentModel model = componentModel(catalog, name);
        if (model != null) {
            addSchemes(schemes, model);
        } else {
            for (String c : catalog.findComponentNames()) {
                ComponentModel m = catalog.componentModel(c);
                if (m != null && ("camel-" + page).equals(m.getArtifactId())) {
                    addSchemes(schemes, m);
                }
            }
        }
        if (schemes.isEmpty()) {
            schemes.add(name);
        }
        return schemes;
    }

    private static void addSchemes(Set<String> schemes, ComponentModel model) {
        if (model.getScheme() != null) {
            schemes.add(model.getScheme());
        }
        String alternatives = model.getAlternativeSchemes();
        if (alternatives != null) {
            for (String s : alternatives.split(",")) {
                if (!s.isBlank()) {
                    schemes.add(s.trim());
                }
            }
        }
    }

    /** Matches an endpoint uri of one of the schemes: {@code uri: kafka:...}, {@code uri: "kafka:..."}. */
    private static Pattern schemePattern(Set<String> schemes) {
        String any = schemes.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        return Pattern.compile("(uri:\\s*[\"']?|[\"'])(" + any + "):");
    }

    private static ComponentModel componentModel(CamelCatalog catalog, String name) {
        return catalog.findComponentNames().contains(name) ? catalog.componentModel(name) : null;
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
     * Samples for the given name, with where it goes: an EIP or entry name (camelCase, kebab-case, any case, or what to
     * do), a component, a data format or a language. Without a kind the EIPs and file entries are looked up first, then
     * the catalog's components, data formats and languages, then the aliases of the EIPs; a name that is in several
     * kinds with samples returns the kinds to choose from. The samples come from the catalog's documentation for the
     * Camel version in use when it has the page, else (EIPs and file entries) the shipped set.
     *
     * @param catalog the catalog of the Camel version to answer for, or null for the shipped samples only
     * @param kind    eip, component, dataformat or language, or null to resolve the name across the kinds
     * @param name    the name, or what to do
     * @param limit   the maximum number of samples, at most {@link #MAX_LIMIT}
     */
    public static JsonObject sample(CamelCatalog catalog, String kind, String name, int limit) {
        JsonObject answer = new JsonObject();
        String given = name != null ? name.trim() : "";
        if (given.isEmpty()) {
            answer.put("error", "name is required, e.g. onException, aggregate, circuitBreaker, split, rest, kafka, csv, jq");
            return answer;
        }
        String wanted = normalizeKind(kind);
        if (wanted != null && !KINDS.contains(wanted)) {
            answer.put("error", "kind must be one of " + String.join(", ", KINDS) + ", got '" + kind + "'");
            return answer;
        }
        int max = Math.max(1, Math.min(MAX_LIMIT, limit <= 0 ? DEFAULT_LIMIT : limit));
        boolean eips = wanted == null || wanted.equals("eip");

        // an EIP or a file entry by its name, a part of it, or what to do
        if (eips) {
            String key = resolveExact(given);
            if (key != null) {
                return eipAnswer(catalog, given, key, max, wanted == null ? alsoKinds(catalog, given) : List.of());
            }
        }
        // a component, data format or language by its name
        if (catalog != null && !"eip".equals(wanted)) {
            Map<String, String> kinds = new TreeMap<>();
            for (String k : wanted != null ? List.of(wanted) : CATALOG_KINDS) {
                String canonical = canonical(catalog, k, given);
                if (canonical != null) {
                    kinds.put(k, canonical);
                }
            }
            if (kinds.size() > 1) {
                // the kinds with samples decide (file is a component with examples and a language without)
                Map<String, String> withSamples = new TreeMap<>();
                kinds.forEach((k, canonical) -> {
                    if (!fromCatalog(catalog, k, canonical).samples().isEmpty()) {
                        withSamples.put(k, canonical);
                    }
                });
                if (!withSamples.isEmpty()) {
                    kinds = withSamples;
                }
            }
            if (kinds.size() > 1) {
                answer.put("name", given);
                answer.put("kinds", new JsonArray(new ArrayList<>(kinds.keySet())));
                answer.put("hint", "'" + given + "' is a " + String.join(" and a ", kinds.keySet())
                                   + "; ask again with kind");
                return answer;
            }
            if (kinds.size() == 1) {
                Map.Entry<String, String> only = kinds.entrySet().iterator().next();
                return catalogAnswer(catalog, only.getKey(), only.getValue(), max);
            }
        }
        // an alias of an EIP model (fan-out, dedup, rate-limit) or a word of its title
        if (eips) {
            String key = resolveAlias(catalog, given);
            if (key != null) {
                return eipAnswer(catalog, given, key, max, List.of());
            }
        }
        answer.put("error", "No sample for '" + given + "'" + (wanted != null ? " as a " + wanted : ""));
        answer.put("suggestions", new JsonArray(suggestions(catalog, wanted, given)));
        // the name may still be an EIP (dedup is idempotentConsumer) whose page has no YAML example
        List<String> eipNames = catalog != null && eips ? catalog.suggestEipNames(given, 1) : List.of();
        if (!eipNames.isEmpty()) {
            answer.put("eip", eipNames.get(0));
            answer.put("hint", "'" + given + "' is the " + eipNames.get(0) + " EIP, which has no YAML sample; "
                               + "camel_catalog_doc gives its options");
        } else {
            answer.put("hint", "camel_catalog_doc gives the options of an EIP, component, data format or language; "
                               + "the sample tool covers what the documentation shows with YAML examples");
        }
        return answer;
    }

    private static JsonObject eipAnswer(CamelCatalog catalog, String given, String key, int max, List<String> also) {
        JsonObject answer = new JsonObject();
        String partOf = PART_OF.get(normalize(given));
        answer.put("name", key);
        answer.put("kind", "eip");
        if (partOf != null) {
            answer.put("partOf", partOf);
            answer.put("note", given + " is a part of " + partOf + "; the sample shows it in place");
        } else if (!key.equalsIgnoreCase(normalize(given))) {
            answer.put("note", "'" + given + "' is done with the " + key + " EIP");
        }
        answer.put("placement", placement(key));
        List<Map<String, String>> list = fromCatalog(catalog, "eip", key).samples();
        if (list.isEmpty()) {
            list = samples().getOrDefault(key, List.of());
        }
        putSamples(answer, list, max);
        if (!also.isEmpty()) {
            answer.put("also", new JsonArray(also));
            answer.put("hint", "'" + given + "' is also a " + String.join(" and a ", also)
                               + "; ask with kind for that sample");
        }
        return answer;
    }

    private static JsonObject catalogAnswer(CamelCatalog catalog, String kind, String name, int max) {
        JsonObject answer = new JsonObject();
        answer.put("name", name);
        answer.put("kind", kind);
        answer.put("placement", placement(catalog, kind, name));
        Found found = fromCatalog(catalog, kind, name);
        putSamples(answer, found.samples(), max);
        if (found.samples().isEmpty()) {
            answer.put("hint", "the documentation of " + name + " has no YAML route example; camel_catalog_doc gives "
                               + "its options" + ("component".equals(kind) ? " and the uri syntax" : ""));
        } else if ("component".equals(kind) && !found.endpoint()) {
            answer.put("note", "none of the examples of the documentation uses a " + name + " endpoint; they show the "
                               + "component in another way (a properties function, a policy, a converter)");
        }
        return answer;
    }

    private static void putSamples(JsonObject answer, List<Map<String, String>> list, int max) {
        JsonArray arr = new JsonArray();
        for (Map<String, String> s : list.subList(0, Math.min(max, list.size()))) {
            JsonObject jo = new JsonObject();
            jo.put("source", s.get("source"));
            jo.put("yaml", s.get("yaml"));
            arr.add(jo);
        }
        answer.put("samples", arr);
        answer.put("count", list.size());
    }

    /** The catalog kinds, other than the EIPs, the name has samples in. */
    private static List<String> alsoKinds(CamelCatalog catalog, String given) {
        List<String> also = new ArrayList<>();
        if (catalog != null) {
            for (String k : CATALOG_KINDS) {
                String canonical = canonical(catalog, k, given);
                if (canonical != null && !fromCatalog(catalog, k, canonical).samples().isEmpty()) {
                    also.add(k);
                }
            }
        }
        return also;
    }

    /**
     * The catalog name of the given kind the name stands for, when it has a documentation page: the name itself, or the
     * name in another case; for a component also a page that groups components (mail).
     */
    static String canonical(CamelCatalog catalog, String kind, String given) {
        List<String> names = switch (kind) {
            case "component" -> catalog.findComponentNames();
            case "dataformat" -> catalog.findDataFormatNames();
            case "language" -> catalog.findLanguageNames();
            default -> List.of();
        };
        String name = names.contains(given) ? given : null;
        if (name == null) {
            for (String n : names) {
                if (n.equalsIgnoreCase(given)) {
                    name = n;
                    break;
                }
            }
        }
        if ("component".equals(kind)) {
            if (name != null) {
                return componentPage(catalog, name) != null ? name : null;
            }
            String page = given.toLowerCase(Locale.ROOT);
            return catalog.asciiDoc(page + "-component") != null ? page : null;
        }
        if ("dataformat".equals(kind)) {
            return name != null && !dataFormatPages(catalog, name).isEmpty() ? name : null;
        }
        return name != null && catalog.asciiDoc(name + "-" + kind) != null ? name : null;
    }

    /**
     * The documentation pages of a data format: its own, the page of its artifact (bindyCsv, bindyFixed and bindyKvp
     * are on the bindy page), or the pages of its variants by major version (jackson2 and jackson3 for jackson).
     */
    static List<String> dataFormatPages(CamelCatalog catalog, String name) {
        if (catalog.asciiDoc(name + "-dataformat") != null) {
            return List.of(name + "-dataformat");
        }
        DataFormatModel model = catalog.findDataFormatNames().contains(name) ? catalog.dataFormatModel(name) : null;
        String artifactId = model != null ? model.getArtifactId() : null;
        if (artifactId != null && artifactId.startsWith("camel-")) {
            String page = artifactId.substring("camel-".length()) + "-dataformat";
            if (catalog.asciiDoc(page) != null) {
                return List.of(page);
            }
        }
        List<String> variants = new ArrayList<>();
        Pattern variant = Pattern.compile(Pattern.quote(name) + "\\d+-dataformat");
        for (String doc : catalog.findDocNames()) {
            if (variant.matcher(doc).matches()) {
                variants.add(doc);
            }
        }
        Collections.sort(variants);
        return variants;
    }

    static String normalizeKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return null;
        }
        String k = kind.trim().toLowerCase(Locale.ROOT).replace("-", "").replace(" ", "").replace("_", "");
        if (k.endsWith("s")) {
            k = k.substring(0, k.length() - 1);
        }
        return k;
    }

    static String placement(String key) {
        if (TOP_LEVEL.contains(key)) {
            return "top-level entry: a list item at the same level as route or from, not a step inside a route";
        }
        return "a step inside the steps of a route (or of another EIP)";
    }

    static String placement(CamelCatalog catalog, String kind, String name) {
        switch (kind) {
            case "component": {
                ComponentModel model = componentModel(catalog, name);
                if (model != null && model.isConsumerOnly()) {
                    return "an endpoint uri in from: only (a consumer; it cannot be used in to:)";
                }
                if (model != null && model.isProducerOnly()) {
                    return "an endpoint uri in to: only (a producer; it cannot be used in from:)";
                }
                return "an endpoint uri in from: (consumer) or to: (producer)";
            }
            case "dataformat":
                return "a marshal or unmarshal step inside the steps of a route, with the data format and its options"
                       + " under it";
            case "language":
                return "the expression of a step (setBody, setHeader, filter, when, split, ...), under expression:";
            default:
                return placement(name);
        }
    }

    /**
     * Resolves a user given name to a shipped sample key: exact, an intent, a part of another EIP, or case-insensitive.
     */
    static String resolveExact(String given) {
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
        return null;
    }

    /**
     * Resolves a user given name through the catalog's EIP models: an alias (fan-out, dedup, rate-limit) or a word of
     * the title, then the EIP an aliased part belongs to (fallback is a part of circuitBreaker).
     */
    static String resolveAlias(CamelCatalog catalog, String given) {
        if (catalog == null) {
            return null;
        }
        for (String eip : catalog.suggestEipNames(given, 3)) {
            if (samples().containsKey(eip)) {
                return eip;
            }
            String whole = PART_OF.get(eip);
            if (whole != null && samples().containsKey(whole)) {
                return whole;
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

    /**
     * Names close to the given one: the shipped keys, and with a catalog its components, data formats and languages.
     */
    private static List<String> suggestions(CamelCatalog catalog, String wanted, String given) {
        List<String> out = wanted == null || wanted.equals("eip") ? suggest(normalize(given)) : new ArrayList<>();
        if (catalog != null) {
            for (String kind : CATALOG_KINDS) {
                if (wanted == null || wanted.equals(kind)) {
                    suggestCatalog(out, catalog, kind, given, kind.equals("component") ? 3 : 2);
                }
            }
        }
        return out;
    }

    /** Adds the catalog names of the kind close to the given one, only those with a page to come back for. */
    private static void suggestCatalog(List<String> out, CamelCatalog catalog, String kind, String given, int max) {
        List<String> found = switch (kind) {
            case "component" -> catalog.suggestComponentNames(given, max * 3);
            case "dataformat" -> catalog.suggestDataFormatNames(given, max * 3);
            default -> catalog.suggestLanguageNames(given, max * 3);
        };
        int n = 0;
        for (String s : found) {
            if (n < max && canonical(catalog, kind, s) != null) {
                out.add(s + " (" + kind + ")");
                n++;
            }
        }
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
