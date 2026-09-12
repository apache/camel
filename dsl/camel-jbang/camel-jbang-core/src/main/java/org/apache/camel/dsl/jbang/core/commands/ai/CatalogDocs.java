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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.EndpointValidationResult;
import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * The catalog documentation an AI agent authoring an integration asks for: a component, data format, language or EIP
 * with its options, the rules of an endpoint URI spelled out, the simple language's syntax, functions and operators,
 * and an endpoint URI checked against the catalog. Written for what a small model gets wrong most (a path option as a
 * query parameter, an operator inside a placeholder), and shared by the {@code camel_catalog_doc} tool of every Camel
 * MCP server.
 */
public final class CatalogDocs {

    /** The sub-pages of a language's documentation (simple has functions, operators, ognl and advanced). */
    private static final List<String> LANGUAGE_DOC_PAGES = List.of("functions", "operators", "ognl", "advanced");

    /**
     * The rules a small model gets wrong most: functions live inside the placeholder, operators between placeholders.
     * Sent with the simple language result so an answer's examples follow the same shape as the catalog's.
     */
    public static final String SIMPLE_SYNTAX = "Values and functions go inside ${...}: ${body}, ${header.name},"
                                               + " ${exchangeProperty.name}, ${variable.name}, ${random(1,10)},"
                                               + " ${date:now:yyyy-MM-dd}. Operators go BETWEEN placeholders, with spaces,"
                                               + " never inside one: ${header.foo} == 'bar', ${header.user} ?: 'Guest',"
                                               + " ${header.n} > 5 && ${body} != null, ${header.a} == 'x' ? 'yes' : 'no'."
                                               + " Text literals are in single quotes; text outside ${...} is kept as is:"
                                               + " Hello ${header.name}. Nesting works: ${header.${header.key}}.";

    private CatalogDocs() {
    }

    /**
     * The documentation of a catalog artifact, or the check of an endpoint URI when {@code endpoint} is given.
     *
     * @param  catalog        the catalog of the Camel version to answer for
     * @param  name           the artifact name (kafka, json-jackson, simple, choice); ignored when endpoint is given
     * @param  endpoint       an endpoint URI to validate instead of documenting an artifact
     * @param  kind           component, dataformat, language or eip; null to detect
     * @param  optionsFilter  keyword to match in option names, descriptions and groups; also picks the simple functions
     *                        and operators to list in full
     * @param  includeOptions whether to list the options
     * @param  includeDoc     whether to add the full AsciiDoc page
     * @param  docPage        a language doc sub-page (simple: functions, operators, ognl, advanced) to return as text
     * @return                the JSON result, an {@code error} object when nothing matches
     */
    public static JsonObject catalogDoc(
            CamelCatalog catalog, String name, String endpoint, String kind, String optionsFilter,
            boolean includeOptions, boolean includeDoc, String docPage) {
        if (endpoint != null && !endpoint.isBlank()) {
            return validateEndpoint(catalog, endpoint.trim());
        }
        if (name == null || name.isBlank()) {
            return error("'name' or 'endpoint' parameter is required");
        }
        String page = docPage != null ? docPage.trim().toLowerCase(Locale.ROOT) : null;
        String lowerFilter = optionsFilter != null && !optionsFilter.isBlank() ? optionsFilter.toLowerCase() : null;

        if (kind == null || "component".equals(kind)) {
            ComponentModel cm = catalog.componentModel(name);
            if (cm != null) {
                String doc = includeDoc ? catalog.asciiDoc(name + "-component") : null;
                return componentDoc(cm, lowerFilter, includeOptions, doc);
            }
            if (kind != null) {
                return notFound("Component", name, catalog.suggestComponentNames(name, 5));
            }
        }
        if (kind == null || "dataformat".equals(kind)) {
            DataFormatModel dm = catalog.dataFormatModel(name);
            if (dm != null) {
                String doc = includeDoc ? catalog.asciiDoc(name + "-dataformat") : null;
                return dataFormatDoc(dm, lowerFilter, includeOptions, doc);
            }
            if (kind != null) {
                return notFound("Data format", name, catalog.suggestDataFormatNames(name, 5));
            }
        }
        if (kind == null || "language".equals(kind)) {
            LanguageModel lm = catalog.languageModel(name);
            if (lm != null) {
                String doc = null;
                if (page != null && !page.isEmpty()) {
                    doc = catalog.asciiDoc(name + "-" + page);
                    if (doc == null) {
                        JsonObject err = error("No doc page '" + page + "' for language " + name);
                        err.put("docPages", new JsonArray(languageDocPages(catalog, name)));
                        return err;
                    }
                } else if (includeDoc) {
                    doc = catalog.asciiDoc(name + "-language");
                }
                boolean docPageOnly = page != null && !page.isEmpty();
                return languageDoc(lm, lowerFilter, includeOptions, doc, languageDocPages(catalog, name), docPageOnly);
            }
            if (kind != null) {
                return notFound("Language", name, catalog.suggestLanguageNames(name, 5));
            }
        }
        if (kind == null || "eip".equals(kind)) {
            EipModel em = catalog.eipModel(name);
            if (em != null) {
                String doc = includeDoc ? catalog.asciiDoc(name + "-eip") : null;
                return eipDoc(em, lowerFilter, includeOptions, doc);
            }
            if (kind != null) {
                return error("EIP not found: " + name);
            }
        }
        List<String> suggestions = new ArrayList<>(catalog.suggestComponentNames(name, 5));
        suggestions.addAll(catalog.suggestDataFormatNames(name, 3));
        suggestions.addAll(catalog.suggestLanguageNames(name, 3));
        return notFound("Artifact", name, suggestions);
    }

    /**
     * Finds the catalog artifacts matching a term that need not be a name: a protocol (mqtt, amqp), a product (s3,
     * snowflake) or a word of the title. Best match first, with the title and description so the caller can pick.
     *
     * @param catalog the catalog
     * @param term    what to look for
     * @param kind    component, dataformat or language; null for all three
     * @param limit   maximum matches per kind
     */
    public static JsonObject find(CamelCatalog catalog, String term, String kind, int limit) {
        if (term == null || term.isBlank()) {
            return error("'term' parameter is required");
        }
        int max = limit > 0 ? limit : 10;
        JsonObject result = new JsonObject();
        result.put("term", term);
        JsonArray matches = new JsonArray();
        if (kind == null || "component".equals(kind)) {
            for (String name : catalog.suggestComponentNames(term, max)) {
                ComponentModel m = catalog.componentModel(name);
                if (m != null) {
                    JsonObject o = summary("component", m.getScheme(), m.getTitle(), m.getDescription(), m.getLabel());
                    o.put("syntax", m.getSyntax());
                    matches.add(o);
                }
            }
        }
        if (kind == null || "dataformat".equals(kind)) {
            for (String name : catalog.suggestDataFormatNames(term, max)) {
                DataFormatModel m = catalog.dataFormatModel(name);
                if (m != null) {
                    matches.add(summary("dataformat", m.getName(), m.getTitle(), m.getDescription(), m.getLabel()));
                }
            }
        }
        if (kind == null || "language".equals(kind)) {
            for (String name : catalog.suggestLanguageNames(term, max)) {
                LanguageModel m = catalog.languageModel(name);
                if (m != null) {
                    matches.add(summary("language", m.getName(), m.getTitle(), m.getDescription(), m.getLabel()));
                }
            }
        }
        result.put("matches", matches);
        result.put("count", matches.size());
        if (matches.isEmpty()) {
            result.put("message", "Nothing in the catalog matches '" + term + "'");
        } else {
            result.put("message", "Best match first; camel_catalog_doc gives the options of one");
        }
        return result;
    }

    private static JsonObject summary(String kind, String name, String title, String description, String label) {
        JsonObject o = new JsonObject();
        o.put("kind", kind);
        o.put("name", name);
        o.put("title", title);
        if (description != null) {
            o.put("description", description);
        }
        if (label != null) {
            o.put("label", label);
        }
        return o;
    }

    /**
     * Checks an endpoint URI against the catalog the way the YAML validator does on a write: the component is taken
     * from the scheme, the path is parsed against the component's syntax and every option is checked. The problems come
     * back in words, with the closest real option name or the allowed values, and the options the URI uses come with
     * their documentation so a follow-up needs no second call.
     */
    public static JsonObject validateEndpoint(CamelCatalog catalog, String endpoint) {
        int colon = endpoint.indexOf(':');
        if (colon <= 0) {
            return error("Not an endpoint URI (scheme:path?options expected): " + endpoint);
        }
        String scheme = endpoint.substring(0, colon).toLowerCase(Locale.ROOT);
        ComponentModel cm = catalog.componentModel(scheme);
        if (cm == null) {
            JsonObject err = error("Camel has no component named '" + scheme + "'");
            List<String> similar = catalog.suggestComponentNames(scheme, 5);
            if (!similar.isEmpty()) {
                err.put("suggestions", new JsonArray(similar));
                err.put("hint", "Check again with one of those schemes, e.g. '" + similar.get(0)
                                + endpoint.substring(colon) + "'.");
            }
            return err;
        }
        JsonObject result = new JsonObject();
        result.put("kind", "component");
        result.put("name", scheme);
        result.put("title", cm.getTitle());
        result.put("endpoint", endpoint);
        if (cm.getSyntax() != null) {
            result.put("syntax", cm.getSyntax());
            result.put("uriSyntax", uriSyntax(cm));
        }
        EndpointValidationResult validation;
        try {
            validation = catalog.validateEndpointProperties(endpoint, false, false, false);
        } catch (Exception e) {
            result.put("valid", false);
            result.put("problems", new JsonArray(List.of("Cannot parse the URI: " + e.getMessage())));
            return result;
        }
        List<String> problems = endpointProblems(validation);
        List<String> warnings = new ArrayList<>();
        if (validation.getDeprecated() != null) {
            for (String name : validation.getDeprecated()) {
                warnings.add("Option '" + name + "' is deprecated");
            }
        }
        if (validation.getDefaultValues() != null) {
            for (Map.Entry<String, String> entry : validation.getDefaultValues().entrySet()) {
                warnings.add("Option '" + entry.getKey() + "' is set to its default value " + entry.getValue());
            }
        }
        result.put("valid", problems.isEmpty());
        result.put("problems", new JsonArray(problems));
        if (!warnings.isEmpty()) {
            result.put("warnings", new JsonArray(warnings));
        }
        // the options the URI uses (path parts included), with their catalog documentation
        Map<String, String> used;
        try {
            used = catalog.endpointProperties(endpoint);
        } catch (Exception e) {
            used = Map.of();
        }
        JsonArray options = new JsonArray();
        if (cm.getEndpointOptions() != null) {
            for (BaseOptionModel opt : cm.getEndpointOptions()) {
                if (used.containsKey(opt.getName())) {
                    JsonObject o = optionToJson(opt, "endpoint");
                    o.put("value", used.get(opt.getName()));
                    options.add(o);
                }
            }
        }
        result.put("usedOptions", options);
        result.put("message", problems.isEmpty()
                ? "The URI is valid for the " + scheme + " component"
                : problems.size() + " problem(s); fix them before using the URI");
        return result;
    }

    /** The problems of an endpoint validation in words, one per entry, with suggestions and allowed values. */
    public static List<String> endpointProblems(EndpointValidationResult r) {
        List<String> problems = new ArrayList<>();
        if (r.getSyntaxError() != null) {
            problems.add("Syntax error: " + r.getSyntaxError());
        }
        if (r.getUnknownComponent() != null) {
            problems.add("Unknown component: " + r.getUnknownComponent());
        }
        if (r.getIncapable() != null) {
            problems.add("Cannot validate: " + r.getIncapable());
        }
        if (r.getUnknown() != null) {
            for (String name : r.getUnknown()) {
                StringBuilder sb = new StringBuilder("Unknown option '").append(name).append("'");
                // the catalog suggests the closest names itself (edit distance, CAMEL-24666)
                String[] suggestions = r.getUnknownSuggestions() != null ? r.getUnknownSuggestions().get(name) : null;
                if (suggestions != null && suggestions.length > 0) {
                    sb.append(". Did you mean: ").append(Arrays.asList(suggestions));
                }
                problems.add(sb.toString());
            }
        }
        if (r.getRequired() != null) {
            for (String name : r.getRequired()) {
                problems.add("Missing required option '" + name + "'");
            }
        }
        if (r.getInvalidEnum() != null) {
            for (Map.Entry<String, String> entry : r.getInvalidEnum().entrySet()) {
                StringBuilder sb = new StringBuilder("Invalid value '").append(entry.getValue())
                        .append("' for option '").append(entry.getKey()).append("'");
                String[] choices = r.getInvalidEnumChoices() != null ? r.getInvalidEnumChoices().get(entry.getKey()) : null;
                if (choices != null) {
                    sb.append(". Possible values: ").append(Arrays.asList(choices));
                }
                problems.add(sb.toString());
            }
        }
        addInvalid(problems, r.getInvalidBoolean(), "boolean");
        addInvalid(problems, r.getInvalidInteger(), "integer");
        addInvalid(problems, r.getInvalidNumber(), "number");
        addInvalid(problems, r.getInvalidDuration(), "duration");
        addInvalid(problems, r.getInvalidReference(), "reference (#bean)");
        addInvalid(problems, r.getInvalidMap(), "map");
        addInvalid(problems, r.getInvalidArray(), "array");
        if (r.getNotConsumerOnly() != null) {
            for (String name : r.getNotConsumerOnly()) {
                problems.add("Option '" + name + "' is a producer option; not for a from (consumer) endpoint");
            }
        }
        if (r.getNotProducerOnly() != null) {
            for (String name : r.getNotProducerOnly()) {
                problems.add("Option '" + name + "' is a consumer option; not for a to (producer) endpoint");
            }
        }
        return problems;
    }

    private static void addInvalid(List<String> problems, Map<String, String> invalid, String type) {
        if (invalid != null) {
            for (Map.Entry<String, String> entry : invalid.entrySet()) {
                problems.add("Invalid " + type + " value '" + entry.getValue() + "' for option '" + entry.getKey() + "'");
            }
        }
    }

    private static JsonObject error(String message) {
        JsonObject err = new JsonObject();
        err.put("error", message);
        return err;
    }

    /**
     * Error for a catalog lookup that found nothing, with the names the catalog suggests for the term (a protocol or
     * product name such as mqtt or s3) so the next call can use one of them.
     */
    private static JsonObject notFound(String kind, String name, List<String> suggestions) {
        JsonObject err = error(kind + " not found: " + name);
        if (!suggestions.isEmpty()) {
            err.put("suggestions", new JsonArray(suggestions));
        }
        return err;
    }

    private static void addCommonModelFields(JsonObject result, BaseModel<?> model) {
        if (model.getFirstVersion() != null) {
            result.put("since", model.getFirstVersion());
        }
        if (model.getSupportLevel() != null) {
            result.put("supportLevel", model.getSupportLevel().name());
        }
        if (model.isNativeSupported()) {
            result.put("nativeSupported", true);
        }
        if (model.isDeprecated()) {
            result.put("deprecated", true);
            if (model.getDeprecatedSince() != null) {
                result.put("deprecatedSince", model.getDeprecatedSince());
            }
            if (model.getDeprecationNote() != null) {
                result.put("deprecationNote", model.getDeprecationNote());
            }
        }
    }

    /**
     * The rules of an endpoint URI for this component, spelled out with its own path parts: what a small model gets
     * wrong most is a path option written as a query parameter or the other way round, and the YAML form.
     */
    public static String uriSyntax(ComponentModel model) {
        List<String> path = new ArrayList<>();
        if (model.getEndpointOptions() != null) {
            for (BaseOptionModel opt : model.getEndpointOptions()) {
                if ("path".equals(opt.getKind())) {
                    path.add(opt.getName());
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("URI: ").append(model.getSyntax()).append("?option=value&option=value. ");
        if (path.isEmpty()) {
            sb.append("The path has no options; ");
        } else {
            sb.append("The path options (").append(String.join(", ", path))
                    .append(") go in the path, never as ?name=value; ");
        }
        sb.append("every other endpoint option is a query parameter after ?, separated by &.")
                .append(" In YAML DSL: uri: ").append(model.getScheme()).append(":<path> plus a parameters: map of the")
                .append(" query options (or the full URI in uri). Values may use {{property.placeholders}};")
                .append(" wrap a value containing & or + in RAW(value). Component options (scope component) are")
                .append(" set in application.properties as camel.component.").append(model.getScheme())
                .append(".<option>=value, not on the URI.");
        return sb.toString();
    }

    private static JsonObject componentDoc(ComponentModel model, String filter, boolean includeOptions, String doc) {
        JsonObject result = new JsonObject();
        result.put("kind", "component");
        result.put("name", model.getScheme());
        result.put("title", model.getTitle());
        result.put("description", model.getDescription());
        if (model.getLabel() != null) {
            result.put("label", model.getLabel());
        }
        if (model.getSyntax() != null) {
            result.put("syntax", model.getSyntax());
            result.put("uriSyntax", uriSyntax(model));
        }
        result.put("consumerOnly", model.isConsumerOnly());
        result.put("producerOnly", model.isProducerOnly());
        result.put("remote", model.isRemote());
        result.put("groupId", model.getGroupId());
        result.put("artifactId", model.getArtifactId());
        addCommonModelFields(result, model);

        if (includeOptions) {
            JsonArray options = new JsonArray();
            if (model.getComponentOptions() != null) {
                for (BaseOptionModel opt : model.getComponentOptions()) {
                    if (matchesOptionFilter(opt, filter)) {
                        options.add(optionToJson(opt, "component"));
                    }
                }
            }
            if (model.getEndpointOptions() != null) {
                for (BaseOptionModel opt : model.getEndpointOptions()) {
                    if (matchesOptionFilter(opt, filter)) {
                        options.add(optionToJson(opt, "endpoint"));
                    }
                }
            }
            result.put("options", options);
            result.put("matchedOptions", options.size());
        }
        if (doc != null) {
            result.put("doc", doc);
        }
        return result;
    }

    private static JsonObject dataFormatDoc(DataFormatModel model, String filter, boolean includeOptions, String doc) {
        JsonObject result = new JsonObject();
        result.put("kind", "dataformat");
        result.put("name", model.getName());
        result.put("title", model.getTitle());
        result.put("description", model.getDescription());
        if (model.getLabel() != null) {
            result.put("label", model.getLabel());
        }
        result.put("groupId", model.getGroupId());
        result.put("artifactId", model.getArtifactId());
        addCommonModelFields(result, model);
        if (includeOptions) {
            result.put("options", filteredOptions(model.getOptions(), filter));
            result.put("matchedOptions", ((JsonArray) result.get("options")).size());
        }
        if (doc != null) {
            result.put("doc", doc);
        }
        return result;
    }

    private static List<String> languageDocPages(CamelCatalog catalog, String name) {
        List<String> pages = new ArrayList<>();
        for (String page : LANGUAGE_DOC_PAGES) {
            if (catalog.asciiDoc(name + "-" + page) != null) {
                pages.add(page);
            }
        }
        return pages;
    }

    private static JsonObject languageDoc(
            LanguageModel model, String filter, boolean includeOptions, String doc, List<String> docPages,
            boolean docPageOnly) {
        JsonObject result = new JsonObject();
        result.put("kind", "language");
        result.put("name", model.getName());
        result.put("title", model.getTitle());
        result.put("description", model.getDescription());
        if (model.getLabel() != null) {
            result.put("label", model.getLabel());
        }
        result.put("groupId", model.getGroupId());
        result.put("artifactId", model.getArtifactId());
        addCommonModelFields(result, model);

        // a requested doc page is the answer; the options, functions and operators would only add tokens around it
        if (includeOptions && !docPageOnly) {
            result.put("options", filteredOptions(model.getOptions(), filter));
            result.put("matchedOptions", ((JsonArray) result.get("options")).size());
        }
        if (!docPageOnly) {
            addLanguageFunctions(result, model, filter);
        }
        if ("simple".equals(model.getName()) || "csimple".equals(model.getName())) {
            result.put("syntax", SIMPLE_SYNTAX);
        }
        if (!docPages.isEmpty()) {
            result.put("docPages", new JsonArray(docPages));
            result.put("docPagesHint", "docPage=<name> returns that documentation page as text");
        }
        if (doc != null) {
            result.put("doc", doc);
        }
        return result;
    }

    /**
     * The functions and operators of a language that has them (simple): without a filter their count and names by
     * group, which answers "what is there" in a few hundred tokens; with a filter the matching ones in full, with
     * parameters and examples, the way the options are filtered.
     */
    private static void addLanguageFunctions(JsonObject result, LanguageModel model, String filter) {
        List<LanguageModel.LanguageFunctionModel> functions = model.getFunctions();
        if (functions != null && !functions.isEmpty()) {
            result.put("functionCount", functions.size());
            if (filter != null) {
                JsonArray arr = new JsonArray();
                for (LanguageModel.LanguageFunctionModel fn : functions) {
                    if (matchesOptionFilter(fn, filter)
                            || (fn.getDisplayName() != null && fn.getDisplayName().toLowerCase().contains(filter))) {
                        arr.add(functionToJson(fn));
                    }
                }
                result.put("functions", arr);
                result.put("matchedFunctions", arr.size());
            } else {
                Map<String, JsonArray> groups = new TreeMap<>();
                for (LanguageModel.LanguageFunctionModel fn : functions) {
                    String group = fn.getGroup() != null ? fn.getGroup() : "other";
                    groups.computeIfAbsent(group, g -> new JsonArray()).add(fn.getName());
                }
                result.put("functionGroups", new JsonObject(groups));
                result.put("functionsHint", "optionsFilter with a function name, a group above or a word from its"
                                            + " description returns the matching functions with their parameters"
                                            + " and examples");
            }
        }
        List<LanguageModel.LanguageOperatorModel> operators = model.getOperators();
        if (operators != null && !operators.isEmpty()) {
            result.put("operatorCount", operators.size());
            if (filter != null) {
                JsonArray arr = new JsonArray();
                for (LanguageModel.LanguageOperatorModel op : operators) {
                    if (matchesOptionFilter(op, filter)
                            || (op.getOperatorKind() != null && op.getOperatorKind().toLowerCase().contains(filter))) {
                        arr.add(operatorToJson(op));
                    }
                }
                result.put("operators", arr);
                result.put("matchedOperators", arr.size());
            } else {
                JsonArray syntaxes = new JsonArray();
                for (LanguageModel.LanguageOperatorModel op : operators) {
                    syntaxes.add(op.getOperatorSyntax() != null ? op.getOperatorSyntax() : op.getName());
                }
                result.put("operatorSyntax", syntaxes);
            }
        }
    }

    private static JsonObject functionToJson(LanguageModel.LanguageFunctionModel fn) {
        JsonObject o = new JsonObject();
        o.put("name", fn.getName());
        if (fn.getDisplayName() != null) {
            o.put("displayName", fn.getDisplayName());
        }
        if (fn.getGroup() != null) {
            o.put("group", fn.getGroup());
        }
        if (fn.getJavaType() != null) {
            o.put("javaType", fn.getJavaType());
        }
        if (fn.getDescription() != null) {
            o.put("description", fn.getDescription());
        }
        if (fn.getParams() != null && !fn.getParams().isEmpty()) {
            JsonArray params = new JsonArray();
            for (LanguageModel.FunctionParamModel param : fn.getParams()) {
                JsonObject p = new JsonObject();
                p.put("name", param.getName());
                if (param.getJavaType() != null) {
                    p.put("javaType", param.getJavaType());
                }
                p.put("required", param.isRequired());
                if (param.getDescription() != null) {
                    p.put("description", param.getDescription());
                }
                params.add(p);
            }
            o.put("params", params);
        }
        if (fn.getExamples() != null && !fn.getExamples().isEmpty()) {
            o.put("examples", new JsonArray(fn.getExamples()));
        }
        if (fn.isOgnl()) {
            o.put("ognl", true);
        }
        if (fn.isDeprecated()) {
            o.put("deprecated", true);
        }
        return o;
    }

    private static JsonObject operatorToJson(LanguageModel.LanguageOperatorModel op) {
        JsonObject o = new JsonObject();
        o.put("name", op.getName());
        if (op.getDisplayName() != null) {
            o.put("displayName", op.getDisplayName());
        }
        if (op.getOperatorKind() != null) {
            o.put("kind", op.getOperatorKind());
        }
        if (op.getOperatorSyntax() != null) {
            o.put("syntax", op.getOperatorSyntax());
        }
        if (op.getDescription() != null) {
            o.put("description", op.getDescription());
        }
        if (op.getExamples() != null && !op.getExamples().isEmpty()) {
            o.put("examples", new JsonArray(op.getExamples()));
        }
        return o;
    }

    private static JsonObject eipDoc(EipModel model, String filter, boolean includeOptions, String doc) {
        JsonObject result = new JsonObject();
        result.put("kind", "eip");
        result.put("name", model.getName());
        result.put("title", model.getTitle());
        result.put("description", model.getDescription());
        if (model.getLabel() != null) {
            result.put("label", model.getLabel());
        }
        result.put("input", model.isInput());
        result.put("output", model.isOutput());
        addCommonModelFields(result, model);
        if (includeOptions) {
            result.put("options", filteredOptions(model.getOptions(), filter));
            result.put("matchedOptions", ((JsonArray) result.get("options")).size());
        }
        if (doc != null) {
            result.put("doc", doc);
        }
        return result;
    }

    private static JsonArray filteredOptions(List<? extends BaseOptionModel> options, String filter) {
        JsonArray arr = new JsonArray();
        if (options != null) {
            for (BaseOptionModel opt : options) {
                if (matchesOptionFilter(opt, filter)) {
                    arr.add(optionToJson(opt, null));
                }
            }
        }
        return arr;
    }

    private static boolean matchesOptionFilter(BaseOptionModel opt, String filter) {
        if (filter == null) {
            return true;
        }
        return (opt.getName() != null && opt.getName().toLowerCase().contains(filter))
                || (opt.getDescription() != null && opt.getDescription().toLowerCase().contains(filter))
                || (opt.getGroup() != null && opt.getGroup().toLowerCase().contains(filter))
                || (opt.getLabel() != null && opt.getLabel().toLowerCase().contains(filter));
    }

    /** One option as the tools return it; {@code scope} is component or endpoint for a component's options. */
    public static JsonObject optionToJson(BaseOptionModel opt, String scope) {
        JsonObject o = new JsonObject();
        o.put("name", opt.getName());
        o.put("description", opt.getDescription());
        o.put("type", opt.getType());
        o.put("required", opt.isRequired());
        if ("path".equals(opt.getKind()) || "parameter".equals(opt.getKind())) {
            // an endpoint option is either part of the URI path or a query parameter; a model must not mix them up
            o.put("kind", opt.getKind());
        }
        if (opt.getDefaultValue() != null) {
            o.put("defaultValue", opt.getDefaultValue().toString());
        }
        if (opt.getGroup() != null) {
            o.put("group", opt.getGroup());
        }
        if (scope != null) {
            o.put("scope", scope);
        }
        if (opt.isDeprecated()) {
            o.put("deprecated", true);
        }
        if (opt.isSecret()) {
            o.put("secret", true);
        }
        if (opt.getEnums() != null && !opt.getEnums().isEmpty()) {
            JsonArray enums = new JsonArray();
            enums.addAll(opt.getEnums());
            o.put("enumValues", enums);
        }
        return o;
    }
}
