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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.ConfigurationPropertiesValidationResult;
import org.apache.camel.tooling.model.MainModel;

/**
 * The application.properties checks of {@link SourceValidator}: unknown camel.* options with the option meant, an
 * endpoint option set on the component, invented switches, the log level, prose lines.
 */
final class PropertiesChecks {

    private PropertiesChecks() {
    }

    /**
     * Validates a properties file line by line: {@code camel.*} keys against the catalog, the rest with the extra
     * check.
     */
    public static List<String> validateProperties(
            String content, CamelCatalog catalog, Function<String, String> extraPropertyLine) {
        return validatePropertiesLines(content, line -> validatePropertyLine(line, catalog, extraPropertyLine));
    }

    /** camel.<group>.<rest>=: the option groups of the main model (resilience4j, faulttolerance, threadpool...). */
    static final Pattern GROUP_KEY_PATTERN = Pattern.compile("^\\s*camel\\.([a-zA-Z0-9-]+)\\.([^=\\s]+)\\s*=");

    /** The camel.* prefixes whose keys legitimately nest, or that other checks own. */
    private static final Set<String> NESTING_GROUPS = Set.of("component", "dataformat", "language", "beans", "variable",
            "kamelet", "jbang", "route-template", "routeTemplate", "main", "rest", "server", "management");

    /**
     * camel.resilience4j.circuitbreaker.supplierCircuitBreaker.slidingWindowSize=4, an invented per-id form: the
     * catalog accepts it and the run dies at startup ("Cannot find getter method: supplierCircuitBreaker on bean: class
     * java.lang.String"). The options of a group are global, one segment after the group; resilience4j is also set per
     * circuit breaker in the route (CAMEL-24856).
     */
    static String nestedGroupKeyHint(String line, CamelCatalog catalog) {
        Matcher m = GROUP_KEY_PATTERN.matcher(line);
        if (!m.find()) {
            return null;
        }
        String group = m.group(1);
        String rest = m.group(2);
        if (!rest.contains(".") || rest.contains("[") || NESTING_GROUPS.contains(group)) {
            return null;
        }
        MainModel mm = catalog.mainModel();
        if (mm == null) {
            return null; // a catalog without the main model: the key goes to the catalog as is
        }
        String prefix = "camel." + group + ".";
        List<String> options = new ArrayList<>();
        for (var o : mm.getOptions()) {
            if (o.getName().startsWith(prefix) && !o.getName().substring(prefix.length()).contains(".")) {
                options.add(o.getName().substring(prefix.length()));
            }
        }
        if (options.isEmpty()) {
            return null; // not a known group: the catalog reports the key
        }
        String first = rest.substring(0, rest.indexOf('.'));
        String last = rest.substring(rest.lastIndexOf('.') + 1);
        String option = options.contains(last) ? last : closestName(last, options);
        String example = "camel." + group + "." + (option != null ? option : options.get(0)) + "=...";
        String more = "resilience4j".equals(group)
                ? ", or per circuit breaker in the route: circuitBreaker: {resilience4jConfiguration: {"
                  + (option != null ? option : "...") + ": ...}}"
                : "; the options are " + String.join(", ", options.size() > 8 ? options.subList(0, 8) : options)
                  + (options.size() > 8 ? ", ..." : "");
        return first + "    Unknown option (camel." + group + " has no nested settings such as " + first
               + ": its options are global, " + example + more + ")";
    }

    /** Validates one properties line: a {@code camel.*} key against the catalog, any other with the extra check. */
    static final Pattern COMPONENT_KEY_PATTERN
            = Pattern.compile("^\\s*camel\\.(component|dataformat|language)\\.([A-Za-z0-9-]+)\\.");

    static final Pattern ROOT_LEVEL_PATTERN
            = Pattern.compile("^\\s*logging\\.level\\.root\\s*=\\s*(WARN|WARNING|ERROR|FATAL|OFF)\\s*$",
                    Pattern.CASE_INSENSITIVE);

    public static String validatePropertyLine(String line, CamelCatalog catalog, Function<String, String> extra) {
        Matcher rl = ROOT_LEVEL_PATTERN.matcher(line);
        if (rl.find()) {
            // the log EIP logs under the route file's name, so a root level above INFO hides the route's own output
            return "logging.level.root=" + rl.group(1) + " also hides the route's own log steps (they log at INFO under"
                   + " the route file's name): keep root at INFO, or add logging.level.<route-file-name>=INFO";
        }
        // camel.component.logger.level: the catalog skips a component it does not know, camel run does not
        Matcher km = COMPONENT_KEY_PATTERN.matcher(line);
        if (km.find()) {
            String kind = km.group(1);
            String name = km.group(2);
            List<String> known = switch (kind) {
                case "component" -> catalog.findComponentNames();
                case "dataformat" -> catalog.findDataFormatNames();
                default -> catalog.findLanguageNames();
            };
            if (!known.contains(name)) {
                String closest = closestName(name, known);
                return name + "    Unknown " + kind + (closest != null ? " (did you mean " + closest + "?)" : "");
            }
        }
        // camel.resilience4j.circuitbreaker.<id>.<option>: a nested segment under a known option group (CAMEL-24856)
        String nested = nestedGroupKeyHint(line, catalog);
        if (nested != null) {
            return nested;
        }
        try {
            ConfigurationPropertiesValidationResult result = catalog.validateConfigurationProperty(line);
            if (result.isAccepted()) {
                if (!result.isSuccess()) {
                    String msg = result.summaryErrorMessage(false);
                    if (msg != null) {
                        msg = msg.trim();
                        String hint = mainOptionHint(line, catalog);
                        if (hint == null && km.reset().find() && "component".equals(km.group(1))) {
                            hint = endpointOptionHint(km.group(2), line, catalog);
                        }
                        return hint != null ? msg + " " + hint : msg;
                    }
                }
                return null;
            }
        } catch (Exception e) {
            // ignore validation errors
        }
        return extra != null ? extra.apply(line) : null;
    }

    /**
     * For an unknown camel.main.* key, the closest real option name (camel.main.duration -> durationMaxSeconds). For an
     * option that exists in another group of the main configuration, that group (camel.component.netty-http
     * .binding-mode -> camel.rest.bindingMode; camel.main.binding-mode -> camel.rest.bindingMode).
     */
    static String mainOptionHint(String line, CamelCatalog catalog) {
        String key = line.contains("=") ? line.substring(0, line.indexOf('=')).trim() : line.trim();
        if (!key.startsWith("camel.")) {
            return null;
        }
        String option = key.substring(key.lastIndexOf('.') + 1);
        String camel = dashToCamelCase(option);
        boolean main = key.startsWith("camel.main.");
        if (main && camel.toLowerCase(Locale.ROOT).matches("duration(strict|check|strictcheck|logging|logger)[a-z]*")) {
            // camel.main.durationStrictCheck=false, durationLoggingEnabled=true: nothing to switch off or on
            return "(there is no such switch: camel.main.duration=15s (or durationMaxSeconds=15) is all that limits a"
                   + " run, and without it the application runs until stopped; remove the line)";
        }
        if (main && camel.toLowerCase(Locale.ROOT).matches("log(ging)?level|log(ging)?")) {
            // camel.main.loggingLevel=INFO: the log level is a logging.level.* key, not a camel.* option
            return "(the log level is set with logging.level.root=INFO, or logging.level.<package>=DEBUG for one"
                   + " package)";
        }
        try {
            List<String> known = new ArrayList<>();
            List<String> elsewhere = new ArrayList<>();
            for (var o : catalog.mainModel().getOptions()) {
                String n = o.getName();
                if (n.startsWith("camel.main.")) {
                    known.add(n.substring("camel.main.".length()));
                }
                String group = n.substring(0, n.lastIndexOf('.'));
                String name = n.substring(n.lastIndexOf('.') + 1);
                if (!key.startsWith(group + ".") && name.equalsIgnoreCase(camel)) {
                    elsewhere.add(n);
                }
                // camel.main.restComponent: the group's own name folded into the option (camel.rest.component)
                String groupWord = group.substring(group.lastIndexOf('.') + 1);
                if (main && camel.length() > groupWord.length() && camel.toLowerCase(Locale.ROOT).startsWith(groupWord)
                        && Character.isUpperCase(camel.charAt(groupWord.length()))
                        && name.equalsIgnoreCase(camel.substring(groupWord.length()))) {
                    return "(did you mean " + n + "? the " + groupWord + " options are the camel." + groupWord + ".* keys)";
                }
            }
            if (main) {
                if (known.contains(camel)) {
                    return !camel.equals(option) ? "(did you mean camel.main." + camel + "?)" : null;
                }
                List<String> closest = new ArrayList<>(closestNames(camel, known));
                if (!closest.isEmpty()) {
                    String note = "";
                    if (closest.remove("durationMaxSeconds")) {
                        // camel.main.duration=60s: the option a run limit means, and its value has no unit
                        closest.add(0, "durationMaxSeconds");
                        note = "; durationMaxSeconds is a number of seconds without a unit, such as 60";
                    }
                    return "(did you mean " + String.join(", ", closest.stream().map(c -> "camel.main." + c).toList())
                           + "?" + note + ")";
                }
            }
            if ((camel.equals("enabled") || camel.equals("enable")) && !main) {
                // camel.resilience4j.enabled=true: the group has no switch, it applies when the route uses the feature
                String group = key.substring(0, key.lastIndexOf('.'));
                List<String> options = new ArrayList<>();
                for (var o : catalog.mainModel().getOptions()) {
                    if (o.getName().startsWith(group + ".")) {
                        options.add(o.getName().substring(group.length() + 1));
                    }
                }
                if (!options.isEmpty()) {
                    return "(" + group + " has no enabled switch: its settings apply when a route uses the feature; the options"
                           + " are " + String.join(", ", options.size() > 8 ? options.subList(0, 8) : options)
                           + (options.size() > 8 ? ", ..." : "") + ")";
                }
            }
            if (!elsewhere.isEmpty()) {
                // the groups a route author means most often first: camel.rest before camel.management
                elsewhere.sort(java.util.Comparator.comparingInt(n -> groupRank(n.substring(0, n.lastIndexOf('.')))));
                List<String> groups = elsewhere.stream().map(n -> n.substring(0, n.lastIndexOf('.'))).limit(3).toList();
                String where = groups.size() == 1
                        ? groups.get(0)
                        : String.join(", ", groups.subList(0, groups.size() - 1)) + " or " + groups.get(groups.size() - 1);
                return "(did you mean " + elsewhere.get(0) + "? " + option + " is an option of " + where
                       + (main ? "" : ", not of the " + key.substring(0, key.lastIndexOf('.')) + " key") + ")";
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * camel.component.timer.period=1000: period is an endpoint option (it goes in the uri), the camel.component.* keys
     * are the component's own options; say so and name a few of those.
     */
    static String endpointOptionHint(String scheme, String line, CamelCatalog catalog) {
        try {
            String key = line.contains("=") ? line.substring(0, line.indexOf('=')).trim() : line.trim();
            String option = dashToCamelCase(key.substring(key.lastIndexOf('.') + 1));
            var model = catalog.componentModel(scheme);
            if (model == null) {
                return null;
            }
            boolean endpointOption = model.getEndpointOptions().stream().anyMatch(o -> o.getName().equals(option));
            if (!endpointOption) {
                return null;
            }
            List<String> componentOptions = model.getComponentOptions().stream().map(o -> o.getName()).limit(6).toList();
            return "(" + option + " is an endpoint option of " + scheme + ": set it in the uri, " + scheme + ":name?" + option
                   + "=...; the camel.component." + scheme + ".* keys are the component's own options"
                   + (componentOptions.isEmpty() ? "" : ": " + String.join(", ", componentOptions)) + ")";
        } catch (Exception e) {
            return null;
        }
    }

    static final List<String> GROUP_ORDER
            = List.of("camel.main", "camel.rest", "camel.server", "camel.management", "camel.health", "camel.metrics");

    static int groupRank(String group) {
        int i = GROUP_ORDER.indexOf(group);
        return i < 0 ? GROUP_ORDER.size() : i;
    }

    static String dashToCamelCase(String text) {
        if (!text.contains("-")) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char ch : text.toCharArray()) {
            if (ch == '-') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(ch) : ch);
                upper = false;
            }
        }
        return sb.toString();
    }

    static String closestName(String name, List<String> known) {
        List<String> all = closestNames(name, known);
        return all.isEmpty() ? null : String.join(", ", all);
    }

    /** Up to three names within edit distance, the closest first; a prefix match counts as distance 1. */
    static List<String> closestNames(String name, List<String> known) {
        int threshold = Math.max(2, name.length() / 3);
        String lower = name.toLowerCase(Locale.ROOT);
        Map<String, Integer> distances = new LinkedHashMap<>();
        for (String k : known) {
            String kl = k.toLowerCase(Locale.ROOT);
            int d = lower.startsWith(kl) || kl.startsWith(lower) ? 1 : editDistance(lower, kl);
            if (d <= threshold) {
                distances.put(k, d);
            }
        }
        if (distances.isEmpty()) {
            // durationStyle: nothing within edit distance, so the options that start with the same word (duration)
            String word = leadingWord(name).toLowerCase(Locale.ROOT);
            if (word.length() >= 5) {
                for (String k : known) {
                    if (k.toLowerCase(Locale.ROOT).startsWith(word)) {
                        distances.put(k, 1);
                    }
                }
            }
        }
        return distances.entrySet().stream()
                .sorted(Map.Entry.<String, Integer> comparingByValue().thenComparing(e -> e.getKey().length())
                        .thenComparing(Map.Entry::getKey))
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** The first camelCase or dash-separated word of a name (durationStyle, duration-style -> duration). */
    static String leadingWord(String name) {
        int i = 0;
        while (i < name.length() && Character.isLowerCase(name.charAt(i))) {
            i++;
        }
        return name.substring(0, i);
    }

    static int editDistance(String a, String b) {
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

    /** The index of the first unescaped '=' or ':' in a properties line, or -1. */
    static int firstSeparator(String line) {
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\\') {
                i++;
            } else if (ch == '=' || ch == ':') {
                return i;
            }
        }
        return -1;
    }

    /** Runs a line validator over the key=value lines of a properties file, prefixing each message with its line. */
    public static List<String> validatePropertiesLines(String content, Function<String, String> lineValidator) {
        List<String> msgs = new ArrayList<>();
        if (content == null) {
            return msgs;
        }
        String[] lines = content.split("\n", -1);
        boolean continued = false;
        int prose = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            boolean wasContinued = continued;
            continued = line.endsWith("\\");
            if (wasContinued || line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            int sep = firstSeparator(line);
            String key = sep < 0 ? line : line.substring(0, sep).trim();
            if (key.isEmpty() || key.contains(" ") || key.contains("\t") || sep < 0 && key.startsWith("`")) {
                {
                    // an explanation appended after the properties: java.util.Properties reads it as keys with no
                    // value, so camel run does not fail on it, but it is not what the author meant
                    if (++prose <= 3) {
                        msgs.add("Line " + (i + 1) + " is not a property (\""
                                 + (line.length() > 40 ? line.substring(0, 40) + "..." : line)
                                 + "\"): a properties file holds key=value lines; put explanations in a # comment or leave"
                                 + " them out");
                    } else if (prose == 4) {
                        msgs.add("Line " + (i + 1) + " and the lines after it: more text that is not a property; the"
                                 + " file must end with its last key=value line");
                    }
                }
                continue;
            }
            if (!line.contains("=")) {
                continue;
            }
            String error = lineValidator.apply(lines[i]);
            if (error != null) {
                msgs.add("Line " + (i + 1) + ": " + error);
            }
        }
        return msgs;
    }

}
