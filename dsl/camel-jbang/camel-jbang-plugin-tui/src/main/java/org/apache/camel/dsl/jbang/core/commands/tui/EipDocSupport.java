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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Stateless helpers that turn catalog metadata (EIP and component models) into markdown detail text and option docs for
 * the source viewer.
 * <p>
 * Shared by {@link RoutesTab}, {@link DiagramTab} and {@link SourceTab}.
 */
final class EipDocSupport {

    private EipDocSupport() {
    }

    static List<JsonObject> getAllProcessors(JsonObject routeDetail) {
        if (routeDetail == null) {
            return List.of();
        }
        JsonArray routes = (JsonArray) routeDetail.get("routes");
        if (routes != null) {
            List<JsonObject> all = new ArrayList<>();
            for (Object obj : routes) {
                JsonObject route = (JsonObject) obj;
                JsonArray procs = (JsonArray) route.get("processors");
                if (procs != null) {
                    for (Object p : procs) {
                        all.add((JsonObject) p);
                    }
                }
            }
            return all;
        }
        JsonArray procs = (JsonArray) routeDetail.get("processors");
        if (procs != null) {
            List<JsonObject> all = new ArrayList<>();
            for (Object p : procs) {
                all.add((JsonObject) p);
            }
            return all;
        }
        return List.of();
    }

    static String formatOptionDoc(BaseOptionModel doc) {
        StringBuilder sb = new StringBuilder();
        if (doc.getDescription() != null && !doc.getDescription().isEmpty()) {
            sb.append(truncateText(doc.getDescription(), 80));
        }
        List<String> meta = new ArrayList<>();
        if (doc.getType() != null) {
            meta.add(doc.getType());
        }
        if (doc.isRequired()) {
            meta.add("required");
        }
        if (doc.getDefaultValue() != null) {
            meta.add("default: " + doc.getDefaultValue());
        }
        if (!meta.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append("(").append(String.join(", ", meta)).append(")");
        }
        return !sb.isEmpty() ? sb.toString() : null;
    }

    /**
     * The period that ends the first sentence: one followed by a space or the end of the text, and not the period of an
     * abbreviation (e.g., i.e.) - not the dot of (i.e., a number such as 0.5, or a name such as file.txt.
     */
    static int sentenceEnd(String text) {
        for (int i = text.indexOf('.'); i >= 0; i = text.indexOf('.', i + 1)) {
            boolean end = i + 1 == text.length() || Character.isWhitespace(text.charAt(i + 1));
            if (end && !text.regionMatches(true, Math.max(0, i - 3), "e.g", 0, 3)
                    && !text.regionMatches(true, Math.max(0, i - 3), "i.e", 0, 3)) {
                return i;
            }
        }
        return -1;
    }

    static String truncateText(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        int dot = sentenceEnd(text);
        if (dot > 0 && dot < maxLen) {
            return text.substring(0, dot + 1);
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen - 3) + "...";
    }

    private static void appendOptionDetail(StringBuilder md, String optName, String optValue, BaseOptionModel doc) {
        md.append("---\n\n");
        md.append("**").append(optName).append("** = **").append(optValue).append("**\n\n");

        if (doc != null) {
            if (doc.getDescription() != null && !doc.getDescription().isEmpty()) {
                md.append(doc.getDescription()).append("\n\n");
            }
            List<String> meta = new ArrayList<>();
            if (doc.getType() != null) {
                meta.add("Type: `" + doc.getType() + "`");
            }
            if (doc.getDefaultValue() != null) {
                meta.add("Default: `" + doc.getDefaultValue() + "`");
            }
            if (doc.isRequired()) {
                meta.add("Required: yes");
            }
            if (doc.getEnums() != null && !doc.getEnums().isEmpty()) {
                meta.add("Enum: " + String.join(", ", doc.getEnums()));
            }
            if (doc.getGroup() != null && !doc.getGroup().isEmpty()) {
                meta.add("Group: " + doc.getGroup());
            }
            if (doc.isDeprecated()) {
                String depText = "Deprecated";
                if (doc.getDeprecationNote() != null && !doc.getDeprecationNote().isEmpty()) {
                    depText += " — " + doc.getDeprecationNote();
                }
                meta.add(depText);
            }
            if (!meta.isEmpty()) {
                for (String m : meta) {
                    md.append("- ").append(m).append("\n");
                }
                md.append("\n");
            }
        }
    }

    static void renderEipDetail(StringBuilder md, CamelCatalog catalog, String type, JsonObject opts) {
        EipModel model = catalog != null ? catalog.eipModel(type) : null;

        if (model != null && model.getTitle() != null) {
            md.append("## ").append(model.getTitle()).append("\n\n");
            if (model.getDescription() != null && !model.getDescription().isEmpty()) {
                md.append(model.getDescription()).append("\n\n");
            }
        } else {
            md.append("## ").append(type).append("\n\n");
        }

        if (opts != null && !opts.isEmpty()) {
            Map<String, BaseOptionModel> optionDocs = new LinkedHashMap<>();
            if (model != null) {
                for (BaseOptionModel opt : model.getOptions()) {
                    if (opt.getName() != null) {
                        optionDocs.put(opt.getName(), opt);
                    }
                }
            }

            for (Map.Entry<String, Object> entry : opts.entrySet()) {
                String optName = entry.getKey();
                String optValue = String.valueOf(entry.getValue());
                appendOptionDetail(md, optName, optValue, optionDocs.get(optName));
            }
        } else {
            md.append("*No configured options*\n");
        }
    }

    static void renderEndpointDetail(StringBuilder md, CamelCatalog catalog, String uri) {
        String component = uri.contains(":") ? uri.substring(0, uri.indexOf(':')) : uri;
        ComponentModel model = catalog.componentModel(component);

        if (model != null) {
            String compTitle = model.getTitle() != null ? model.getTitle() : component;
            md.append("## ").append(compTitle).append("\n\n");
            if (model.getDescription() != null && !model.getDescription().isEmpty()) {
                md.append(model.getDescription()).append("\n\n");
            }

            Map<String, String> parsedOptions;
            try {
                parsedOptions = catalog.endpointProperties(uri);
            } catch (URISyntaxException e) {
                parsedOptions = Map.of();
            }

            if (parsedOptions.isEmpty()) {
                md.append("*No configured options*\n");
            } else {
                Map<String, BaseOptionModel> optionDocs = new LinkedHashMap<>();
                for (ComponentModel.EndpointOptionModel opt : model.getEndpointOptions()) {
                    if (opt.getName() != null) {
                        optionDocs.put(opt.getName(), opt);
                    }
                }

                for (Map.Entry<String, String> entry : parsedOptions.entrySet()) {
                    appendOptionDetail(md, entry.getKey(), entry.getValue(), optionDocs.get(entry.getKey()));
                }
            }
        } else {
            md.append("## ").append(component).append("\n\n");
            md.append("*No catalog documentation for: ").append(component).append("*\n");
        }
    }
}
