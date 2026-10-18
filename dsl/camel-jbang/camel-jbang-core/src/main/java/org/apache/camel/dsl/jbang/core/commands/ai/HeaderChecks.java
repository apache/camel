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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;

import static org.apache.camel.dsl.jbang.core.commands.ai.PropertiesChecks.closestName;

/**
 * The Camel* header check of {@link SourceValidator}: a header a component never sets, against the header metadata of
 * the components the route uses.
 */
final class HeaderChecks {

    private HeaderChecks() {
    }

    static final Pattern CAMEL_HEADER_REF_PATTERN = Pattern.compile(
            "(?:\\$\\{headers?\\.|headers\\.|headers\\[['\"]|header\\(['\"]|(name):\\s*['\"]?)(Camel[A-Z][A-Za-z0-9]*(?:\\.[A-Za-z0-9_]+)*)");

    static final Pattern SCHEME_IN_URI_PATTERN = Pattern.compile("uri:\\s*\"?([a-zA-Z][a-zA-Z0-9+.-]*):");

    /**
     * Names a component sets as exchange properties, not headers; the catalog has no metadata for those, so the ones a
     * beginner reaches for are listed here (TimerConsumer sets them with setProperty).
     */
    static final Map<String, List<String>> EXCHANGE_PROPERTIES = Map.of(
            "timer", List.of("CamelTimerCounter", "CamelTimerName", "CamelTimerPeriod", "CamelTimerTime"));

    /**
     * The option without which a component sets none of its {@link #EXCHANGE_PROPERTIES}: TimerConsumer sets them only
     * with includeMetadata=true.
     */
    static final Map<String, String> METADATA_OPTION = Map.of("timer", "includeMetadata");

    /**
     * A comparison of the timer's counter with a number: the counter counts the fires from 1, so {@code == 0} is never
     * true (CAMEL-25744: in 8 of 10 benchmark runs a model compared it with 0 to pick the first fire). The Simple forms
     * (${exchangeProperty.CamelTimerCounter} == 0, ${header.CamelTimerCounter == 0 ? ...}) and the Groovy ones
     * (exchange.getProperty('CamelTimerCounter', Integer) == 0).
     */
    static final Pattern TIMER_COUNTER_COMPARISON = Pattern.compile(
            "CamelTimerCounter(?:\\}|['\"]\\s*(?:,\\s*[\\w.]+\\s*)?\\))?\\s*(==|!=|<=|<)\\s*['\"]?(\\d+)['\"]?");

    static final Pattern EXCHANGE_PROPERTY_REF_PATTERN = Pattern.compile("\\$\\{exchangeProperty\\.(Camel[A-Z][A-Za-z0-9]*)");

    /** Whether the option that makes the component set its exchange properties is true somewhere in the content. */
    static boolean setsExchangeProperties(String scheme, String content) {
        String option = METADATA_OPTION.get(scheme);
        return option == null
                || Pattern.compile(Pattern.quote(option) + "\\s*[=:]\\s*[\"']?true").matcher(content).find();
    }

    /**
     * Whether a header reference also finds an exchange property of that name: Simple's ${header.x} and the header("x")
     * expression look in the exchange properties when the message has no such header; ${headers.x} reads the map.
     */
    static boolean fallsBackToProperties(String reference) {
        return reference.startsWith("${header.") || reference.startsWith("header(");
    }

    private static String onlyWithOption(int line, String scheme, String name) {
        return "Line " + line + ": " + scheme + " sets " + name + " only with " + METADATA_OPTION.get(scheme)
               + "=true on the endpoint, as the exchange property ${exchangeProperty." + name + "}";
    }

    /**
     * What an invented Camel* header is usually reaching for, keyed on a fragment of its name, and the expression that
     * answers it. Tried when no real header is close: in a timer-triggered route there is no message source to name, so
     * a beginner or a model writes CamelFromEndpoint or CamelEndpointUri for "which endpoint did this come from" and
     * was told only that the value would be null.
     */
    static final List<Map.Entry<Pattern, String>> INTENT_HINTS = List.of(
            Map.entry(Pattern.compile("Endpoint|Uri|Source|From|Route|Origin"),
                    "For the endpoint or route a message came from use ${exchange.fromEndpoint} or ${routeId}; for the"
                                                                                + " endpoint it was last sent to, ${header.CamelToEndpoint}."),
            Map.entry(Pattern.compile("Size|Length|Count"),
                    "The body size is ${body.length()} for a String body or ${bodyAs(byte[]).length}; the number of"
                                                            + " headers is ${headers.size()}."),
            Map.entry(Pattern.compile("Timestamp|Time|Date"),
                    "The current time is ${date:now:yyyy-MM-dd HH:mm:ss} and the time the message was created is"
                                                              + " ${date:exchangeCreated:yyyy-MM-dd HH:mm:ss}; ${messageTimestamp} is set only by"
                                                              + " components that stamp their messages."));

    static String intentHint(String name) {
        for (var e : INTENT_HINTS) {
            if (e.getKey().matcher(name).find()) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * The comparisons of CamelTimerCounter that can never be true (== 0, <= 0, < 1) or never be false (!= 0), as the
     * counter is 1 on the first fire.
     */
    static List<String> timerCounterComparisons(String content) {
        List<String> msgs = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = TIMER_COUNTER_COMPARISON.matcher(lines[i]);
            while (m.find()) {
                String op = m.group(1);
                int n = Integer.parseInt(m.group(2));
                boolean never = ("==".equals(op) || "<=".equals(op)) && n == 0 || "<".equals(op) && n <= 1;
                boolean always = "!=".equals(op) && n == 0;
                if (never || always) {
                    msgs.add("Line " + (i + 1) + ": CamelTimerCounter counts the fires from 1 (the first fire is 1), so "
                             + op + " " + n + " is " + (never ? "never true" : "always true")
                             + ": compare with 1 to pick the first fire");
                }
            }
        }
        return msgs;
    }

    public static List<String> validateKnownHeaders(String content, CamelCatalog catalog) {
        List<String> msgs = new ArrayList<>();
        if (content == null) {
            return msgs;
        }
        msgs.addAll(timerCounterComparisons(content));
        Set<String> known = new LinkedHashSet<>();
        Map<String, String> owner = new LinkedHashMap<>();
        Matcher sm = SCHEME_IN_URI_PATTERN.matcher(content);
        Set<String> schemes = new LinkedHashSet<>();
        while (sm.find()) {
            schemes.add(sm.group(1));
        }
        for (String scheme : schemes) {
            try {
                var model = catalog.componentModel(scheme);
                if (model != null) {
                    for (var h : model.getEndpointHeaders()) {
                        known.add(h.getName());
                        owner.putIfAbsent(h.getName(), scheme);
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }
        if (known.isEmpty()) {
            return msgs;
        }
        // headers every exchange may carry, whatever the component
        Set<String> common = Set.of("CamelMessageTimestamp", "CamelFileName", "CamelFileNameProduced", "CamelCorrelationId",
                "CamelRedelivered", "CamelRedeliveryCounter", "CamelHttpResponseCode", "CamelHttpMethod", "CamelHttpPath",
                "CamelHttpQuery", "CamelHttpUri", "CamelHttpUrl", "CamelSplitIndex", "CamelSplitSize", "CamelSplitComplete",
                "CamelAggregatedSize", "CamelAggregatedCompletedBy", "CamelAggregatedCorrelationKey", "CamelLoopIndex",
                "CamelLoopSize", "CamelToEndpoint", "CamelBatchIndex", "CamelBatchSize", "CamelBatchComplete",
                "CamelFailureEndpoint", "CamelExceptionCaught", "CamelRouteStop", "CamelCharsetName", "CamelFileParent",
                "CamelFilePath", "CamelFileAbsolutePath", "CamelFileLength", "CamelFileLastModified", "CamelFileNameOnly",
                "CamelFileRelativePath", "CamelFileNameConsumed", "CamelFileExists", "CamelFileContentType",
                "CamelDuplicateMessage", "CamelSlipEndpoint", "CamelMulticastIndex",
                "CamelMulticastComplete", "CamelRecipientListEndpoint", "CamelReceivedTimestamp");
        String[] lines = content.split("\n", -1);
        Set<String> reported = new HashSet<>();
        for (int i = 0; i < lines.length; i++) {
            Matcher m = CAMEL_HEADER_REF_PATTERN.matcher(lines[i]);
            while (m.find()) {
                if (m.group(1) != null && !isHeaderName(lines, i)) {
                    // name: of a setProperty, setVariable, bean...: not a header
                    continue;
                }
                String name = m.group(2);
                if (common.contains(name) || reported.contains(name) || isKnown(name, known)) {
                    continue;
                }
                String propertyOwner = null;
                for (String scheme : schemes) {
                    if (EXCHANGE_PROPERTIES.getOrDefault(scheme, List.of()).contains(name)) {
                        propertyOwner = scheme;
                    }
                }
                if (propertyOwner != null) {
                    // before the header metadata: an exchange property is never a header, whatever the metadata says
                    reported.add(name);
                    if (!setsExchangeProperties(propertyOwner, content)) {
                        msgs.add(onlyWithOption(i + 1, propertyOwner, name));
                    } else if (!fallsBackToProperties(m.group())) {
                        msgs.add("Line " + (i + 1) + ": " + name + " is an exchange property set by " + propertyOwner
                                 + ", not a header (the header would be null): write ${exchangeProperty." + name + "}");
                    }
                    continue;
                }
                if (!reported.add(name)) {
                    continue;
                }
                String best = closestName(name, new ArrayList<>(known));
                String scheme = best != null && owner.containsKey(best.split(", ")[0]) ? owner.get(best.split(", ")[0]) : null;
                StringBuilder sb = new StringBuilder("Line ").append(i + 1).append(": header ").append(name)
                        .append(" is not set by ").append(String.join(", ", schemes)).append(" (the value would be null)");
                if (best != null) {
                    sb.append(": did you mean ").append(best).append("?");
                } else {
                    String hint = intentHint(name);
                    if (hint != null) {
                        sb.append(" ").append(hint);
                    }
                }
                // what the file's components do set: the owner of the closest name, and the first component in the
                // file (normally the trigger), so a name with nothing close still learns the real ones
                Set<String> listed = new LinkedHashSet<>();
                if (scheme != null) {
                    listed.add(scheme);
                }
                listed.add(schemes.iterator().next());
                for (String sc : listed) {
                    List<String> names = new ArrayList<>();
                    for (var e : owner.entrySet()) {
                        if (e.getValue().equals(sc)) {
                            names.add(e.getKey());
                        }
                    }
                    if (!names.isEmpty()) {
                        sb.append(" The ").append(sc).append(" headers are ").append(String.join(", ", names)).append(".");
                    }
                }
                for (String sc : schemes) {
                    List<String> props = EXCHANGE_PROPERTIES.getOrDefault(sc, List.of());
                    if (!props.isEmpty()) {
                        sb.append(" ").append(sc).append(" sets the exchange properties ").append(String.join(", ", props));
                        if (METADATA_OPTION.containsKey(sc)) {
                            sb.append(" with ").append(METADATA_OPTION.get(sc)).append("=true");
                        }
                        sb.append(" (${exchangeProperty.").append(props.get(0)).append("}).");
                    }
                }
                if (m.group(1) == null) {
                    sb.append(" A header no component sets stays null unless a setHeader step sets it earlier in the route.");
                }
                msgs.add(sb.toString());
            }
        }
        // an exchange property read without the option that sets it stays null as well
        for (String scheme : schemes) {
            if (setsExchangeProperties(scheme, content)) {
                continue;
            }
            List<String> props = EXCHANGE_PROPERTIES.getOrDefault(scheme, List.of());
            for (int i = 0; i < lines.length; i++) {
                Matcher pm = EXCHANGE_PROPERTY_REF_PATTERN.matcher(lines[i]);
                while (pm.find()) {
                    if (props.contains(pm.group(1)) && reported.add(pm.group(1))) {
                        msgs.add(onlyWithOption(i + 1, scheme, pm.group(1)));
                    }
                }
            }
        }
        return msgs;
    }

    /**
     * A header the metadata lists: as is (CamelBox.fileName), by a prefix the component documents with a trailing dot
     * (CamelSolrField. for CamelSolrField.id), or as the head of an OGNL path (CamelFileName.length()).
     */
    static boolean isKnown(String name, Set<String> known) {
        String candidate = name;
        while (true) {
            if (known.contains(candidate)) {
                return true;
            }
            for (String k : known) {
                if (k.endsWith(".") && candidate.startsWith(k)) {
                    return true;
                }
            }
            int dot = candidate.lastIndexOf('.');
            if (dot < 0) {
                return false;
            }
            candidate = candidate.substring(0, dot);
        }
    }

    /** Whether the name: on this line belongs to a setHeader or removeHeader step, not a setProperty, a bean... */
    static boolean isHeaderName(String[] lines, int index) {
        int indent = YamlLines.countLeadingSpaces(lines[index]);
        for (int j = index - 1; j >= 0; j--) {
            String line = lines[j];
            if (line.isBlank() || line.trim().startsWith("#")) {
                continue;
            }
            if (YamlLines.countLeadingSpaces(line) < indent) {
                String step = line.trim();
                if (step.startsWith("- ")) {
                    step = step.substring(2).trim();
                }
                return step.startsWith("setHeader:") || step.startsWith("removeHeader:");
            }
        }
        return false;
    }

}
