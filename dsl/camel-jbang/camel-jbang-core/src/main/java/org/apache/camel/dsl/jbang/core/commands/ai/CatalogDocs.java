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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.EndpointValidationResult;
import org.apache.camel.tooling.model.ApiReferenceModel;
import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.model.PojoBeanModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * The catalog documentation an AI agent authoring an integration asks for: a component, data format, language or EIP
 * with its options, the rules of an endpoint URI spelled out, the simple language's syntax, functions and operators, an
 * endpoint URI checked against the catalog, and the Java API a route author's code touches (Exchange, Message,
 * CamelContext, ...) with the variables a script language binds. Written for what a small model gets wrong most (a path
 * option as a query parameter, an operator inside a placeholder, a bean name used as a Groovy variable), and shared by
 * the {@code camel_catalog_doc} tool of every Camel MCP server.
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
     * @param  kind           component, dataformat, language, eip, bean or api; null to detect
     * @param  optionsFilter  keyword to match in option names, descriptions and groups; also picks the simple functions
     *                        and operators to list in full
     * @param  includeOptions which options to list: {@code common} (the default; without the deprecated and advanced
     *                        ones), {@code required}, {@code all} or {@code false}; {@code true} is {@code common}. A
     *                        filter searches all options
     * @param  includeHeaders whether to list the message headers of a component (name, constant, type, group)
     * @param  includeDoc     whether to add the full AsciiDoc page
     * @param  docPage        a language doc sub-page (simple: functions, operators, ognl, advanced) to return as text
     * @return                the JSON result, an {@code error} object when nothing matches
     */
    public static JsonObject catalogDoc(
            CamelCatalog catalog, String name, String endpoint, String kind, String optionsFilter,
            String includeOptions, boolean includeHeaders, boolean includeDoc, String docPage) {
        if (endpoint != null && !endpoint.isBlank()) {
            return validateEndpoint(catalog, endpoint.trim());
        }
        if (name == null || name.isBlank()) {
            return error("'name' or 'endpoint' parameter is required");
        }
        String page = docPage != null ? docPage.trim().toLowerCase(Locale.ROOT) : null;
        String lowerFilter = optionsFilter != null && !optionsFilter.isBlank() ? optionsFilter.toLowerCase() : null;
        OptionScope scope = OptionScope.parse(includeOptions);
        if (scope == null) {
            return error("includeOptions must be common, required, all, true or false, got: " + includeOptions);
        }

        if (kind == null || "component".equals(kind)) {
            ComponentModel cm = catalog.componentModel(name);
            if (cm != null) {
                String doc = includeDoc ? catalog.asciiDoc(name + "-component") : null;
                return componentDoc(cm, lowerFilter, scope, includeHeaders, doc);
            }
            JsonObject group = mainOptionsGroup(catalog, name);
            if (group != null) {
                // resilience4j, health, metrics: not a component but a group of camel.<name>.* main options
                return group;
            }
            if (kind != null) {
                return notFound("Component", name, catalog.suggestComponentNames(name, 5));
            }
        }
        if (kind == null || "dataformat".equals(kind)) {
            DataFormatModel dm = catalog.dataFormatModel(name);
            if (dm != null) {
                String doc = includeDoc ? catalog.asciiDoc(name + "-dataformat") : null;
                return dataFormatDoc(dm, lowerFilter, scope, doc);
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
                return languageDoc(lm, lowerFilter, scope, doc, languageDocPages(catalog, name), docPageOnly);
            }
            if (kind != null) {
                return notFound("Language", name, catalog.suggestLanguageNames(name, 5));
            }
        }
        if (kind == null || "eip".equals(kind)) {
            EipModel em = catalog.eipModel(name);
            if (em != null) {
                return eipDoc(catalog, em, lowerFilter, scope, includeDoc, null);
            }
            if (kind != null) {
                // an alias (fan-out, dedup, rate-limit) or a word of the title names the EIP as well
                JsonObject byTerm = eipByTerm(catalog, name, lowerFilter, scope, includeDoc);
                if (byTerm != null) {
                    return byTerm;
                }
                return notFound("EIP", name, catalog.suggestEipNames(name, 5));
            }
        }
        if (kind == null || "api".equals(kind)) {
            // the API of a core class (Exchange, AggregationStrategy) before the beans: an exact card name is more
            // specific than a bean implementing the interface, which the card lists anyway
            JsonObject api = apiDoc(catalog, name, kind != null);
            if (api != null) {
                return api;
            }
        }
        if (kind == null || "bean".equals(kind)) {
            PojoBeanModel bm = catalog.pojoBeanModel(name);
            if (bm == null) {
                // by class name or interface: the first match
                List<PojoBeanModel> beans = findBeans(catalog, name);
                if (!beans.isEmpty() && (kind != null || name.contains(".") || name.endsWith("Strategy")
                        || name.endsWith("Repository") || name.endsWith("Policy"))) {
                    bm = beans.get(0);
                    name = bm.getName();
                }
            }
            if (bm != null) {
                return beanDoc(catalog, bm, name);
            }
            if (kind != null) {
                return notFound("Bean", name, findBeans(catalog, name).stream().map(PojoBeanModel::getName).limit(5).toList());
            }
        }
        JsonObject group = mainOptionsGroup(catalog, name);
        if (group != null) {
            return group;
        }
        // nothing has the exact name: an EIP alias such as fan-out or dedup is the last thing the name can be
        JsonObject eip = eipByTerm(catalog, name, lowerFilter, scope, includeDoc);
        if (eip != null) {
            return eip;
        }
        List<String> suggestions = new ArrayList<>(catalog.suggestComponentNames(name, 5));
        suggestions.addAll(catalog.suggestDataFormatNames(name, 3));
        suggestions.addAll(catalog.suggestLanguageNames(name, 3));
        suggestions.addAll(catalog.suggestEipNames(name, 3));
        suggestions.addAll(findBeans(catalog, name).stream().map(PojoBeanModel::getName).limit(3).toList());
        return notFound("Artifact", name, suggestions);
    }

    /**
     * The documentation of the EIP a term such as an alias (fan-out, dedup, rate-limit) or a word of the title names,
     * with the term the caller used; null when no EIP matches.
     */
    private static JsonObject eipByTerm(
            CamelCatalog catalog, String term, String filter, OptionScope scope, boolean includeDoc) {
        List<String> names = catalog.suggestEipNames(term, 1);
        if (names.isEmpty()) {
            return null;
        }
        EipModel em = catalog.eipModel(names.get(0));
        return em == null ? null : eipDoc(catalog, em, filter, scope, includeDoc, term);
    }

    private static JsonObject eipDoc(
            CamelCatalog catalog, EipModel model, String filter, OptionScope scope, boolean includeDoc,
            String matchedTerm) {
        String doc = includeDoc ? catalog.asciiDoc(model.getName() + "-eip") : null;
        JsonObject result = eipDoc(model, filter, scope, doc);
        if (matchedTerm != null) {
            result.put("matchedTerm", matchedTerm);
        }
        return result;
    }

    /**
     * The camel.&lt;name&gt;.* main options when the name is one of their groups (resilience4j, faulttolerance, health,
     * metrics, threadpool, rest, ...), else null. A model asks for "resilience4j" as a component when it wants the
     * application.properties keys of the circuit breaker.
     */
    static JsonObject mainOptionsGroup(CamelCatalog catalog, String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (n.startsWith("camel.")) {
            n = n.substring("camel.".length());
        }
        if (n.endsWith(".")) {
            n = n.substring(0, n.length() - 1);
        }
        if (n.isEmpty() || n.contains(".")) {
            return null;
        }
        String prefix = "camel." + n + ".";
        JsonArray options = new JsonArray();
        try {
            for (var o : catalog.mainModel().getOptions()) {
                if (!o.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    continue;
                }
                JsonObject jo = new JsonObject();
                jo.put("name", o.getName());
                if (o.getType() != null) {
                    jo.put("type", o.getType());
                }
                if (o.getDefaultValue() != null) {
                    jo.put("defaultValue", o.getDefaultValue().toString());
                }
                if (o.getDescription() != null) {
                    jo.put("description", o.getDescription());
                }
                options.add(jo);
            }
        } catch (Exception e) {
            return null;
        }
        if (options.isEmpty()) {
            return null;
        }
        String group = ((JsonObject) options.get(0)).getString("name");
        group = group.substring(0, group.lastIndexOf('.'));
        JsonObject result = new JsonObject();
        result.put("kind", "main-options");
        result.put("group", group);
        result.put("note", name + " is not a component: these are the " + group + ".* keys for application.properties"
                           + " (camel run reads them at startup)");
        result.put("count", options.size());
        result.put("options", options);
        return result;
    }

    /**
     * Finds the catalog artifacts matching a term that need not be a name: a protocol (mqtt, amqp), a product (s3,
     * snowflake), an EIP alias (fan-out, dedup) or a word of the title. Best match first, with the title and
     * description so the caller can pick.
     *
     * @param catalog the catalog
     * @param term    what to look for
     * @param kind    component, dataformat, language, eip or bean; null for all
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
        if (kind == null || "eip".equals(kind)) {
            for (String name : catalog.suggestEipNames(term, max)) {
                EipModel m = catalog.eipModel(name);
                if (m != null) {
                    JsonObject o = summary("eip", m.getName(), m.getTitle(), m.getDescription(), m.getLabel());
                    if (m.getAliases() != null && !m.getAliases().isEmpty()) {
                        o.put("aliases", new JsonArray(m.getAliases()));
                    }
                    matches.add(o);
                }
            }
        }
        if (kind == null || "bean".equals(kind)) {
            // the built-in beans: an interface name (AggregationStrategy) lists its implementations
            int n = 0;
            for (PojoBeanModel bean : findBeans(catalog, term)) {
                if (n++ >= max) {
                    break;
                }
                JsonObject o = summary("bean", bean.getName(), bean.getTitle(), bean.getDescription(), null);
                o.put("javaType", bean.getJavaType());
                if (bean.getInterfaceType() != null) {
                    o.put("interfaceType", bean.getInterfaceType());
                }
                matches.add(o);
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

    /**
     * The built-in beans whose name, type, interface, title or description contains the term, exact interface and name
     * matches first.
     */
    static List<PojoBeanModel> findBeans(CamelCatalog catalog, String term) {
        String t = term.toLowerCase(Locale.ROOT).trim();
        List<PojoBeanModel> exact = new ArrayList<>();
        List<PojoBeanModel> partial = new ArrayList<>();
        for (String name : catalog.findBeansNames()) {
            PojoBeanModel bean = catalog.pojoBeanModel(name);
            if (bean == null) {
                continue;
            }
            String iface = bean.getInterfaceType() != null ? bean.getInterfaceType() : "";
            String ifaceSimple = iface.substring(iface.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (name.equalsIgnoreCase(t) || ifaceSimple.equals(t) || iface.equalsIgnoreCase(t)
                    || String.valueOf(bean.getJavaType()).equalsIgnoreCase(t)) {
                exact.add(bean);
            } else if ((name + " " + bean.getJavaType() + " " + iface + " " + bean.getTitle() + " "
                        + bean.getDescription())
                    .toLowerCase(Locale.ROOT).contains(t)) {
                partial.add(bean);
            }
        }
        exact.addAll(partial);
        return exact;
    }

    /** The built-in beans of an interface (by simple or full name), as "Name (javaType)" strings. */
    public static List<String> beansOfInterface(CamelCatalog catalog, String interfaceName) {
        List<String> answer = new ArrayList<>();
        if (catalog == null || interfaceName == null) {
            return answer;
        }
        String simple = interfaceName.substring(interfaceName.lastIndexOf('.') + 1);
        try {
            for (PojoBeanModel bean : findBeans(catalog, simple)) {
                String iface = bean.getInterfaceType() != null ? bean.getInterfaceType() : "";
                if (iface.equals(interfaceName) || iface.endsWith("." + simple)) {
                    answer.add(bean.getName() + " (" + bean.getJavaType() + ")");
                }
            }
        } catch (Exception e) {
            // an older catalog without bean metadata
        }
        return answer;
    }

    /** The languages whose script variables are documented, by their catalog name (js is javascript, java is joor). */
    private static final List<String> SCRIPT_LANGUAGES
            = List.of("groovy", "js", "python", "python3", "quickjs", "java", "template");

    /** The template components that bind the same variable map, answered by the template card. */
    private static final List<String> TEMPLATE_COMPONENTS
            = List.of("velocity", "freemarker", "mvel", "mustache", "chunk", "stringtemplate", "thymeleaf", "jslt");

    /**
     * The compact API reference of a core Camel class (Exchange, Message, CamelContext, Registry, ProducerTemplate,
     * Processor, AggregationStrategy, Predicate, Expression, TypeConverter) from the catalog, or the variables a script
     * language binds; null when the name is neither. An older catalog that has no API reference answers from the CLI's
     * own, the API is the same.
     *
     * @param catalog  the catalog
     * @param name     a simple or qualified class name, or a script language (groovy, javascript, python, java)
     * @param explicit whether kind=api was asked for, which makes a miss an error with the names that exist
     */
    static JsonObject apiDoc(CamelCatalog catalog, String name, boolean explicit) {
        String n = name.trim();
        String simple = n.substring(n.lastIndexOf('.') + 1);
        CamelCatalog source = catalog.findApiReferenceNames().isEmpty() ? ownCatalog() : catalog;
        List<String> names = source.findApiReferenceNames();
        String match = names.stream().filter(c -> c.equalsIgnoreCase(simple)).findFirst().orElse(null);
        if (match != null) {
            ApiReferenceModel model = source.apiReferenceModel(match);
            if (model != null && (n.equals(simple) || model.getJavaType().equalsIgnoreCase(n))) {
                return apiReferenceDoc(catalog, model, names);
            }
        }
        String lang = n.toLowerCase(Locale.ROOT);
        if ("joor".equals(lang)) {
            lang = "java";
        } else if ("javascript".equals(lang)) {
            lang = "js";
        } else if (TEMPLATE_COMPONENTS.contains(lang)) {
            // the mvel component is a template; the mvel language is asked for without kind and answers as a language
            lang = "template";
        }
        JsonObject script = scriptVariables(lang);
        if (script != null) {
            script.put("apis", new JsonArray(apiNames(names)));
            return script;
        }
        if (explicit) {
            JsonObject err = error("No API reference for '" + name + "'");
            err.put("apis", new JsonArray(apiNames(names)));
            return err;
        }
        return null;
    }

    /** The names an api lookup answers: the class cards and the script languages. */
    private static List<String> apiNames(List<String> classNames) {
        List<String> answer = new ArrayList<>(classNames);
        answer.addAll(SCRIPT_LANGUAGES);
        return answer;
    }

    /** The catalog of the CLI's own Camel version, created on first use (the holder idiom, no locking needed). */
    private static final class OwnCatalog {
        static final CamelCatalog CATALOG = new DefaultCamelCatalog();
    }

    /** The catalog of the CLI's own Camel version, for the API reference an older catalog does not carry. */
    private static CamelCatalog ownCatalog() {
        return OwnCatalog.CATALOG;
    }

    /**
     * The card of a core class: the class description with its common mistakes, then the methods, the important ones
     * first, each with the signatures of its overloads, a one-line description and examples. For an interface the
     * built-in implementations of the catalog come along, so AggregationStrategy also says which strategies exist.
     */
    private static JsonObject apiReferenceDoc(CamelCatalog catalog, ApiReferenceModel model, List<String> names) {
        JsonObject result = new JsonObject();
        result.put("kind", "api");
        result.put("name", model.getName());
        result.put("javaType", model.getJavaType());
        if (model.getDescription() != null) {
            result.put("description", model.getDescription());
        }
        JsonArray methods = new JsonArray();
        List<ApiReferenceModel.ApiMethodOptionModel> sorted = new ArrayList<>(model.getOptions());
        sorted.sort(Comparator.comparing((ApiReferenceModel.ApiMethodOptionModel m) -> !m.isImportant())
                .thenComparing(ApiReferenceModel.ApiMethodOptionModel::getName));
        for (ApiReferenceModel.ApiMethodOptionModel m : sorted) {
            JsonObject o = new JsonObject();
            o.put("name", m.getName());
            o.put("signatures", new JsonArray(m.getSignatures()));
            if (m.getDescription() != null) {
                o.put("description", m.getDescription());
            }
            if (!m.getExamples().isEmpty()) {
                o.put("examples", new JsonArray(m.getExamples()));
            }
            if (m.isDeprecated()) {
                o.put("deprecated", true);
            }
            methods.add(o);
        }
        result.put("methods", methods);
        List<String> implementations = beansOfInterface(catalog, model.getJavaType());
        if (!implementations.isEmpty()) {
            result.put("implementations", new JsonArray(implementations));
            result.put("implementationsHint", "built-in beans to declare and use instead of writing one; camel_catalog_doc"
                                              + " kind=bean gives the options of each");
        }
        result.put("apis", new JsonArray(apiNames(names)));
        return result;
    }

    /**
     * The variables a script language binds, hand-written because each language binds its own set with its own names
     * (the CamelContext is camelContext in groovy and context in javascript, the exchange properties exchangeProperties
     * and properties), and how a script reaches the Camel API and a registry bean from them; null for a language that
     * is not a script.
     */
    static JsonObject scriptVariables(String language) {
        JsonObject variables = new JsonObject();
        String note;
        switch (language) {
            case "groovy" -> {
                variables.put("exchange", "the Exchange");
                variables.put("message",
                        "the message (exchange.getMessage()); request is an alias, in is an alias (older names)");
                variables.put("body", "the message body");
                variables.put("headers", "the message headers (Map); header is an alias");
                variables.put("variables", "the exchange variables (Map); variable is an alias");
                variables.put("exchangeProperties", "the exchange properties (Map); exchangeProperty is an alias");
                variables.put("exception", "the exception when the exchange failed, else null");
                variables.put("camelContext", "the CamelContext");
                variables.put("attachments", "the message attachments (Map)");
                variables.put("log", "an SLF4J logger");
                variables.put("response", "the out message, only when one exists; out is an alias");
                note = "The value of the last statement is the result. A registry bean is not a variable: use"
                       + " camelContext.registry.lookupByName('myBean'). Setting body or headers in the script"
                       + " does not change the message: use message.body = ... or message.setHeader(name, value)."
                       + " Groovy property syntax works on the Camel API: message.body, exchange.context.registry.";
            }
            case "js", "python" -> {
                variables.put("exchange", "the Exchange");
                variables.put("context", "the CamelContext (not camelContext)");
                variables.put("exchangeId", "the exchange id");
                variables.put("message", "the message (exchange.getMessage())");
                variables.put("headers", "the message headers (Map)");
                variables.put("properties", "the exchange properties (Map)");
                variables.put("body", "the message body");
                note = "The value of the last expression is the result, converted to the expected type. A registry"
                       + " bean is context.getRegistry().lookupByName('myBean'). To change the message call"
                       + " message.setBody(...) or message.setHeader(name, value), not body = ...";
            }
            case "python3" -> {
                variables.put("exchangeId", "the exchange id");
                variables.put("headers", "the message headers (dict)");
                variables.put("properties", "the exchange properties (dict)");
                variables.put("body", "the message body");
                variables.put("exchange", "the Exchange, only when the language was created with host access");
                variables.put("message", "the message, only with host access");
                variables.put("context", "the CamelContext, only with host access");
                note = "The value of the last expression is the result. Without host access there are no Java"
                       + " objects: work with body, headers and properties as values.";
            }
            case "quickjs" -> {
                variables.put("body", "the message body as a JSON value");
                variables.put("headers", "the message headers as a JSON object");
                variables.put("properties", "the exchange properties as a JSON object");
                variables.put("exchangeId", "the exchange id");
                variables.put("variables", "the exchange variables as a JSON object");
                variables.put("exception", "{type, message} when the exchange failed, else null");
                note = "A sandboxed JavaScript: the values are JSON copies, there is no exchange, message or"
                       + " context object and no Java API; the result of the script is the value.";
            }
            case "java" -> {
                variables.put("context", "the CamelContext");
                variables.put("exchange", "the Exchange");
                variables.put("message", "the message (exchange.getMessage())");
                variables.put("body", "the message body (Object)");
                variables.put("optionalBody", "the body as Optional");
                note = "The java (joor) language compiles the script as the body of a method with these parameters;"
                       + " bodyAs(String.class) converts the body, #bean:myBean is replaced by the registry bean, and a"
                       + " script without return returns its last expression.";
            }
            case "template" -> {
                variables.put("body", "the message body");
                variables.put("headers", "the message headers (Map); header is an alias");
                variables.put("variables", "the exchange variables (Map); variable is an alias");
                variables.put("exception", "the exception when the exchange failed, else null");
                variables.put("exchange", "the Exchange, only with allowContextMapAll=true");
                variables.put("request", "the message, only with allowContextMapAll=true; in is an alias");
                variables.put("exchangeProperties", "the exchange properties (Map), only with allowContextMapAll=true;"
                                                    + " exchangeProperty is an alias");
                variables.put("camelContext", "the CamelContext, only with allowContextMapAll=true");
                variables.put("response", "the out message, only with allowContextMapAll=true and when one exists;"
                                          + " out is an alias");
                note = "The variables of the template components (" + String.join(", ", TEMPLATE_COMPONENTS)
                       + "): by default only"
                       + " body, headers, variables and exception; allowContextMapAll=true on the endpoint adds the"
                       + " exchange, the message, the properties and the CamelContext.";
            }
            default -> {
                return null;
            }
        }
        JsonObject result = new JsonObject();
        result.put("kind", "api");
        result.put("name", language);
        result.put("title", "template".equals(language)
                ? "Template variables" : ("js".equals(language) ? "javascript" : language) + " script variables");
        result.put("variables", variables);
        result.put("note", note);
        return result;
    }

    /** The documentation of a built-in bean: type, interface, options, and how it is declared and used in YAML. */
    static JsonObject beanDoc(CamelCatalog catalog, PojoBeanModel bm, String name) {
        JsonObject result = new JsonObject();
        result.put("kind", "bean");
        result.put("name", bm.getName());
        result.put("title", bm.getTitle());
        result.put("description", bm.getDescription());
        result.put("javaType", bm.getJavaType());
        String iface = bm.getInterfaceType();
        if (iface != null) {
            result.put("interfaceType", iface);
        }
        if (bm.getArtifactId() != null) {
            result.put("artifactId", bm.getArtifactId());
        }
        JsonArray options = new JsonArray();
        for (var opt : bm.getOptions()) {
            JsonObject o = new JsonObject();
            o.put("name", opt.getName());
            o.put("type", opt.getType());
            if (opt.getDescription() != null) {
                o.put("description", opt.getDescription());
            }
            if (opt.getDefaultValue() != null) {
                o.put("defaultValue", String.valueOf(opt.getDefaultValue()));
            }
            options.add(o);
        }
        result.put("options", options);
        String beanName = Character.toLowerCase(bm.getName().charAt(0)) + bm.getName().substring(1);
        String declare = "- beans:\n    - name: " + beanName + "\n      type: \"#class:" + bm.getJavaType() + "\""
                         + (options.isEmpty()
                                 ? "" : "\n      properties:\n        " + ((JsonObject) options.get(0)).getString("name")
                                        + ": ...");
        String use;
        String ifaceSimple = iface != null ? iface.substring(iface.lastIndexOf('.') + 1) : "";
        switch (ifaceSimple) {
            case "AggregationStrategy" -> use = "aggregate: {aggregationStrategy: " + beanName
                                                + ", ...} (also split, multicast, recipientList, enrich, pollEnrich)";
            case "AggregationRepository" -> use = "aggregate: {aggregationRepository: " + beanName + ", ...}";
            case "IdempotentRepository" -> use = "idempotentConsumer: {idempotentRepository: " + beanName + ", ...}";
            case "Processor" -> use = "- process: {ref: " + beanName + "}";
            case "LoadBalancer" -> use = "loadBalance: {customLoadBalancer: {ref: " + beanName + "}}";
            case "ExceptionPolicyStrategy", "RedeliveryPolicy" -> use = "errorHandler / onException options";
            default -> use = "the option that takes a " + (iface != null ? iface : "bean") + ", by the bean name " + beanName;
        }
        result.put("declare", declare);
        result.put("use", use);
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

    /**
     * Where the component goes in a route, which a description such as "Read and write files" does not say: a consumer
     * is a from:, a producer is a to:, and reading once in the middle of a route is the poll EIP.
     */
    static String usage(ComponentModel model) {
        if (model.isConsumerOnly()) {
            return "consumer only: use it as from: (a route starts from it); it cannot be used as a to:";
        }
        if (model.isProducerOnly()) {
            return "producer only: use it as a to: (send to it); it cannot start a route";
        }
        return "from: consumes (reads or receives, starts a route); to: produces (writes or sends); to consume one message"
               + " in the middle of a route use the poll EIP (poll: {uri: ...}), or pollEnrich";
    }

    private static JsonObject componentDoc(
            ComponentModel model, String filter, OptionScope scope, boolean includeHeaders, String doc) {
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
        result.put("usage", usage(model));
        result.put("remote", model.isRemote());
        result.put("groupId", model.getGroupId());
        result.put("artifactId", model.getArtifactId());
        result.put("version", model.getVersion());
        addCommonModelFields(result, model);

        if (scope != OptionScope.NONE) {
            JsonArray options = new JsonArray();
            int omitted = 0;
            if (model.getComponentOptions() != null) {
                for (BaseOptionModel opt : model.getComponentOptions()) {
                    if (matchesOptionFilter(opt, filter)) {
                        if (scope.accepts(opt, filter)) {
                            options.add(optionToJson(opt, "component"));
                        } else {
                            omitted++;
                        }
                    }
                }
            }
            if (model.getEndpointOptions() != null) {
                for (BaseOptionModel opt : model.getEndpointOptions()) {
                    if (matchesOptionFilter(opt, filter)) {
                        if (scope.accepts(opt, filter)) {
                            options.add(optionToJson(opt, "endpoint"));
                        } else {
                            omitted++;
                        }
                    }
                }
            }
            putOptions(result, options, omitted, scope);
        }
        if (includeHeaders && model.getEndpointHeaders() != null) {
            // the CamelXxx headers the component reads and sets, with the constant to use from Java
            JsonArray headers = new JsonArray();
            for (ComponentModel.EndpointHeaderModel h : model.getEndpointHeaders()) {
                JsonObject jo = new JsonObject();
                jo.put("name", h.getName());
                if (h.getConstantName() != null) {
                    jo.put("constantName", h.getConstantName());
                }
                if (h.getJavaType() != null) {
                    jo.put("javaType", h.getJavaType());
                }
                if (h.getGroup() != null) {
                    jo.put("group", h.getGroup());
                }
                if (h.isRequired()) {
                    jo.put("required", true);
                }
                if (h.getDescription() != null) {
                    jo.put("description", h.getDescription());
                }
                headers.add(jo);
            }
            result.put("headers", headers);
        }
        if (doc != null) {
            result.put("doc", doc);
        }
        return result;
    }

    private static JsonObject dataFormatDoc(DataFormatModel model, String filter, OptionScope scope, String doc) {
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
        if (scope != OptionScope.NONE) {
            putOptions(result, model.getOptions(), filter, scope);
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
            LanguageModel model, String filter, OptionScope scope, String doc, List<String> docPages,
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
        if (scope != OptionScope.NONE && !docPageOnly) {
            putOptions(result, model.getOptions(), filter, scope);
        }
        if (!docPageOnly) {
            addLanguageFunctions(result, model, filter);
        }
        if ("simple".equals(model.getName()) || "csimple".equals(model.getName())) {
            result.put("syntax", SIMPLE_SYNTAX);
        }
        JsonObject script = scriptVariables(model.getName());
        if (script != null) {
            // the variables the script sees, and how to reach the Camel API from them
            result.put("scriptVariables", script.get("variables"));
            result.put("scriptNote", script.get("note"));
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
                // a function name comes first, even when a group has the same name; a group name is then that group
                // and nothing else (date is two functions, not every function whose description mentions a date);
                // the rest is a word match
                boolean group = functions.stream().anyMatch(fn -> filter.equalsIgnoreCase(fn.getGroup()));
                JsonArray exact = new JsonArray();
                JsonArray arr = new JsonArray();
                for (LanguageModel.LanguageFunctionModel fn : functions) {
                    if (isFunctionName(fn, filter)) {
                        exact.add(functionToJson(fn));
                    } else if (group) {
                        if (filter.equalsIgnoreCase(fn.getGroup())) {
                            arr.add(functionToJson(fn));
                        }
                    } else if (matchesOptionFilter(fn, filter)
                            || (fn.getDisplayName() != null && fn.getDisplayName().toLowerCase().contains(filter))) {
                        arr.add(functionToJson(fn));
                    }
                }
                exact.addAll(arr);
                result.put("functions", exact);
                result.put("matchedFunctions", exact.size());
                if (group) {
                    result.put("functionGroup", filter);
                }
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
                // an operator kind (binary, logical, unary...) is that kind only; an operator itself comes first
                boolean kind = operators.stream().anyMatch(op -> filter.equalsIgnoreCase(op.getOperatorKind()));
                JsonArray exact = new JsonArray();
                JsonArray arr = new JsonArray();
                for (LanguageModel.LanguageOperatorModel op : operators) {
                    if (kind) {
                        if (filter.equalsIgnoreCase(op.getOperatorKind())) {
                            arr.add(operatorToJson(op));
                        }
                    } else if (filter.equalsIgnoreCase(op.getName())) {
                        exact.add(operatorToJson(op));
                    } else if (matchesOptionFilter(op, filter)
                            || (op.getOperatorKind() != null && op.getOperatorKind().toLowerCase().contains(filter))) {
                        arr.add(operatorToJson(op));
                    }
                }
                exact.addAll(arr);
                result.put("operators", exact);
                result.put("matchedOperators", exact.size());
                if (kind) {
                    result.put("operatorKind", filter);
                }
            } else {
                JsonArray syntaxes = new JsonArray();
                for (LanguageModel.LanguageOperatorModel op : operators) {
                    syntaxes.add(op.getOperatorSyntax() != null ? op.getOperatorSyntax() : op.getName());
                }
                result.put("operatorSyntax", syntaxes);
            }
        }
    }

    /**
     * Whether the filter is the function's name: {@code random} or {@code random(min,max)} for {@code random(min,max)}.
     */
    private static boolean isFunctionName(LanguageModel.LanguageFunctionModel fn, String filter) {
        String name = fn.getName();
        if (name == null) {
            return false;
        }
        int paren = name.indexOf('(');
        String bare = paren > 0 ? name.substring(0, paren) : name;
        return filter.equalsIgnoreCase(name) || filter.equalsIgnoreCase(bare);
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

    private static JsonObject eipDoc(EipModel model, String filter, OptionScope scope, String doc) {
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
        if (scope != OptionScope.NONE) {
            putOptions(result, model.getOptions(), filter, scope);
        }
        if (doc != null) {
            result.put("doc", doc);
        }
        return result;
    }

    /**
     * Which options a doc lists: the common ones by default, so the answer for a component such as kafka stays readable
     * for a small model; the deprecated and advanced ones on request. A filter searches all options, as it names what
     * it wants.
     */
    enum OptionScope {
        NONE,
        COMMON,
        REQUIRED,
        ALL;

        /** The scope a caller named: common (also true, empty or null), required, all or false; null when unknown. */
        static OptionScope parse(String value) {
            if (value == null || value.isBlank()) {
                return COMMON;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "common", "true" -> COMMON;
                case "required" -> REQUIRED;
                case "all" -> ALL;
                case "false", "none" -> NONE;
                default -> null;
            };
        }

        boolean accepts(BaseOptionModel opt, String filter) {
            return switch (this) {
                case NONE -> false;
                case ALL -> true;
                case REQUIRED -> opt.isRequired();
                case COMMON -> filter != null || !(opt.isDeprecated() || isAdvanced(opt));
            };
        }

        private static boolean isAdvanced(BaseOptionModel opt) {
            return opt.getLabel() != null && opt.getLabel().contains("advanced");
        }
    }

    private static void putOptions(
            JsonObject result, List<? extends BaseOptionModel> options, String filter,
            OptionScope scope) {
        JsonArray arr = new JsonArray();
        int omitted = 0;
        if (options != null) {
            for (BaseOptionModel opt : options) {
                if (matchesOptionFilter(opt, filter)) {
                    if (scope.accepts(opt, filter)) {
                        arr.add(optionToJson(opt, null));
                    } else {
                        omitted++;
                    }
                }
            }
        }
        putOptions(result, arr, omitted, scope);
    }

    /** The options with their count, and what the scope left out so the caller knows to ask for more. */
    private static void putOptions(JsonObject result, JsonArray options, int omitted, OptionScope scope) {
        result.put("options", options);
        result.put("matchedOptions", options.size());
        if (omitted > 0) {
            result.put("omittedOptions", omitted);
            result.put("optionsHint", scope == OptionScope.REQUIRED
                    ? "the required options only; includeOptions=common or all lists the others"
                    : "deprecated and advanced options left out; includeOptions=all lists them, optionsFilter finds one");
        }
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
