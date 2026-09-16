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
     * A Camel* header that no component used in the file defines (CamelTimerIndex; the timer sets CamelTimerCounter):
     * the value is null at runtime. Checked against the header metadata of every component the file names, with the
     * closest real name.
     */
    /**
     * Names a component sets as exchange properties, not headers; the catalog has no metadata for those, so the ones a
     * beginner reaches for are listed here (TimerConsumer sets them with setProperty).
     */
    static final Map<String, List<String>> EXCHANGE_PROPERTIES = Map.of(
            "timer", List.of("CamelTimerCounter", "CamelTimerName", "CamelTimerPeriod", "CamelTimerTime"));

    public static List<String> validateKnownHeaders(String content, CamelCatalog catalog) {
        List<String> msgs = new ArrayList<>();
        if (content == null) {
            return msgs;
        }
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
                    msgs.add("Line " + (i + 1) + ": " + name + " is an exchange property set by " + propertyOwner
                             + ", not a header (the header would be null): write ${exchangeProperty." + name + "}");
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
                }
                if (scheme != null) {
                    List<String> names = new ArrayList<>();
                    for (var e : owner.entrySet()) {
                        if (e.getValue().equals(scheme)) {
                            names.add(e.getKey());
                        }
                    }
                    sb.append(" The ").append(scheme).append(" headers are ").append(String.join(", ", names)).append(".");
                }
                msgs.add(sb.toString());
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
