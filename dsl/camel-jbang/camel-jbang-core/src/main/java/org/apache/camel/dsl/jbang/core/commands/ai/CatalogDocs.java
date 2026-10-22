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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.EndpointValidationResult;
import org.apache.camel.catalog.impl.EditDistanceSuggestionStrategy;
import org.apache.camel.tooling.model.ApiReferenceModel;
import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.model.PojoBeanModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The catalog documentation an AI agent authoring an integration asks for: a component, data format, language or EIP
 * with its options, the rules of an endpoint URI spelled out, the simple language's syntax, functions and operators, an
 * endpoint URI checked against the catalog, and the Java API a route author's code touches (Exchange, Message,
 * CamelContext, ...) with the variables a script language binds. Written for what a small model gets wrong most (a path
 * option as a query parameter, an operator inside a placeholder, a bean name used as a Groovy variable), and shared by
 * the {@code camel_catalog_doc} tool of every Camel MCP server.
 */
public final class CatalogDocs {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogDocs.class);

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

    /** The docPage argument of camel_catalog_doc; the MCP server describes it the same way. */
    public static final String DOC_PAGE_DESC
            = "Returned as text: a section title from sections (e.g. SSL support), a sub-page from docPages (e.g. main),"
              + " or for simple: functions, operators, ognl, advanced";
    /** The optionsFilter argument of camel_catalog_doc. */
    public static final String OPTIONS_FILTER_DESC
            = "Word to match in option names or descriptions, all matches listed in full; for an EIP whose own options"
              + " do not match, its elements' options are searched (nestedOptions)";
    /** The limit argument of camel_catalog_doc. */
    public static final String OPTIONS_LIMIT_DESC
            = "Options of a component listed in full (default 20, the rest by name; all with optionsFilter)";

    /** The names the YAML DSL and its users give a data format, and the catalog artifact each is (CAMEL-24898). */
    static final Map<String, String> YAML_DATAFORMAT_NAMES = Map.ofEntries(
            Map.entry("json", "jackson"), Map.entry("json-jackson", "jackson"), Map.entry("jsonjackson", "jackson"),
            Map.entry("json-gson", "gson"), Map.entry("json-fastjson", "fastjson"), Map.entry("json-jsonb", "jsonb"),
            Map.entry("yaml", "snakeYaml"), Map.entry("snakeyaml", "snakeYaml"), Map.entry("yaml-snakeyaml", "snakeYaml"),
            Map.entry("avro-jackson", "avroJackson"), Map.entry("avrojackson", "avroJackson"),
            Map.entry("protobuf-jackson", "protobufJackson"), Map.entry("protobufjackson", "protobufJackson"),
            Map.entry("xml", "jacksonXml"), Map.entry("jackson-xml", "jacksonXml"), Map.entry("jacksonxml", "jacksonXml"));

    /** The YAML DSL shape of the data formats whose key is not the artifact name. */
    static final Map<String, String> YAML_DATAFORMAT_SHAPES = Map.ofEntries(
            Map.entry("jackson", "json: {library: Jackson}"), Map.entry("gson", "json: {library: Gson}"),
            Map.entry("fastjson", "json: {library: Fastjson}"), Map.entry("jsonb", "json: {library: Jsonb}"),
            Map.entry("snakeYaml", "yaml: {library: SnakeYAML}"),
            Map.entry("avroJackson", "avro: {library: Jackson}"), Map.entry("avro", "avro: {library: ApacheAvro}"),
            Map.entry("protobufJackson", "protobuf: {library: Jackson}"),
            Map.entry("protobuf", "protobuf: {library: GoogleProtobuf}"));

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

    /** How much of a component's documentation prose is carried in the answer by default (CAMEL-25040). */
    static final int DOC_EXCERPT_BUDGET = 1400;
    /**
     * The same for a language or a data format (CAMEL-25235). More than a component's, because a component says most of
     * what it does through its options and these say it in prose: the semantic language's named questions, their type,
     * instructions, criteria and threshold are not options at all.
     */
    static final int DOC_EXCERPT_BUDGET_PROSE = 3600;

    /** The java type of an expression option of an EIP other than its main expression: handled, completionPredicate. */
    private static final String EXPRESSION_SUB_ELEMENT = "org.apache.camel.model.ExpressionSubElementDefinition";

    /**
     * The java type of a list of key/value properties. Today only the namespace option of xpath, xquery and xtokenize
     * has it; any other option of that type gets the generic key/value form.
     */
    private static final String PROPERTY_LIST = "java.util.List<org.apache.camel.model.PropertyDefinition>";

    private static final Pattern XREF = Pattern.compile("xref:[^\\[]*\\[([^]]*)]");
    private static final Pattern INTERNAL_REF = Pattern.compile("<<[^,>]*,([^>]*)>>");

    /**
     * The prose of a component's documentation page, trimmed to a budget, or null when the page has none.
     * <p/>
     * The option list says what can be set, and nothing else: syntax that is not an option is invisible in it. The
     * {@code sql} component is the clearest case -- that a named parameter is written {@code :#name} appears nowhere in
     * its options, only in its page, which an author who does not already know the answer has no reason to ask for
     * (CAMEL-25040). So the page's own prose comes along, from its first section, which is where a component page
     * explains its URI and the essentials.
     * <p/>
     * Left out: the title and attribute header, the Maven dependency stanza, and the {@code include::} directives that
     * pull in the generated option tables, which the answer already carries as options. A cross-reference is reduced to
     * the words it links, so the excerpt reads as text rather than as AsciiDoc.
     */
    static String docExcerpt(String asciiDoc, int budget) {
        if (asciiDoc == null || asciiDoc.isBlank()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean started = false;
        boolean fenced = false;
        for (String line : asciiDoc.split("\n", -1)) {
            String trimmed = line.strip();
            if (!started) {
                // the page's own header, its intro and the dependency stanza come before the first section, and the
                // description and Maven coordinates of the answer already say what they say
                started = trimmed.startsWith("== ");
                if (!started) {
                    continue;
                }
            }
            if (trimmed.startsWith("include::") || trimmed.startsWith("ifdef::") || trimmed.startsWith("endif::")
                    || trimmed.startsWith("ifndef::")) {
                continue;
            }
            if (!fenced && trimmed.startsWith("//")) {
                // an asciidoc line comment, such as the "// language options: START" markers around a generated table
                continue;
            }
            if (trimmed.startsWith("----")) {
                fenced = !fenced;
            } else if (!fenced) {
                // asciidoc decoration that carries nothing on its own
                if (trimmed.equals("====") || trimmed.equals("|===") || trimmed.startsWith("[tabs]")
                        || trimmed.startsWith("[width=") || trimmed.startsWith("[cols=")
                        || trimmed.startsWith("[source,") || trimmed.startsWith("[NOTE]")
                        || trimmed.startsWith("[TIP]") || trimmed.startsWith("[IMPORTANT]")
                        || trimmed.startsWith("[WARNING]") || trimmed.startsWith("[CAUTION]")
                        || (trimmed.startsWith(":") && trimmed.indexOf(':', 1) > 0)) {
                    continue;
                }
            }
            String text = XREF.matcher(line).replaceAll("$1");
            text = INTERNAL_REF.matcher(text).replaceAll("$1");
            if (sb.length() + text.length() + 1 > budget) {
                break;
            }
            sb.append(text).append('\n');
        }
        String answer = sb.toString().strip();
        return answer.isEmpty() ? null : answer;
    }

    public static JsonObject catalogDoc(
            CamelCatalog catalog, String name, String endpoint, String kind, String optionsFilter,
            String includeOptions, boolean includeHeaders, boolean includeDoc, String docPage) {
        return catalogDoc(catalog, name, endpoint, kind, optionsFilter, includeOptions, includeHeaders, includeDoc,
                docPage, -1);
    }

    /**
     * The same, with how many options of a component are listed in full: a negative limit is the default, which is
     * {@link #DEFAULT_OPTIONS_LIMIT} without a filter and all the matches with one. The answer is cut at
     * {@link #MAX_ANSWER_LENGTH} characters, with a marker that says so (CAMEL-25541).
     */
    public static JsonObject catalogDoc(
            CamelCatalog catalog, String name, String endpoint, String kind, String optionsFilter,
            String includeOptions, boolean includeHeaders, boolean includeDoc, String docPage, int limit) {
        return capAnswer(answer(catalog, name, endpoint, kind, optionsFilter, includeOptions, includeHeaders, includeDoc,
                docPage, limit));
    }

    private static JsonObject answer(
            CamelCatalog catalog, String name, String endpoint, String kind, String optionsFilter,
            String includeOptions, boolean includeHeaders, boolean includeDoc, String docPage, int limit) {
        if (endpoint != null && !endpoint.isBlank()) {
            return validateEndpoint(catalog, endpoint.trim());
        }
        if (name == null || name.isBlank()) {
            return error("'name' or 'endpoint' parameter is required");
        }
        name = name.trim();
        String suffixKind = kindOfSuffix(name);
        if (suffixKind != null && (kind == null || kind.equals(suffixKind))) {
            // the page names camel_catalog_docs hands out (wireTap-eip, jackson-dataformat) name the artifact too
            name = name.substring(0, name.length() - suffixKind.length() - 1);
            kind = suffixKind;
        }
        String page = docPage != null ? docPage.trim().toLowerCase(Locale.ROOT) : null;
        if ("kamelet".equals(kind) && !KameletDefinitions.catalog().containsKey(name)
                && ("kamelet".equals(name) || page != null)) {
            // the kamelet component's own docs, such as how to write a Kamelet (docPage=custom): a model asks for
            // them with kind=kamelet, which is the kind of the catalog's Kamelets
            kind = null;
            name = "kamelet";
        }
        String lowerFilter = optionsFilter != null && !optionsFilter.isBlank() ? optionsFilter.toLowerCase() : null;
        OptionScope scope = OptionScope.parse(includeOptions);
        if (scope == null) {
            return error("includeOptions must be common, required, all, true or false, got: " + includeOptions);
        }

        if (kind == null || "component".equals(kind)) {
            ComponentModel cm = catalog.componentModel(name);
            if (cm == null) {
                String exact = ignoringCase(catalog.findComponentNames(), name);
                cm = exact != null ? catalog.componentModel(exact) : null;
            }
            if (cm != null) {
                // smtp, imap and pop3 are documented on the mail page, named after the artifact
                String docName = componentDocName(catalog, cm);
                String adoc = docName != null ? catalog.asciiDoc(docName + "-component") : null;
                List<JsonObject> pages = componentDocPages(docName, adoc);
                List<DocSection> sections = docSections(adoc);
                ComponentAnswer how = new ComponentAnswer(catalog, lowerFilter, limit, sections);
                if (page != null && !page.isEmpty()) {
                    // a sub-page the component page links to, such as how to write a custom Kamelet
                    String sub = pages.stream().anyMatch(p -> page.equals(p.getString("page")))
                            ? catalog.asciiDoc(docName + "-" + page) : null;
                    if (sub != null) {
                        return componentDoc(cm, how, OptionScope.NONE, false, sub, null, pages);
                    }
                    // else a section of the page, by its title (CAMEL-25541)
                    DocSection section = findSection(sections, page);
                    if (section == null) {
                        JsonObject err = error("No doc page or section '" + page + "' for component " + name);
                        err.put("docPages", new JsonArray(pages));
                        err.put("sections", sectionTitles(sections, lowerFilter));
                        return err;
                    }
                    JsonObject answer = componentDoc(cm, how, OptionScope.NONE, false, section.text(), null, List.of());
                    answer.put("section", section.title());
                    answer.remove("sections");
                    answer.remove("sectionsHint");
                    return answer;
                }
                // the whole page when it was asked for, else its first section, which the options cannot say
                return componentDoc(cm, how, scope, includeHeaders, includeDoc ? adoc : null,
                        includeDoc ? null : docExcerpt(adoc, DOC_EXCERPT_BUDGET), pages);
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
            // the YAML DSL names a data format by its key and library (json + Jackson), the catalog by artifact
            // (jackson): both are answered, with the YAML shape in the answer (CAMEL-24898)
            String artifact = YAML_DATAFORMAT_NAMES.getOrDefault(name.toLowerCase(), name);
            DataFormatModel dm = catalog.dataFormatModel(artifact);
            String anyCase = dm == null ? ignoringCase(catalog.findDataFormatNames(), artifact) : null;
            if (anyCase != null) {
                artifact = anyCase;
                dm = catalog.dataFormatModel(artifact);
            }
            if (dm != null) {
                String adoc = catalog.asciiDoc(artifact + "-dataformat");
                JsonObject result = dataFormatDoc(dm, lowerFilter, scope, includeDoc ? adoc : null,
                        includeDoc ? null : docExcerpt(adoc, DOC_EXCERPT_BUDGET_PROSE));
                String shape = YAML_DATAFORMAT_SHAPES.get(artifact);
                if (shape != null) {
                    result.put("yaml", "marshal: {" + shape + "} or unmarshal: {" + shape + "}"
                                       + " (the YAML DSL key with its library, not the artifact name " + artifact + ")");
                }
                return result;
            }
            if (kind != null) {
                return notFound("Data format", name, catalog.suggestDataFormatNames(name, 5));
            }
        }
        if (kind == null || "language".equals(kind)) {
            LanguageModel lm = catalog.languageModel(name);
            String anyCase = lm == null ? ignoringCase(catalog.findLanguageNames(), name) : null;
            if (anyCase != null) {
                name = anyCase;
                lm = catalog.languageModel(name);
            }
            if (lm != null) {
                String doc = null;
                String sectionTitle = null;
                if (page != null && !page.isEmpty()) {
                    doc = catalog.asciiDoc(name + "-" + page);
                    if (doc == null) {
                        // a section of the page or of one of its sub-pages, by its title (CAMEL-25541)
                        List<DocSection> sections = new ArrayList<>(docSections(catalog.asciiDoc(name + "-language")));
                        for (String sub : languageDocPages(catalog, name)) {
                            sections.addAll(docSections(catalog.asciiDoc(name + "-" + sub)));
                        }
                        DocSection section = findSection(sections, page);
                        if (section == null) {
                            JsonObject err = error("No doc page or section '" + page + "' for language " + name);
                            err.put("docPages", new JsonArray(languageDocPages(catalog, name)));
                            err.put("sections", sectionTitles(sections, lowerFilter));
                            return err;
                        }
                        doc = section.text();
                        sectionTitle = section.title();
                    }
                } else if (includeDoc) {
                    doc = catalog.asciiDoc(name + "-language");
                }
                boolean docPageOnly = page != null && !page.isEmpty();
                // the start of the page unless the whole of it, or one of its sub-pages, was asked for
                String excerpt = docPageOnly || includeDoc
                        ? null : docExcerpt(catalog.asciiDoc(name + "-language"), DOC_EXCERPT_BUDGET_PROSE);
                JsonObject answer
                        = languageDoc(lm, lowerFilter, scope, doc, languageDocPages(catalog, name), docPageOnly, excerpt);
                if (sectionTitle != null) {
                    answer.put("section", sectionTitle);
                }
                return answer;
            }
            if (kind != null) {
                return notFound("Language", name, catalog.suggestLanguageNames(name, 5));
            }
        }
        if (kind == null || "eip".equals(kind)) {
            EipModel em = catalog.eipModel(name);
            String anyCase = em == null ? ignoringCase(catalog.findModelNames(), name) : null;
            if (anyCase != null) {
                em = catalog.eipModel(anyCase);
            }
            if (em != null) {
                return eipDoc(catalog, em, lowerFilter, scope, includeDoc, null);
            }
            if (BEANS_ENTRY.equalsIgnoreCase(name)) {
                JsonObject beans = beansDoc(catalog, lowerFilter, scope, includeDoc);
                if (beans != null) {
                    return beans;
                }
            }
            EipModel app = appModel(catalog, name);
            if (app != null) {
                return eipDoc(catalog, app, lowerFilter, scope, includeDoc, null);
            }
            if (kind != null) {
                // an alias (fan-out, dedup, rate-limit) or the whole title names the EIP as well
                JsonObject byTerm = eipByTerm(catalog, name, lowerFilter, scope, includeDoc);
                if (byTerm != null) {
                    return byTerm;
                }
                return withErrorHandling(notFound("EIP", name, catalog.suggestEipNames(name, 5)), name);
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
        if (kind == null || "kamelet".equals(kind)) {
            KameletDefinitions.Definition def = KameletDefinitions.catalog().get(name);
            if (def != null) {
                return kameletDoc(def);
            }
            if (kind != null) {
                JsonObject err = notFound("Kamelet", name,
                        KameletDefinitions.suggest(name, KameletDefinitions.catalog().keySet(), 5));
                // a Kamelet of the project is not in the catalog: how to write one
                err.put("guide", AuthoringTools.KAMELET_GUIDE);
                return err;
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
        suggestions.addAll(KameletDefinitions.suggest(name, KameletDefinitions.catalog().keySet(), 3));
        JsonObject answer = withErrorHandling(notFound("Artifact", name, suggestions), name);
        if (name.toLowerCase(Locale.ROOT).contains("kamelet")) {
            // kamelet-custom, custom-kamelet: how to write one
            answer.put("guide", AuthoringTools.KAMELET_GUIDE);
        }
        return answer;
    }

    /** The words a model asks with when it wants the redelivery of the error handler, which no EIP is named after. */
    private static final Set<String> ERROR_HANDLING_TERMS = Set.of(
            "retry", "retries", "retrying", "redeliver", "redelivery", "redeliveries", "backoff");

    /**
     * The error handler entries for a not-found term that asks for retrying (CAMEL-25541): retry used to resolve to
     * optimisticLockRetryPolicy, an option of the aggregate EIP, by a word of its title. The error handler entries come
     * first in the suggestions, with how redelivery is written.
     */
    private static JsonObject withErrorHandling(JsonObject notFound, String term) {
        if (!ERROR_HANDLING_TERMS.contains(term.trim().toLowerCase(Locale.ROOT))) {
            return notFound;
        }
        List<String> suggestions = new ArrayList<>(List.of("errorHandler", "onException", "redeliveryPolicy"));
        Object others = notFound.get("suggestions");
        if (others instanceof JsonArray arr) {
            for (Object o : arr) {
                if (!suggestions.contains(String.valueOf(o))) {
                    suggestions.add(String.valueOf(o));
                }
            }
        }
        notFound.put("suggestions", new JsonArray(suggestions));
        notFound.put("errorHandling", "retrying a failed step is redelivery, configured on the error handler: "
                                      + "errorHandler: {defaultErrorHandler: {redeliveryPolicy: {maximumRedeliveries: 3,"
                                      + " redeliveryDelay: 1000}}} for all routes, or onException with redeliveryPolicy"
                                      + " for one exception; camel_catalog_doc name=redeliveryPolicy lists the options");
        return notFound;
    }

    /** The kind a page name of camel_catalog_docs ends with (kafka-component, wireTap-eip), or null. */
    private static String kindOfSuffix(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String kind : List.of("component", "dataformat", "language", "eip")) {
            if (lower.endsWith("-" + kind) && lower.length() > kind.length() + 1) {
                return kind;
            }
        }
        return null;
    }

    /** The name of the list that equals the given one ignoring case (wiretap for wireTap), or null. */
    private static String ignoringCase(List<String> names, String name) {
        if (names == null) {
            return null;
        }
        for (String n : names) {
            if (n.equalsIgnoreCase(name)) {
                return n;
            }
        }
        return null;
    }

    /** The Kamelets of the catalog whose name has the term, then those whose description has it. */
    private static List<KameletDefinitions.Definition> kameletsMatching(String lowerTerm) {
        List<KameletDefinitions.Definition> byName = new ArrayList<>();
        List<KameletDefinitions.Definition> byDescription = new ArrayList<>();
        String dashed = lowerTerm.trim().replace(' ', '-');
        for (KameletDefinitions.Definition def : KameletDefinitions.catalog().values()) {
            if (def.name().contains(dashed)) {
                byName.add(def);
            } else if (def.description() != null && def.description().toLowerCase(Locale.ROOT).contains(lowerTerm)) {
                byDescription.add(def);
            }
        }
        byName.sort((a, b) -> Integer.compare(a.name().length(), b.name().length()));
        byName.addAll(byDescription);
        return byName;
    }

    /**
     * A Kamelet of the catalog: its properties, which go under parameters of the kamelet: endpoint, and how to write
     * it. A model writes the options of the component a Kamelet wraps instead (brokers on kafka-sink, which has
     * bootstrapServers), as it knows those from years of examples.
     */
    public static JsonObject kameletDoc(KameletDefinitions.Definition def) {
        JsonObject result = new JsonObject();
        result.put("kind", "kamelet");
        result.put("name", def.name());
        if (def.type() != null) {
            result.put("type", def.type());
        }
        if (def.description() != null) {
            result.put("description", def.description());
        }
        result.put("from", def.source());
        StringBuilder params = new StringBuilder();
        for (KameletDefinitions.Property p : def.properties()) {
            if (p.required() && p.defaultValue() == null) {
                params.append(params.isEmpty() ? "" : ", ").append(p.name()).append(": <").append(p.name()).append(">");
            }
        }
        String endpoint = "{uri: kamelet:" + def.name() + (params.isEmpty() ? "" : ", parameters: {" + params + "}") + "}";
        result.put("yaml", ("source".equals(def.type()) ? "from: " : "- to: ") + endpoint
                           + " (its properties go under parameters: they are the Kamelet's, not the options of the"
                           + " component it uses)");
        JsonArray props = new JsonArray();
        for (KameletDefinitions.Property p : def.properties()) {
            JsonObject o = new JsonObject();
            o.put("name", p.name());
            if (p.required() && p.defaultValue() == null) {
                o.put("required", true);
            }
            if (p.type() != null) {
                o.put("type", p.type());
            }
            if (p.defaultValue() != null) {
                o.put("defaultValue", p.defaultValue());
            }
            if (!p.enumValues().isEmpty()) {
                o.put("enum", new JsonArray(p.enumValues()));
            }
            if (p.description() != null) {
                o.put("description", p.description());
            }
            props.add(o);
        }
        result.put("properties", props);
        return result;
    }

    /**
     * The documentation of the EIP a term such as an alias (fan-out, dedup, rate-limit) or its whole title names, with
     * the term the caller used; null when no EIP matches. A single word of the title is not enough: retry named
     * optimisticLockRetryPolicy, an option of the aggregate EIP, that way (CAMEL-25541); such matches stay suggestions.
     */
    private static JsonObject eipByTerm(
            CamelCatalog catalog, String term, String filter, OptionScope scope, boolean includeDoc) {
        List<String> names = catalog.suggestEipNames(term, 1);
        if (names.isEmpty()) {
            return null;
        }
        EipModel em = catalog.eipModel(names.get(0));
        return em == null || !namesEip(em, term) ? null : eipDoc(catalog, em, filter, scope, includeDoc, term);
    }

    /** Whether the term is the name, an alias or the whole title of the EIP, ignoring case and punctuation. */
    private static boolean namesEip(EipModel em, String term) {
        String key = normalizeName(term);
        if (key.equals(normalizeName(em.getName())) || key.equals(normalizeName(em.getTitle()))) {
            return true;
        }
        return em.getAliases() != null && em.getAliases().stream().anyMatch(a -> key.equals(normalizeName(a)));
    }

    private static String normalizeName(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static JsonObject eipDoc(
            CamelCatalog catalog, EipModel model, String filter, OptionScope scope, boolean includeDoc,
            String matchedTerm) {
        String doc = includeDoc ? catalog.asciiDoc(model.getName() + "-eip") : null;
        JsonObject result = eipDoc(model, filter, scope, doc);
        if (CatalogSamples.hasEipSample(catalog, model.getName())) {
            // only when there is one, else an agent spends a call that answers "no sample" (CAMEL-25539)
            result.put("sampleHint", "camel_catalog_sample name=" + model.getName() + " returns a validated YAML example");
        }
        // only when the filter matched none of the EIP's own options, also none the scope left out
        if (filter != null && scope != OptionScope.NONE && result.getIntegerOrDefault("matchedOptions", 0) == 0
                && result.getIntegerOrDefault("omittedOptions", 0) == 0) {
            putNestedOptions(catalog, model, filter, scope, result);
        }
        if (matchedTerm != null) {
            result.put("matchedTerm", matchedTerm);
        }
        return result;
    }

    /**
     * The top-level entries of the YAML DSL whose model is an application model of the catalog (models-app), not an EIP
     * model: camel_catalog_doc and camel_catalog_find did not find sslContextParameters (CAMEL-25735).
     */
    static final List<String> APP_ENTRIES = List.of("sslContextParameters");

    /** The application model of the catalog for a top-level entry (any case), or null. */
    static EipModel appModel(CamelCatalog catalog, String name) {
        String entry = APP_ENTRIES.stream().filter(e -> e.equalsIgnoreCase(name)).findFirst().orElse(null);
        if (entry == null) {
            return null;
        }
        try (InputStream is = catalog.getVersionManager()
                .getResourceAsStream("org/apache/camel/catalog/models-app/" + entry + ".json")) {
            if (is == null) {
                return null;
            }
            EipModel model = JsonMapper.generateEipModel(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            // the description comes from the javadoc, with its XML example escaped
            if (model.getDescription() != null) {
                model.setDescription(model.getDescription().replace("&lt;", "<").replace("&gt;", ">")
                        .replace("&quot;", "\"").replace("&amp;", "&"));
            }
            return model;
        } catch (Exception e) {
            LOG.debug("Cannot read the application model {} from the catalog", entry, e);
            return null;
        }
    }

    /** The top-level entry of the YAML DSL that declares beans: a list of bean factories, with no model of its own. */
    static final String BEANS_ENTRY = "beans";

    private static final String BEANS_DESCRIPTION
            = "The top-level entry of the YAML DSL that declares beans: a list of beans, each with a name and how to create"
              + " it (type, properties, constructors, a factory method or a script)";

    /**
     * The beans entry of the YAML DSL (CAMEL-25719). Each item of its list is a bean factory, so the options are those
     * of the beanFactory model, and the answer says where the entry goes and how a route uses a bean, with an example.
     * A model that looked up beans before was told "EIP not found" four times in one step, while camel_catalog_sample
     * had the example all along. Null when the catalog has no beanFactory model.
     */
    private static JsonObject beansDoc(CamelCatalog catalog, String filter, OptionScope scope, boolean includeDoc) {
        EipModel factory = catalog.eipModel("beanFactory");
        if (factory == null) {
            return null;
        }
        JsonObject result = eipDoc(catalog, factory, filter, scope, includeDoc, null);
        result.put("name", BEANS_ENTRY);
        result.put("title", "Beans");
        result.put("description", BEANS_DESCRIPTION + ". The options are those of one bean; name is required. A route uses"
                                  + " a bean as #bean:name in an option, ${bean:name.method} in Simple, or with the bean EIP"
                                  + " (ref: name). The entry goes in the file of the routes, or in a file of its own such as"
                                  + " beans.yaml next to them.");
        result.put("placement", CatalogSamples.placement(BEANS_ENTRY));
        // the example itself, short as it is, rather than a hint to fetch it with another call
        JsonArray samples = (JsonArray) CatalogSamples.sample(catalog, BEANS_ENTRY, 1).get("samples");
        // the hint of eipDoc names beanFactory, which is not the name to ask for
        result.remove("sampleHint");
        if (samples != null && !samples.isEmpty()) {
            result.put("sample", ((JsonObject) samples.get(0)).getString("yaml"));
        }
        return result;
    }

    /**
     * The options of the elements of an EIP that match the filter, when none of its own do: logStackTrace is an option
     * of the redeliveryPolicy of onException, and a filter on onException found nothing (CAMEL-25370).
     */
    private static void putNestedOptions(
            CamelCatalog catalog, EipModel model, String filter, OptionScope scope, JsonObject result) {
        JsonArray nested = new JsonArray();
        String[] example = new String[1];
        JsonObject yamlNodes = null;
        for (BaseOptionModel opt : model.getOptions()) {
            if (!"element".equals(opt.getKind())) {
                continue;
            }
            if (isWrittenAsKind(opt)) {
                // the element is written as one of its kinds, and the options are the ones of the kinds: the error
                // handler's deadLetterUri is errorHandler: {deadLetterChannel: {deadLetterUri: ...}} (CAMEL-25377).
                // An option that several kinds have is listed once, with the other kinds in alsoUnder.
                Map<String, JsonObject> byName = new LinkedHashMap<>();
                for (String kind : opt.getOneOfs()) {
                    EipModel element = catalog.eipModel(kind);
                    if (element == null || element.getOptions() == null) {
                        continue;
                    }
                    JsonArray found = new JsonArray();
                    addNestedOptions(element, kind, false, filter, scope, found, example);
                    for (Object f : found) {
                        JsonObject o = (JsonObject) f;
                        JsonObject first = byName.putIfAbsent(o.getString("name"), o);
                        if (first != null) {
                            JsonArray also = (JsonArray) first.computeIfAbsent("alsoUnder", k -> new JsonArray());
                            also.add(kind);
                        }
                    }
                }
                nested.addAll(byName.values());
                continue;
            }
            EipModel element = catalog.eipModel(opt.getName());
            // the option must hold that model, not one that only shares its name (templatedRoute.bean is a bean
            // factory, not the bean EIP)
            if (element == null || element.getOptions() == null || !holds(opt, element)) {
                continue;
            }
            if (yamlNodes == null) {
                yamlNodes = yamlDslNodes(catalog);
            }
            String key = yamlKey(yamlNodes, model.getName(), opt.getName(), element);
            addNestedOptions(element, key, "array".equals(opt.getType()), filter, scope, nested, example);
        }
        if (!nested.isEmpty()) {
            result.put("nestedOptions", nested);
            result.put("nestedHint", "options of an element of " + model.getName() + ": write them under that element,"
                                     + " for example " + example[0]);
        }
    }

    private static void addNestedOptions(
            EipModel element, String under, boolean list, String filter, OptionScope scope, JsonArray nested,
            String[] example) {
        for (BaseOptionModel inner : element.getOptions()) {
            if (matchesOptionFilter(inner, filter) && scope.accepts(inner, filter)) {
                JsonObject o = optionToJson(inner, null);
                o.put("under", under);
                nested.add(o);
                if (example[0] == null) {
                    example[0] = under + ": " + (list ? "[{" : "{") + inner.getName() + ": ..." + (list ? "}]" : "}");
                }
            }
        }
    }

    /**
     * Whether the option is a single element written as one of its kinds, not under its own name: the error handler is
     * errorHandler: {deadLetterChannel: {...}}, never errorHandlerType:. Not a list such as outputs (written under
     * steps:), nor an element whose kind is its own name (when).
     */
    private static boolean isWrittenAsKind(BaseOptionModel opt) {
        return "element".equals(opt.getKind()) && "object".equals(opt.getType()) && opt.getOneOfs() != null
                && !opt.getOneOfs().isEmpty() && !opt.getOneOfs().contains(opt.getName());
    }

    /**
     * The YAML DSL key of an element of an EIP, from the YAML DSL model of the catalog: the catalog names an element
     * after its model, which is not always its YAML key (routeTemplate's templateParameter is written as parameters:).
     * The element is the YAML child of that name, else the one whose model has the same title. Falls back to the
     * catalog name.
     */
    static String yamlKey(JsonObject yamlNodes, String eip, String name, EipModel element) {
        JsonObject node = yamlNodes != null ? (JsonObject) yamlNodes.get(eip) : null;
        JsonArray children = node != null ? (JsonArray) node.get("children") : null;
        if (children == null) {
            return name;
        }
        for (Object c : children) {
            if (name.equals(((JsonObject) c).getString("name"))) {
                return name;
            }
        }
        for (Object c : children) {
            JsonObject child = (JsonObject) c;
            String ref = child.getString("ref");
            if (ref == null) {
                continue;
            }
            JsonObject refNode = (JsonObject) yamlNodes.get(ref);
            if (ref.equals(element.getName())
                    || (refNode != null && element.getTitle() != null
                            && element.getTitle().equals(refNode.getString("title")))) {
                return child.getString("name");
            }
        }
        return name;
    }

    /** The nodes of the YAML DSL model the catalog ships (schemas/camelYamlDsl-model.json), or null if it has none. */
    private static JsonObject yamlDslNodes(CamelCatalog catalog) {
        try (InputStream is = catalog.getVersionManager()
                .getResourceAsStream("org/apache/camel/catalog/schemas/camelYamlDsl-model.json")) {
            if (is == null) {
                return null;
            }
            JsonObject root = (JsonObject) Jsoner.deserialize(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            return (JsonObject) root.get("nodes");
        } catch (Exception e) {
            return null;
        }
    }

    /** Whether the element option holds the given model: its java type, inside List&lt;...&gt; and without generics. */
    private static boolean holds(BaseOptionModel opt, EipModel element) {
        String type = opt.getJavaType();
        if (type == null || element.getJavaType() == null) {
            return false;
        }
        if (type.startsWith("java.util.List<") && type.endsWith(">")) {
            type = type.substring("java.util.List<".length(), type.length() - 1);
        }
        int generic = type.indexOf('<');
        if (generic > 0) {
            type = type.substring(0, generic);
        }
        return type.equals(element.getJavaType());
    }

    /**
     * The camel.&lt;name&gt;.* main options when the name is one of their groups (resilience4j, faulttolerance, health,
     * metrics, threadpool, rest, ...), else null. A model asks for "resilience4j" as a component when it wants the
     * application.properties keys of the circuit breaker.
     */
    static JsonObject mainOptionsGroup(CamelCatalog catalog, String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        boolean fullKey = n.startsWith("camel.");
        if (fullKey) {
            n = n.substring("camel.".length());
        }
        if (n.endsWith(".")) {
            n = n.substring(0, n.length() - 1);
        }
        // a dotted name is a key only when written as one (camel.server.port), not a class name
        if (n.isEmpty() || (n.contains(".") && !fullKey)) {
            return null;
        }
        // camel.jbang.* are the keys of camel run and export, in the jbang model of the catalog (CAMEL-25541)
        boolean jbang = n.equals("jbang") || n.startsWith("jbang.");
        List<? extends BaseOptionModel> all;
        try {
            all = jbang
                    ? (catalog.jbangModel() != null ? catalog.jbangModel().getOptions() : null)
                    : catalog.mainModel().getOptions();
        } catch (Exception e) {
            return null;
        }
        if (all == null) {
            return null;
        }
        String key = "camel." + n;
        // a full key such as camel.server.port: its group with that key first (CAMEL-25541)
        BaseOptionModel matched = all.stream()
                .filter(o -> o.getName() != null && normalizeKey(o.getName()).equals(normalizeKey(key)))
                .findFirst().orElse(null);
        String group = matched != null ? matched.getName().substring(0, matched.getName().lastIndexOf('.')) : key;
        List<BaseOptionModel> members = optionsUnder(all, group);
        boolean unknownKey = false;
        if (members.isEmpty() && n.contains(".")) {
            // not a key, but its group is: the keys there are, with the closest ones
            group = key.substring(0, key.lastIndexOf('.'));
            members = optionsUnder(all, group);
            unknownKey = true;
        }
        if (members.isEmpty()) {
            return null;
        }
        JsonArray options = new JsonArray();
        if (matched != null) {
            options.add(mainOptionToJson(matched));
        }
        for (BaseOptionModel o : members) {
            if (o != matched) {
                options.add(mainOptionToJson(o));
            }
        }
        JsonObject result = new JsonObject();
        result.put("kind", jbang ? "jbang-options" : "main-options");
        result.put("group", group);
        if (jbang) {
            result.put("note", "the " + group + ".* keys go in application.properties and are read by camel run and"
                               + " camel export, not by the Camel application itself");
        } else if (matched != null) {
            result.put("note", matched.getName() + " is a key for application.properties (camel run and Camel Main read"
                               + " it at startup); the other " + group + ".* keys follow it");
        } else {
            result.put("note", name + " is not a component: these are the " + group + ".* keys for"
                               + " application.properties (camel run reads them at startup)");
        }
        if (matched != null) {
            result.put("matchedKey", matched.getName());
        }
        if (unknownKey) {
            result.put("error", "No key " + name.trim() + " in " + group + ".*");
            String[] closest = EditDistanceSuggestionStrategy.suggest(
                    members.stream().map(BaseOptionModel::getName).toList(), key, 5);
            if (closest.length > 0) {
                result.put("suggestions", new JsonArray(Arrays.asList(closest)));
            }
        }
        result.put("count", options.size());
        result.put("options", options);
        return result;
    }

    private static List<BaseOptionModel> optionsUnder(List<? extends BaseOptionModel> all, String group) {
        String prefix = group.toLowerCase(Locale.ROOT) + ".";
        List<BaseOptionModel> answer = new ArrayList<>();
        for (BaseOptionModel o : all) {
            if (o.getName() != null && o.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                answer.add(o);
            }
        }
        return answer;
    }

    /** A key without case and dashes: camel.jbang.camel-version is camel.jbang.camelVersion. */
    private static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT).replace("-", "");
    }

    private static JsonObject mainOptionToJson(BaseOptionModel o) {
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
        return jo;
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
            // what a component does, or a broker it reaches without a component of its own, such as artemis with the
            // jms component (CAMEL-25783)
            String[] intent = CatalogSamples.COMPONENT_INTENTS.get(term.trim().toLowerCase(Locale.ROOT));
            if (intent != null && matches.stream().noneMatch(o -> intent[0].equals(((JsonObject) o).getString("name")))) {
                ComponentModel m = catalog.componentModel(intent[0]);
                if (m != null) {
                    JsonObject o = summary("component", m.getScheme(), m.getTitle(), m.getDescription(), m.getLabel());
                    o.put("syntax", m.getSyntax());
                    o.put("note", "'" + term.trim() + "' is done with " + intent[1]);
                    matches.add(0, o);
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
            // the beans entry of the YAML DSL has no model to suggest: a model that wants to declare a bean asks for
            // beans, or for bean (CAMEL-25719)
            String key = normalizeName(term);
            if (BEANS_ENTRY.equals(key) || "bean".equals(key)) {
                matches.add(summary("eip", BEANS_ENTRY, "Beans", BEANS_DESCRIPTION, "configuration"));
            }
            // and the entries of an application model, by the start of their name (ssl)
            for (String entry : APP_ENTRIES) {
                if (key.length() >= 3 && normalizeName(entry).startsWith(key)) {
                    EipModel m = appModel(catalog, entry);
                    if (m != null) {
                        matches.add(summary("eip", m.getName(), m.getTitle(), m.getDescription(), m.getLabel()));
                    }
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
        if (kind == null || "kamelet".equals(kind)) {
            // the Kamelets whose name or description has the term: a few next to the components, all when asked for
            int n = 0;
            int maxKamelets = kind == null ? Math.min(max, 5) : max;
            String lower = term.toLowerCase(Locale.ROOT);
            for (KameletDefinitions.Definition def : kameletsMatching(lower)) {
                if (n++ >= maxKamelets) {
                    break;
                }
                JsonObject o = summary("kamelet", def.name(), def.title() != null ? def.title() : def.name(),
                        def.description(), null);
                if (def.type() != null) {
                    o.put("type", def.type());
                }
                o.put("uri", "kamelet:" + def.name());
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
        // a header name models write for one that has another name, such as CamelHttpStatusCode
        String wrong = HeaderChecks.WRONG_NAMES.get(term.trim());
        if (wrong != null) {
            result.put("header", "Camel has no header " + term.trim() + ": " + wrong);
            result.put("message", result.getString("message") + "; " + result.getString("header"));
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

    /**
     * The hint naming each doc page with the call that returns it: a model given docPage=<page> asked for the page by
     * another name instead (kamelet-custom for how to write a Kamelet).
     */
    private static String pagesHint(List<JsonObject> docPages, String key) {
        // key is the field of a page with its name
        StringBuilder sb = new StringBuilder();
        for (JsonObject p : docPages) {
            String page = p.getString(key);
            if (page == null) {
                continue;
            }
            sb.append(sb.isEmpty() ? "" : "; ").append("docPage=").append(page);
            if (p.getString("title") != null) {
                sb.append(": ").append(p.getString("title"));
            }
        }
        return sb.isEmpty()
                ? "docPage=<" + key + "> returns that documentation page as text"
                : sb + " (each returned as text)";
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
        String answer = "from: consumes (reads or receives, starts a route); to: produces (writes or sends); to consume one"
                        + " message in the middle of a route use the poll EIP (poll: {uri: ...}), or pollEnrich";
        if ("file".equals(model.getScheme())) {
            // a lookup table the route needs is read, not consumed: the poll EIP moves the file when it has no noop
            answer += "; to read a data file the route needs, such as a lookup table, use the constant language with"
                      + " resource:file: (such as setVariable with constant: resource:file:stock.json)";
        }
        return answer;
    }

    /**
     * How to pick the one element of a JSON list by a field: the answer of camel_catalog_doc jsonpath says it first.
     */
    static final String JSONPATH_ONE_ELEMENT = "to pick the one element of a list by a field, write a filter with"
                                               + " resultType: java.util.Map, such as expression: \"$[?(@.orderId =="
                                               + " '${header.id}')]\" and resultType: java.util.Map: the element when one"
                                               + " matches, null when none does (an index after the filter gives an empty"
                                               + " list); camel_catalog_sample jsonpath shows it in a route";

    /** How many options of a component are listed in full when no filter is given (CAMEL-25541). */
    static final int DEFAULT_OPTIONS_LIMIT = 20;

    /**
     * The longest answer, in characters: activemq listed 88 options in 38 K characters, which a client rejected or
     * spilled to a file instead of showing it to the model (CAMEL-25541).
     */
    static final int MAX_ANSWER_LENGTH = 16000;

    /** How much of the text of a page a cut answer keeps at least, before its lists are cut. */
    static final int MIN_DOC_KEPT = 8000;

    /**
     * The main option group that configures what a component uses under camel run or Camel Main: platform-http listens
     * on the camel.server.* port, which none of its own options sets.
     */
    private static final Map<String, String> MAIN_CONFIG_GROUPS = Map.of("platform-http", "camel.server");

    /** What a component answer needs besides the model: the catalog, the filter, the option limit and the page. */
    private record ComponentAnswer(CamelCatalog catalog, String filter, int limit, List<DocSection> sections) {
    }

    private static JsonObject componentDoc(
            ComponentModel model, ComponentAnswer how, OptionScope scope, boolean includeHeaders, String doc,
            String docExcerpt, List<JsonObject> docPages) {
        String filter = how.filter();
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
            putComponentOptions(result, model, how, scope);
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
        if (docExcerpt != null) {
            // what the options cannot say: the syntax and the essentials, from the component's own page
            result.put("documentation", docExcerpt);
            result.put("documentationHint", "the start of the component's documentation page; includeDoc=true for all of it");
        }
        if (!docPages.isEmpty()) {
            // named with their titles, so a model can tell which one answers its question
            result.put("docPages", new JsonArray(docPages));
            result.put("docPagesHint", pagesHint(docPages, "page"));
        }
        if (!how.sections().isEmpty()) {
            // what the excerpt leaves out is often what decides correctness (the mail useHeader* options, the xslt
            // header parameters), so the page says what else it has, the sections with the filter term first
            JsonArray titles = sectionTitles(how.sections(), filter);
            result.put("sections", titles);
            int matching = filter != null ? sectionsContaining(how.sections(), filter) : 0;
            result.put("sectionsHint", "docPage=<section title> returns that section of the page as text"
                                       + (matching > 0
                                               ? "; the first " + matching + " contain '" + filter + "'"
                                               : ""));
        }
        String mainGroup = MAIN_CONFIG_GROUPS.get(model.getScheme());
        if (mainGroup != null) {
            putMainConfig(result, how, mainGroup);
        }
        return result;
    }

    /**
     * The options of a component: those the scope accepts and the filter matches, the required ones first, then the
     * endpoint options (what a URI takes) and the component options, with their application.properties key. Without a
     * filter only the first {@link #DEFAULT_OPTIONS_LIMIT} are listed in full and the rest by name. A filter that
     * matches nothing gets a note and the closest option names, and the prefix options are always said, as they take
     * keys no option list names (CAMEL-25541).
     */
    private static void putComponentOptions(
            JsonObject result, ComponentModel model, ComponentAnswer how, OptionScope scope) {
        String filter = how.filter();
        List<JsonObject> required = new ArrayList<>();
        List<JsonObject> byName = new ArrayList<>();
        List<JsonObject> others = new ArrayList<>();
        int omitted = 0;
        // most options of a component are also endpoint options of the same name (112 of the 116 common ones of
        // kafka): such an option is listed once, with both scopes and its key, so the key is not past the limit and
        // matchedOptions counts names
        Set<String> componentNames = new HashSet<>();
        if (model.getComponentOptions() != null) {
            model.getComponentOptions().forEach(opt -> componentNames.add(opt.getName()));
        }
        Set<String> endpointListed = new HashSet<>();
        Set<String> endpointOmitted = new HashSet<>();
        for (String optionScope : List.of("endpoint", "component")) {
            List<? extends BaseOptionModel> options
                    = "endpoint".equals(optionScope) ? model.getEndpointOptions() : model.getComponentOptions();
            if (options == null) {
                continue;
            }
            for (BaseOptionModel opt : options) {
                if (!matchesOptionFilter(opt, filter)) {
                    continue;
                }
                boolean endpoint = "endpoint".equals(optionScope);
                if (!endpoint && endpointListed.contains(opt.getName())) {
                    // already listed with its endpoint twin
                    continue;
                }
                if (!scope.accepts(opt, filter)) {
                    // an option of both scopes is counted once (bridgeErrorHandler of timer is advanced on the
                    // endpoint only, so it is listed as a component option)
                    if (endpoint) {
                        endpointOmitted.add(opt.getName());
                        omitted++;
                    } else if (!endpointOmitted.contains(opt.getName())) {
                        omitted++;
                    }
                    continue;
                }
                if (endpoint) {
                    endpointListed.add(opt.getName());
                } else if (endpointOmitted.contains(opt.getName())) {
                    // listed as a component option after all, so not omitted
                    omitted--;
                }
                boolean both = endpoint && componentNames.contains(opt.getName());
                JsonObject o = optionToJson(opt, both ? "endpoint,component" : optionScope);
                if (both || !endpoint) {
                    // the only valid application.properties key of an option: camel.component.<scheme>.* sets a
                    // component option, never an endpoint one
                    o.put("configKey", "camel.component." + model.getScheme() + "." + opt.getName());
                }
                if (opt.isRequired()) {
                    required.add(o);
                } else if (filter != null && opt.getName().toLowerCase(Locale.ROOT).contains(filter)) {
                    // a name match before a match in a description
                    byName.add(o);
                } else {
                    others.add(o);
                }
            }
        }
        List<JsonObject> ordered = new ArrayList<>(required);
        ordered.addAll(byName);
        ordered.addAll(others);
        int max = how.limit() >= 0 ? how.limit() : filter == null ? DEFAULT_OPTIONS_LIMIT : Integer.MAX_VALUE;
        // the required options are always listed
        int listed = Math.min(ordered.size(), Math.max(max, required.size()));
        putOptions(result, new JsonArray(ordered.subList(0, listed)), omitted, scope);
        result.put("matchedOptions", ordered.size());
        if (listed < ordered.size()) {
            Set<String> rest = new LinkedHashSet<>();
            for (JsonObject o : ordered.subList(listed, ordered.size())) {
                rest.add(o.getString("name"));
            }
            result.put("truncated", true);
            result.put("listedOptions", listed);
            result.put("moreOptions", new JsonArray(new ArrayList<>(rest)));
            String omittedHint = result.getString("optionsHint");
            result.put("optionsHint", optionsHint(listed, ordered.size(), omittedHint));
        }
        if (filter != null && ordered.isEmpty() && omitted == 0) {
            // an empty list read as "not supported" (smtp + tls, xslt + param): say that nothing matched, and where
            // else to look
            result.put("note", "no option matches '" + filter + "': see closestOptions, prefixOptions and the"
                               + " sections of the page below (docPage=<section title> returns one)");
            JsonArray closest = closestOptions(model, filter);
            if (!closest.isEmpty()) {
                result.put("closestOptions", closest);
            }
        }
        JsonArray prefixOptions = prefixOptions(model);
        if (!prefixOptions.isEmpty()) {
            result.put("prefixOptions", prefixOptions);
        }
    }

    /** Says how many options are listed in full and how to get the others, before the hint about omitted ones. */
    private static String optionsHint(int listed, int matched, String omittedHint) {
        String hint = listed + " of " + matched + " options listed in full (required first, then the endpoint"
                      + " options), the others by name in moreOptions; optionsFilter=<word or option name> returns the"
                      + " matching ones in full, includeOptions=all with limit=<n> lists more";
        return omittedHint != null ? hint + "; " + omittedHint : hint;
    }

    /**
     * The options whose name, or a word of it, is closest to the filter by edit distance, with their group: ssl for a
     * mistyped tsl.
     */
    private static JsonArray closestOptions(ComponentModel model, String filter) {
        Map<String, List<BaseOptionModel>> byWord = new LinkedHashMap<>();
        Map<BaseOptionModel, String> scopes = new LinkedHashMap<>();
        for (String optionScope : List.of("endpoint", "component")) {
            List<? extends BaseOptionModel> options
                    = "endpoint".equals(optionScope) ? model.getEndpointOptions() : model.getComponentOptions();
            if (options == null) {
                continue;
            }
            for (BaseOptionModel opt : options) {
                scopes.putIfAbsent(opt, optionScope);
                byWord.computeIfAbsent(opt.getName().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(opt);
                for (String word : opt.getName().split("(?<=[a-z0-9])(?=[A-Z])")) {
                    if (word.length() > 1) {
                        byWord.computeIfAbsent(word.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(opt);
                    }
                }
            }
        }
        JsonArray answer = new JsonArray();
        Set<String> seen = new HashSet<>();
        for (String word : EditDistanceSuggestionStrategy.suggest(byWord.keySet(), filter, 5)) {
            for (BaseOptionModel opt : byWord.get(word)) {
                if (answer.size() < 5 && seen.add(opt.getName())) {
                    JsonObject o = new JsonObject();
                    o.put("name", opt.getName());
                    if (opt.getGroup() != null) {
                        o.put("group", opt.getGroup());
                    }
                    o.put("scope", scopes.get(opt));
                    answer.add(o);
                }
            }
        }
        return answer;
    }

    /**
     * The multi-value options of a component, which take every URI parameter with their prefix: any mail.* parameter of
     * smtp goes to JavaMail (mail.smtp.starttls.enable=true), which no option is named after.
     */
    private static JsonArray prefixOptions(ComponentModel model) {
        JsonArray answer = new JsonArray();
        Set<String> seen = new HashSet<>();
        if (model.getEndpointOptions() == null) {
            return answer;
        }
        for (BaseOptionModel opt : model.getEndpointOptions()) {
            if (opt.isMultiValue() && opt.getPrefix() != null && seen.add(opt.getName())) {
                JsonObject o = new JsonObject();
                o.put("name", opt.getName());
                o.put("prefix", opt.getPrefix());
                o.put("usage", "any " + opt.getPrefix() + "* URI parameter is passed on as " + opt.getName() + ": ?"
                               + opt.getPrefix() + "<key>=<value>");
                answer.add(o);
            }
        }
        return answer;
    }

    /**
     * Where a component that runs on a server of camel run or Camel Main gets its settings: the HTTP port of
     * platform-http is camel.server.port, which an agent searched the component's options for (CAMEL-25541).
     */
    private static void putMainConfig(JsonObject result, ComponentAnswer how, String group) {
        result.put("mainConfig", "the HTTP server is configured by the " + group + ".* keys of"
                                 + " application.properties, not by an option of this component: with camel run it"
                                 + " starts when a route uses platform-http, on port 8080 (camel.server.port or camel"
                                 + " run --port changes it); with Camel Main set camel.server.enabled=true and"
                                 + " camel.server.port; camel_catalog_doc name=" + group + " lists the keys");
        if (how.filter() == null) {
            return;
        }
        JsonArray keys = new JsonArray();
        try {
            for (BaseOptionModel o : optionsUnder(how.catalog().mainModel().getOptions(), group)) {
                if (matchesOptionFilter(o, how.filter())) {
                    keys.add(mainOptionToJson(o));
                }
            }
        } catch (Exception e) {
            return;
        }
        if (!keys.isEmpty()) {
            result.put("mainOptions", keys);
        }
    }

    /**
     * The name the documentation page of a component has: its scheme, else the artifact (smtp, imap and pop3 are on the
     * mail page of camel-mail); null when there is none.
     */
    private static String componentDocName(CamelCatalog catalog, ComponentModel model) {
        if (catalog.asciiDoc(model.getScheme() + "-component") != null) {
            return model.getScheme();
        }
        String artifact = model.getArtifactId();
        if (artifact != null && artifact.startsWith("camel-")) {
            String name = artifact.substring("camel-".length());
            if (catalog.asciiDoc(name + "-component") != null) {
                return name;
            }
        }
        return null;
    }

    private static String titleWords(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }

    /**
     * A section of an AsciiDoc page: its title, its level (2 for ==), its text with its subsections, and its own text
     * up to its first subsection.
     */
    record DocSection(String title, int level, String text, String own) {
    }

    private static final Pattern HEADING = Pattern.compile("^(={2,4}) +(\\S.*)$");
    /** The delimiter line of an AsciiDoc listing (----), literal (....), comment (////) or passthrough (++++) block. */
    private static final Pattern BLOCK_DELIMITER = Pattern.compile("-{4,}|\\.{4,}|/{4,}|\\+{4,}");

    /** The sections of a page, from == to ====, in the order of the page; empty when there is no page. */
    static List<DocSection> docSections(String adoc) {
        List<DocSection> answer = new ArrayList<>();
        if (adoc == null) {
            return answer;
        }
        String[] lines = adoc.split("\n", -1);
        List<Integer> starts = new ArrayList<>();
        List<Integer> levels = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        // the delimiter of the listing, literal, comment or passthrough block the line is in: a block closes only on
        // the delimiter that opened it, so a .... line inside a ---- listing does not end it
        String fence = null;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].strip();
            if (fence == null && BLOCK_DELIMITER.matcher(trimmed).matches()) {
                fence = trimmed;
                continue;
            }
            if (fence != null) {
                if (trimmed.equals(fence)) {
                    fence = null;
                }
                continue;
            }
            Matcher m = HEADING.matcher(lines[i]);
            if (m.matches()) {
                starts.add(i);
                levels.add(m.group(1).length());
                titles.add(m.group(2).strip());
            }
        }
        for (int h = 0; h < starts.size(); h++) {
            int end = lines.length;
            for (int n = h + 1; n < starts.size(); n++) {
                if (levels.get(n) <= levels.get(h)) {
                    end = starts.get(n);
                    break;
                }
            }
            int ownEnd = h + 1 < starts.size() ? Math.min(end, starts.get(h + 1)) : end;
            String text = String.join("\n", Arrays.asList(lines).subList(starts.get(h), end)).strip();
            String own = String.join("\n", Arrays.asList(lines).subList(starts.get(h), ownEnd)).strip();
            answer.add(new DocSection(titles.get(h), levels.get(h), text, own));
        }
        return answer;
    }

    /**
     * The titles of the sections, those whose own text contains the filter first (SSL support before the Usage section
     * it is part of).
     */
    static JsonArray sectionTitles(List<DocSection> sections, String filter) {
        JsonArray first = new JsonArray();
        JsonArray rest = new JsonArray();
        for (DocSection s : sections) {
            if (filter != null && s.own().toLowerCase(Locale.ROOT).contains(filter)) {
                first.add(s.title());
            } else {
                rest.add(s.title());
            }
        }
        first.addAll(rest);
        return first;
    }

    private static int sectionsContaining(List<DocSection> sections, String filter) {
        return (int) sections.stream().filter(s -> s.own().toLowerCase(Locale.ROOT).contains(filter)).count();
    }

    /**
     * The section a docPage names: by its title ignoring case and punctuation (also the AsciiDoc id, such as
     * _ssl_support), else the first whose title contains it; null when none does.
     */
    static DocSection findSection(List<DocSection> sections, String page) {
        String key = normalizeName(page);
        if (key.isEmpty()) {
            return null;
        }
        for (DocSection s : sections) {
            if (key.equals(normalizeName(s.title()))) {
                return s;
            }
        }
        // else the words of the title from one of them on (usable parameters), not a run of letters across words
        String words = " " + titleWords(page);
        for (DocSection s : sections) {
            if ((" " + titleWords(s.title())).contains(words)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Cuts an answer longer than {@link #MAX_ANSWER_LENGTH} characters, with a marker that says what was cut and how to
     * ask for it: first the text of a page, at a line and down to {@link #MIN_DOC_KEPT} characters, with the titles of
     * the sections left out; then the longest list, from its end (the options left out are named in moreOptions).
     */
    static JsonObject capAnswer(JsonObject answer) {
        String doc = answer.getString("doc");
        int excess = answer.toJson().length() - MAX_ANSWER_LENGTH;
        if (excess > 0 && doc != null && doc.length() > MIN_DOC_KEPT) {
            // the text is escaped in the JSON, so cut by the share of the escaped length that has to go
            int escaped = Jsoner.serialize(doc).length();
            int keep = (int) ((long) doc.length() * Math.max(0, escaped - excess - 600) / escaped);
            keep = Math.max(MIN_DOC_KEPT, keep);
            int at = doc.lastIndexOf('\n', keep);
            at = at > keep / 2 ? at : keep;
            List<String> left = new ArrayList<>();
            for (DocSection s : docSections(doc.substring(at))) {
                if (left.size() < 30) {
                    left.add(s.title());
                }
            }
            answer.put("doc", doc.substring(0, at) + "\n\n[cut at " + MAX_ANSWER_LENGTH + " characters"
                              + (left.isEmpty() ? "" : "; the page goes on with: " + String.join(" | ", left))
                              + "; docPage=<section title> returns one section]");
            answer.put("truncated", true);
        }
        Map<String, Integer> cut = new LinkedHashMap<>();
        for (int round = 0; round < 10; round++) {
            excess = answer.toJson().length() - MAX_ANSWER_LENGTH;
            if (excess <= 0) {
                return answer;
            }
            String longest = null;
            int longestSize = 0;
            for (Map.Entry<String, Object> e : answer.entrySet()) {
                if (e.getValue() instanceof JsonArray arr && arr.size() > 1) {
                    int size = arr.toJson().length();
                    if (size > longestSize) {
                        longest = e.getKey();
                        longestSize = size;
                    }
                }
            }
            if (longest == null) {
                // no list left to shorten: the last resort is the longest text, the page below MIN_DOC_KEPT
                // included, so the answer never goes over MAX_ANSWER_LENGTH
                if (!cutLongestText(answer, excess)) {
                    return answer;
                }
                continue;
            }
            JsonArray arr = (JsonArray) answer.get(longest);
            List<String> removedNames = new ArrayList<>();
            int removed = 0;
            int freed = 0;
            while (arr.size() > 1 && freed < excess + 300) {
                Object last = arr.remove(arr.size() - 1);
                freed += Jsoner.serialize(last).length() + 1;
                removed++;
                if (last instanceof JsonObject o && o.getString("name") != null) {
                    removedNames.add(0, o.getString("name"));
                }
            }
            answer.put("truncated", true);
            if ("options".equals(longest) && answer.containsKey("listedOptions")) {
                // the options cut are named with the others not listed in full, and the hint says how many are left
                String prefix = answer.getInteger("listedOptions") + " of ";
                String hint = answer.getString("optionsHint");
                if (hint != null && hint.startsWith(prefix)) {
                    answer.put("optionsHint", arr.size() + " of " + hint.substring(prefix.length()));
                }
                answer.put("listedOptions", arr.size());
                JsonArray names = new JsonArray(removedNames);
                Object more = answer.get("moreOptions");
                if (more instanceof JsonArray m) {
                    names.addAll(m);
                }
                answer.put("moreOptions", names);
                continue;
            }
            int total = cut.merge(longest, removed, Integer::sum);
            answer.put("truncatedNote", "the answer is cut at " + MAX_ANSWER_LENGTH + " characters: the last " + total
                                        + " entries of " + longest + " are left out; optionsFilter=<word> or a"
                                        + " smaller limit narrows the answer");
        }
        return answer;
    }

    /**
     * Cuts the longest text of an answer by what it is over, with a marker; false when no text is long enough to free
     * that much.
     */
    private static boolean cutLongestText(JsonObject answer, int excess) {
        String key = null;
        int length = 0;
        for (Map.Entry<String, Object> e : answer.entrySet()) {
            if (e.getValue() instanceof String text && text.length() > length) {
                key = e.getKey();
                length = text.length();
            }
        }
        if (key == null) {
            return false;
        }
        String marker = "\n[cut at " + MAX_ANSWER_LENGTH + " characters]";
        String text = (String) answer.get(key);
        // the text is escaped in the JSON, so cut by the share of the escaped length that has to go
        int escaped = Jsoner.serialize(text).length();
        int keep = (int) ((long) text.length() * (escaped - excess - marker.length() - 50) / escaped);
        if (keep <= 0) {
            return false;
        }
        answer.put(key, text.substring(0, keep) + marker);
        answer.put("truncated", true);
        return true;
    }

    /**
     * The sub-pages of a component's documentation, found from the links of its page to them
     * ({@code xref:others:kamelet-custom.adoc[Writing a custom Kamelet]}): the page name to ask for with
     * {@code docPage}, and its title.
     */
    static List<JsonObject> componentDocPages(String scheme, String adoc) {
        List<JsonObject> pages = new ArrayList<>();
        if (adoc == null || scheme == null) {
            return pages;
        }
        Matcher m = Pattern.compile("xref:others:" + Pattern.quote(scheme) + "-([a-z0-9][a-z0-9-]*)\\.adoc\\[([^\\]]+)]")
                .matcher(adoc);
        Set<String> seen = new HashSet<>();
        while (m.find()) {
            if (seen.add(m.group(1))) {
                JsonObject jo = new JsonObject();
                jo.put("page", m.group(1));
                jo.put("title", m.group(2));
                pages.add(jo);
            }
        }
        return pages;
    }

    private static JsonObject dataFormatDoc(
            DataFormatModel model, String filter, OptionScope scope, String doc, String docExcerpt) {
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
        if (docExcerpt != null) {
            // what the options cannot say: the syntax and the essentials, from the page itself
            result.put("documentation", docExcerpt);
            result.put("documentationHint", "the start of the documentation page; includeDoc=true for all of it");
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
            boolean docPageOnly, String docExcerpt) {
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
        if ("jsonpath".equals(model.getName())) {
            // the one element of a list by a field, which models wrote as $[?(...)][0], an empty list
            result.put("usage", JSONPATH_ONE_ELEMENT);
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
        if (docExcerpt != null) {
            // what the options cannot say: the syntax and the essentials, from the page itself
            result.put("documentation", docExcerpt);
            result.put("documentationHint", "the start of the documentation page; includeDoc=true for all of it");
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
        // where it goes: onException written as a step inside a route fails the schema (CAMEL-25539)
        String placement = CatalogSamples.placement(model);
        if (placement != null) {
            result.put("placement", placement);
        }
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
            if (opt.isMultiEnum() && opt.getEnums().size() > 1) {
                // CAMEL-25739: the option takes several of its values
                o.put("multiple", "several of enumValues can be set, separated by comma, such as "
                                  + opt.getEnums().get(0) + "," + opt.getEnums().get(1));
            }
        }
        if (isWrittenAsKind(opt)) {
            // written as one of its kinds (CAMEL-25370)
            JsonArray oneOf = new JsonArray();
            oneOf.addAll(opt.getOneOfs());
            o.put("oneOf", oneOf);
            o.put("yaml", "write one of oneOf as the key, not '" + opt.getName() + "': for example "
                          + opt.getOneOfs().get(0) + ": {...}");
        } else {
            String shape = yamlShape(opt);
            if (shape != null) {
                o.put("yaml", shape);
            }
        }
        return o;
    }

    /**
     * How an option whose type says only object or array is written in YAML (CAMEL-25539): an expression has its
     * language as the key (handled: true fails the schema), and a list of properties is a list of key/value pairs
     * (namespace of xpath). The example comes first in the canonical YAML form, the explanation follows as a comment.
     * Null for any other option.
     */
    static String yamlShape(BaseOptionModel opt) {
        String name = opt.getName();
        if ("expression".equals(opt.getKind()) && EXPRESSION_SUB_ELEMENT.equals(opt.getJavaType())) {
            if (opt.isAsPredicate()) {
                return name + ": {constant: {expression: \"true\"}}  # a predicate with its language as the key, not"
                       + " a plain true/false; simple: instead of constant: for a condition";
            }
            return name + ": {header: {expression: myKey}}  # an expression with its language as the key (header,"
                   + " simple, jq, ...)";
        }
        if ("element".equals(opt.getKind()) && "object".equals(opt.getType()) && opt.isAsPredicate()
                && (opt.getOneOfs() == null || opt.getOneOfs().isEmpty())) {
            return name + ": {expression: {simple: {expression: \"${header.foo} == 'bar'\"}}}  # a predicate under"
                   + " expression:, with its language as the key (simple, jq, xpath, ...)";
        }
        if ("array".equals(opt.getType()) && PROPERTY_LIST.equals(opt.getJavaType())) {
            String desc = opt.getDescription() != null ? opt.getDescription().toLowerCase(Locale.ROOT) : "";
            if (desc.contains("namespace")) {
                return name + ": [{key: c, value: \"http://acme.com/cheese\"}]  # a list of key/value pairs: the"
                       + " prefix as key, the namespace uri as value";
            }
            return name + ": [{key: foo, value: bar}]  # a list of key/value pairs";
        }
        return null;
    }
}
