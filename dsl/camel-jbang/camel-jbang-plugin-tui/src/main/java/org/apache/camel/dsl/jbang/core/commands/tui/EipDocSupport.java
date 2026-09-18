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
 * Stateless helpers that turn catalog metadata (EIP and component models) into markdown detail text and inline
 * quick-doc entries for the source viewer.
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

    static void buildEndpointInlineDoc(
            Map<Integer, List<SourceViewer.DocEntry>> result, List<JsonObject> cd,
            CamelCatalog catalog, String endpointUri, int eipIdx) {

        String component = endpointUri.contains(":") ? endpointUri.substring(0, endpointUri.indexOf(':')) : endpointUri;
        ComponentModel model = catalog.componentModel(component);
        if (model == null) {
            return;
        }

        String compTitle = model.getTitle() != null ? model.getTitle() : component;
        String desc = model.getDescription() != null ? truncateText(model.getDescription(), 80) : "";

        Map<String, BaseOptionModel> optionDocs = new LinkedHashMap<>();
        for (ComponentModel.EndpointOptionModel opt : model.getEndpointOptions()) {
            if (opt.getName() != null) {
                optionDocs.put(opt.getName(), opt);
            }
        }

        int beforeSize = result.size();
        List<SourceViewer.DocEntry> titleLines = new ArrayList<>();
        titleLines.add(SourceViewer.DocEntry.of(compTitle + " — " + desc));
        result.put(eipIdx, titleLines);

        inlineParameterDocs(result, cd, eipIdx, optionDocs);

        // For Java/XML where params are in the URI (not on separate source lines),
        // cluster the option docs under the title
        if (result.size() == beforeSize + 1) {
            clusterEndpointOptions(titleLines, catalog, endpointUri, optionDocs);
        }
    }

    private static void clusterEndpointOptions(
            List<SourceViewer.DocEntry> docLines, CamelCatalog catalog,
            String endpointUri, Map<String, BaseOptionModel> optionDocs) {
        try {
            Map<String, String> props = catalog.endpointProperties(endpointUri);
            if (props != null) {
                for (Map.Entry<String, String> entry : props.entrySet()) {
                    BaseOptionModel optModel = optionDocs.get(entry.getKey());
                    if (optModel != null) {
                        String optDoc = formatOptionDoc(optModel);
                        if (optDoc != null) {
                            String text = entry.getKey() + ": " + entry.getValue() + " — " + optDoc;
                            docLines.add(optModel.isDeprecated()
                                    ? SourceViewer.DocEntry.deprecated(text)
                                    : SourceViewer.DocEntry.of(text));
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore URI parse errors
        }
    }

    static void buildEipInlineDoc(
            Map<Integer, List<SourceViewer.DocEntry>> result, List<JsonObject> cd,
            CamelCatalog catalog, String type, JsonObject opts, int eipIdx) {

        EipModel model = catalog != null ? catalog.eipModel(type) : null;

        // For endpoint-bearing EIPs, resolve the component from the uri option
        ComponentModel compModel = null;
        if (opts != null && catalog != null) {
            Object uriObj = opts.get("uri");
            if (uriObj != null) {
                String uri = uriObj.toString();
                String comp = uri.contains(":") ? uri.substring(0, uri.indexOf(':')) : uri;
                compModel = catalog.componentModel(comp);
            }
        }

        List<SourceViewer.DocEntry> titleLines = new ArrayList<>();
        if (model != null && model.getTitle() != null) {
            String eipTitle = model.getTitle();
            if (compModel != null && compModel.getTitle() != null) {
                eipTitle += " (" + compModel.getTitle() + ")";
            }
            String desc;
            if (compModel != null && compModel.getDescription() != null) {
                desc = truncateText(compModel.getDescription(), 80);
            } else {
                desc = model.getDescription() != null ? truncateText(model.getDescription(), 80) : "";
            }
            titleLines.add(SourceViewer.DocEntry.of(eipTitle + " — " + desc));
        } else {
            titleLines.add(SourceViewer.DocEntry.of(type));
        }

        int beforeSize = result.size();
        result.put(eipIdx, titleLines);

        if (model != null) {
            Map<String, BaseOptionModel> optionDocs = new LinkedHashMap<>();
            for (BaseOptionModel opt : model.getOptions()) {
                if (opt.getName() != null) {
                    optionDocs.put(opt.getName(), opt);
                }
            }
            if (compModel != null) {
                for (ComponentModel.EndpointOptionModel opt : compModel.getEndpointOptions()) {
                    if (opt.getName() != null && !optionDocs.containsKey(opt.getName())) {
                        optionDocs.put(opt.getName(), opt);
                    }
                }
            }
            inlineParameterDocs(result, cd, eipIdx, optionDocs);

            // For Java/XML where options are on the same line,
            // cluster the option docs under the title
            if (result.size() == beforeSize + 1 && opts != null) {
                boolean hasParams = hasParametersChild(cd, eipIdx);
                clusterEipOptions(titleLines, opts, optionDocs, hasParams);
            }
        }
    }

    private static void clusterEipOptions(
            List<SourceViewer.DocEntry> docLines, JsonObject opts,
            Map<String, BaseOptionModel> optionDocs, boolean skipUri) {
        for (Map.Entry<String, Object> entry : opts.entrySet()) {
            if (skipUri && "uri".equals(entry.getKey())) {
                continue;
            }
            BaseOptionModel optModel = optionDocs.get(entry.getKey());
            if (optModel != null) {
                String optDoc = formatOptionDoc(optModel);
                if (optDoc != null) {
                    String text = entry.getKey() + ": " + entry.getValue() + " — " + optDoc;
                    docLines.add(optModel.isDeprecated()
                            ? SourceViewer.DocEntry.deprecated(text)
                            : SourceViewer.DocEntry.of(text));
                }
            }
        }
    }

    static boolean hasParametersChild(List<JsonObject> cd, int eipIdx) {
        int eipIndent = lineIndent(cd, eipIdx);
        for (int i = eipIdx + 1; i < cd.size(); i++) {
            String code = cd.get(i).get("code") != null ? cd.get(i).get("code").toString() : "";
            int indent = leadingSpaces(code);
            if (indent < eipIndent && !code.isBlank()) {
                break;
            }
            String trimmed = code.stripLeading();
            if (trimmed.startsWith("parameters:")) {
                return true;
            }
        }
        return false;
    }

    static void inlineParameterDocs(
            Map<Integer, List<SourceViewer.DocEntry>> result, List<JsonObject> cd,
            int eipIdx, Map<String, BaseOptionModel> optionDocs) {

        if (optionDocs.isEmpty()) {
            return;
        }

        int eipIndent = lineIndent(cd, eipIdx);
        int childIndent = -1;
        boolean inParameters = false;
        int paramIndent = -1;
        int parametersLineIndent = -1;

        for (int i = eipIdx + 1; i < cd.size(); i++) {
            String code = cd.get(i).get("code") != null ? cd.get(i).get("code").toString() : "";
            int indent = leadingSpaces(code);
            if (indent < eipIndent && !code.isBlank()) {
                break;
            }
            if (childIndent < 0 && indent > eipIndent) {
                childIndent = indent;
            }

            if (inParameters) {
                if (paramIndent < 0 && indent > parametersLineIndent && !code.isBlank()) {
                    paramIndent = indent;
                }
                if (paramIndent > 0 && indent <= parametersLineIndent && !code.isBlank()) {
                    inParameters = false;
                    paramIndent = -1;
                    parametersLineIndent = -1;
                } else if (paramIndent > 0 && indent == paramIndent) {
                    String trimmed = code.stripLeading();
                    int colon = trimmed.indexOf(':');
                    if (colon > 0) {
                        String key = trimmed.substring(0, colon).strip();
                        BaseOptionModel doc = optionDocs.get(key);
                        if (doc != null) {
                            String docLine = formatOptionDoc(doc);
                            if (docLine != null) {
                                int docIdx = lastContinuationLine(cd, i, indent);
                                result.put(docIdx, List.of(doc.isDeprecated()
                                        ? SourceViewer.DocEntry.deprecated(docLine)
                                        : SourceViewer.DocEntry.of(docLine)));
                            }
                        }
                    }
                    continue;
                } else {
                    continue;
                }
            }

            if (childIndent > 0 && indent != childIndent) {
                continue;
            }
            String trimmed = code.stripLeading();
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = trimmed.substring(0, colon).strip();
            if ("parameters".equals(key)) {
                inParameters = true;
                parametersLineIndent = indent;
                continue;
            }
            if ("uri".equals(key) || "steps".equals(key)
                    || "id".equals(key) || "description".equals(key)) {
                continue;
            }

            BaseOptionModel doc = optionDocs.get(key);
            if (doc != null) {
                String docLine = formatOptionDoc(doc);
                if (docLine != null) {
                    int docIdx = lastContinuationLine(cd, i, indent);
                    result.put(docIdx, List.of(doc.isDeprecated()
                            ? SourceViewer.DocEntry.deprecated(docLine)
                            : SourceViewer.DocEntry.of(docLine)));
                }
            }
        }
    }

    static int lastContinuationLine(List<JsonObject> cd, int keyIdx, int keyIndent) {
        int last = keyIdx;
        for (int j = keyIdx + 1; j < cd.size(); j++) {
            String c = cd.get(j).get("code") != null ? cd.get(j).get("code").toString() : "";
            if (c.isBlank()) {
                continue;
            }
            if (leadingSpaces(c) > keyIndent) {
                last = j;
            } else {
                break;
            }
        }
        return last;
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

    static int lineIndent(List<JsonObject> cd, int idx) {
        if (idx < 0 || idx >= cd.size()) {
            return 0;
        }
        String code = cd.get(idx).get("code") != null ? cd.get(idx).get("code").toString() : "";
        return leadingSpaces(code);
    }

    static int leadingSpaces(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ' ') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    static int findCodeDataIndex(List<JsonObject> cd, int targetLine, int fallback) {
        for (int i = 0; i < cd.size(); i++) {
            Integer lineNum = cd.get(i).getInteger("line");
            if (lineNum != null && lineNum == targetLine) {
                return i;
            }
        }
        return Math.max(0, fallback);
    }

    static String truncateText(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        int dot = text.indexOf('.');
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
