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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultDumpRoutesStrategy;
import org.apache.camel.java.in.JavaParseResult;
import org.apache.camel.model.BeanFactoryDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.TemplatedRouteDefinition;
import org.apache.camel.model.app.BeansDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.xml.in.ModelParser;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Converts a route file from one DSL to another (YAML, XML, Java) without running it (CAMEL-25254): the file is read
 * into the model of a Camel context that is never started, and written with the model writers. No code of the file or
 * the project runs and no class of it is loaded: Java is read by the Java DSL parser, XML by the XML parser, YAML by
 * the YAML DSL with its beans read as definitions only (the YAML DSL would create them). Comments do not survive, and a
 * Java route the parser cannot read completely (a lambda, a processor) is refused rather than converted with a gap.
 */
public final class RouteDslConverter {

    /** The DSLs a file is converted between. */
    public static final List<String> FORMATS = List.of("yaml", "xml", "java");

    /**
     * The result of a conversion.
     *
     * @param content  the file in the target DSL, null when refused
     * @param fileName the name of the converted file next to the original (orders.camel.yaml, Orders.java)
     * @param notes    what the converted file does not carry over (comments...)
     * @param refused  why the file is not converted, null when it is
     */
    public record Result(String content, String fileName, List<String> notes, String refused) {

        public boolean converted() {
            return refused == null;
        }

        static Result refuse(String why) {
            return new Result(null, null, List.of(), why);
        }
    }

    private static final Pattern YAML_BEANS = Pattern.compile("^-\\s+beans\\s*:");
    private static final Pattern COMMENT = Pattern.compile("(?m)^\\s*(#|//|/\\*|<!--)");

    private RouteDslConverter() {
    }

    /** The converted file with what did not carry over as a comment at its top, in the comment syntax of the DSL. */
    public static String withNotes(String content, List<String> notes, String format) {
        if (notes.isEmpty()) {
            return content;
        }
        StringBuilder sb = new StringBuilder();
        switch (format) {
            case "xml" -> {
                sb.append("<!--\n");
                notes.forEach(n -> sb.append("  ").append(n.replace("--", "- -")).append('\n'));
                sb.append("-->\n");
            }
            case "java" -> notes.forEach(n -> sb.append("// ").append(n).append('\n'));
            default -> notes.forEach(n -> sb.append("# ").append(n).append('\n'));
        }
        return sb.append(content).toString();
    }

    /** The DSL of a file by its name: yaml, xml or java; null for another file. */
    public static String formatOf(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            return "yaml";
        }
        if (name.endsWith(".xml")) {
            return "xml";
        }
        if (name.endsWith(".java")) {
            return "java";
        }
        return null;
    }

    /** Converts the file to the format, with the Java sources of its folder for the constants a Java route uses. */
    public static Result convert(Path file, String format) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        return convert(file.getFileName().toString(), content, format, javaSources(file));
    }

    /** Converts the content of a file of the given name to the format. */
    public static Result convert(
            String fileName, String content, String format, Map<String, Supplier<String>> javaSources) {
        String from = formatOf(fileName);
        if (from == null) {
            return Result.refuse(fileName + " is no YAML, XML or Java route file");
        }
        if (!FORMATS.contains(format)) {
            return Result.refuse("Unknown format " + format + ": one of " + FORMATS);
        }
        if (from.equals(format)) {
            return Result.refuse(fileName + " is " + format + " already");
        }
        Result result = read(fileName, content, from, format, javaSources);
        if (!result.converted()) {
            return result;
        }
        return verify(fileName, content, from, format, javaSources, result);
    }

    /**
     * Reads the converted file back and compares its model with the original's, both written as XML: a file that does
     * not load is refused (a writer of the target DSL lacks something), a difference (an error handler the target DSL
     * does not write) is said in the notes, so nothing is lost without a word.
     */
    private static Result verify(
            String fileName, String content, String from, String format, Map<String, Supplier<String>> javaSources,
            Result result) {
        Result back = read(result.fileName(), result.content(), format, "xml", javaSources);
        if (!back.converted()) {
            return Result.refuse("the converted " + format.toUpperCase(Locale.ROOT) + " would not load ("
                                 + back.refused() + "): " + fileName + " cannot be converted to it yet");
        }
        Result original = read(fileName, content, from, "xml", javaSources);
        if (!original.converted()) {
            return result;
        }
        String expected = original.content();
        if ("java".equals(format)) {
            // the beans do not go to Java, which the notes say already
            expected = withoutBeans(expected);
        }
        String difference = firstDifference(expected, back.content());
        if (difference == null) {
            return result;
        }
        List<String> notes = new ArrayList<>(result.notes());
        notes.add("Check the converted file, its routes differ from " + fileName + ": " + difference);
        return new Result(result.content(), result.fileName(), notes, null);
    }

    /** An XML dump without its bean definitions, nested properties included; the bean EIPs of routes stay. */
    static String withoutBeans(String xml) {
        StringBuilder sb = new StringBuilder();
        boolean inBean = false;
        for (String line : xml.split("\n")) {
            String t = line.strip();
            // a bean definition (name, type), not the bean EIP of a route (ref, beanType)
            if (!inBean && t.startsWith("<bean ") && !t.contains(" ref=")
                    && (t.contains(" name=") || t.contains(" type="))) {
                inBean = !t.endsWith("/>");
                continue;
            }
            if (inBean) {
                inBean = !t.equals("</bean>");
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /** The first line that differs between two dumps, as expected / found; null when they are the same. */
    static String firstDifference(String expected, String found) {
        List<String> a = significant(expected);
        List<String> b = significant(found);
        for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
            String x = i < a.size() ? a.get(i) : "(nothing)";
            String y = i < b.size() ? b.get(i) : "(nothing)";
            if (!x.equals(y)) {
                return "expected " + x + " but found " + y;
            }
        }
        return null;
    }

    private static List<String> significant(String dump) {
        List<String> lines = new ArrayList<>();
        for (String line : dump.split("\n")) {
            String t = line.strip();
            if (!t.isEmpty()) {
                lines.add(t);
            }
        }
        return lines;
    }

    /** The model of the file written in the format, also its own (tests compare the models of two files this way). */
    static Result read(
            String fileName, String content, String from, String format, Map<String, Supplier<String>> javaSources) {
        Resource resource = ResourceHelper.fromString("file:" + fileName, content);
        List<String> notes = new ArrayList<>();
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            // never started: adding the definitions only registers them
            String why = switch (from) {
                case "java" -> loadJava(context, fileName, content, javaSources, resource, notes);
                case "xml" -> loadXml(context, content, resource);
                default -> loadYaml(context, content, resource);
            };
            if (why != null) {
                return Result.refuse(why);
            }
            Model model = context.getCamelContextExtension().getContextPlugin(Model.class);
            if (model.getRouteDefinitions().isEmpty() && model.getRestDefinitions().isEmpty()
                    && model.getRouteTemplateDefinitions().isEmpty() && model.getRouteConfigurationDefinitions().isEmpty()
                    && model.getCustomBeans().isEmpty()) {
                return Result.refuse(fileName + " has no routes, rests, route configurations, templates or beans");
            }
            if ("java".equals(format) && model.getRouteDefinitions().isEmpty() && model.getRestDefinitions().isEmpty()
                    && model.getRouteTemplateDefinitions().isEmpty()) {
                return Result.refuse(fileName + " has only beans, which Java declares in code (such as with"
                                     + " @BindToRegistry)");
            }
            if ("java".equals(format) && !model.getRouteConfigurationDefinitions().isEmpty()) {
                return Result.refuse("route configurations are written in Java as a RouteConfigurationBuilder;"
                                     + " convert " + fileName + " without them");
            }
            String output = dump(context, format);
            if (!"java".equals(format) && output.contains("expressionDefinition")) {
                // a predicate or expression built in Java code has no YAML or XML form: the file would not load
                return Result.refuse(fileName + " uses a predicate or expression built in Java (such as"
                                     + " header(\"x\").isEqualTo(..)), which has no " + format.toUpperCase(Locale.ROOT)
                                     + " form: write it with simple first");
            }
            if (COMMENT.matcher(content).find()) {
                notes.add("The comments of " + fileName + " are not carried over");
            }
            String target = targetName(fileName, format);
            if ("java".equals(format)) {
                if (!model.getCustomBeans().isEmpty()) {
                    notes.add("The beans of " + fileName
                              + " are not carried over: declare them in Java, such as with @BindToRegistry");
                }
                output = javaClass(target.substring(0, target.length() - ".java".length()), output);
            }
            return new Result(output, target, notes, null);
        } catch (Exception e) {
            return Result.refuse(fileName + " could not be read: " + e.getMessage());
        }
    }

    /** The types a dumped Java route may use beside the route builder's own methods, imported when it does. */
    private static final Map<String, String> JAVA_TYPES = Map.of(
            "Map", "java.util.Map",
            "LoggingLevel", "org.apache.camel.LoggingLevel",
            "ExchangePattern", "org.apache.camel.ExchangePattern",
            "WaitForTaskToComplete", "org.apache.camel.WaitForTaskToComplete",
            "RestBindingMode", "org.apache.camel.model.rest.RestBindingMode",
            "RestParamType", "org.apache.camel.model.rest.RestParamType",
            "CollectionFormat", "org.apache.camel.model.rest.CollectionFormat",
            "ShutdownRoute", "org.apache.camel.ShutdownRoute",
            "ShutdownRunningTask", "org.apache.camel.ShutdownRunningTask");

    /** The dumped routes as the configure() of a route builder class, with the imports they need. */
    static String javaClass(String className, String routes) {
        StringBuilder sb = new StringBuilder();
        List<String> imports = new ArrayList<>();
        JAVA_TYPES.forEach((simple, fqn) -> {
            if (Pattern.compile("\\b" + simple + "\\.").matcher(routes).find()) {
                imports.add(fqn);
            }
        });
        imports.add("org.apache.camel.builder.RouteBuilder");
        imports.sort(null);
        imports.forEach(i -> sb.append("import ").append(i).append(";\n"));
        sb.append("\npublic class ").append(className).append(" extends RouteBuilder {\n\n");
        sb.append("    @Override\n    public void configure() throws Exception {\n");
        for (String line : routes.strip().split("\n")) {
            sb.append(line.isBlank() ? "" : "        " + line).append('\n');
        }
        sb.append("    }\n}\n");
        return sb.toString();
    }

    /** The name of the converted file: the same base name, as Camel names route files of the DSL. */
    static String targetName(String fileName, String format) {
        String base = fileName;
        for (String ext : List.of(".camel.yaml", ".camel.yml", ".camel.xml", ".yaml", ".yml", ".xml", ".java")) {
            if (base.toLowerCase(Locale.ROOT).endsWith(ext)) {
                base = base.substring(0, base.length() - ext.length());
                break;
            }
        }
        if ("java".equals(format)) {
            return javaClassName(base) + ".java";
        }
        return base + ".camel." + format;
    }

    /** orders-route to OrdersRoute: a Java class name of a base name. */
    static String javaClassName(String base) {
        StringBuilder sb = new StringBuilder();
        boolean upper = true;
        for (char c : base.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            } else {
                upper = true;
            }
        }
        if (sb.isEmpty() || !Character.isJavaIdentifierStart(sb.charAt(0))) {
            sb.insert(0, "Route");
        }
        return sb.toString();
    }

    private static String loadJava(
            DefaultCamelContext context, String fileName, String content, Map<String, Supplier<String>> javaSources,
            Resource resource, List<String> notes)
            throws Exception {
        if (JAVA_SEMANTIC.matcher(content).find()) {
            return SEMANTIC_REFUSED;
        }
        CamelCatalog catalog = new DefaultCamelCatalog();
        JavaParseResult r = ProjectRoutes.parseJava(content, javaSources, catalog);
        for (JavaParseResult.Unresolved u : r.unresolved()) {
            if (!JavaParseResult.configuresTheContext(u)) {
                return fileName + ":" + u.line() + " cannot be converted without running it: " + u.text() + " ("
                       + u.reason() + ")";
            }
            // a statement configuring the Camel context (semantic declarations, context settings) is no route: said
            notes.add("Not carried over, it configures the Camel context rather than the routes: " + u.text() + " ("
                      + fileName + ":" + u.line() + ")");
        }
        r.routes().getRoutes().forEach(d -> d.setResource(resource));
        r.rests().getRests().forEach(d -> d.setResource(resource));
        r.routeTemplates().getRouteTemplates().forEach(d -> d.setResource(resource));
        r.routeConfigurations().getRouteConfigurations().forEach(d -> d.setResource(resource));
        context.addRouteConfigurations(r.routeConfigurations().getRouteConfigurations());
        context.addRestDefinitions(r.rests().getRests(), false);
        context.addRouteTemplateDefinitions(r.routeTemplates().getRouteTemplates());
        context.addRouteDefinitions(r.routes().getRoutes());
        return null;
    }

    private static String loadXml(DefaultCamelContext context, String content, Resource resource) throws Exception {
        String root = xmlRoot(content);
        if (root == null) {
            return "no XML root element";
        }
        String namespace = xmlNamespace(content);
        ModelParser parser = new ModelParser(new StringReader(content), namespace);
        List<RouteDefinition> routes = new ArrayList<>();
        List<RestDefinition> rests = new ArrayList<>();
        List<RouteConfigurationDefinition> configurations = new ArrayList<>();
        List<RouteTemplateDefinition> templates = new ArrayList<>();
        List<TemplatedRouteDefinition> templated = new ArrayList<>();
        List<BeanFactoryDefinition> beans = new ArrayList<>();
        switch (root) {
            case "camel", "beans" -> {
                BeansDefinition b = parser.parseBeansDefinition().orElse(null);
                if (b != null) {
                    routes.addAll(b.getRoutes());
                    rests.addAll(b.getRests());
                    configurations.addAll(b.getRouteConfigurations());
                    templates.addAll(b.getRouteTemplates());
                    templated.addAll(b.getTemplatedRoutes());
                    beans.addAll(b.getBeans());
                }
            }
            case "routes", "route" -> parser.parseRoutesDefinition().ifPresent(r -> routes.addAll(r.getRoutes()));
            case "rests", "rest" -> parser.parseRestsDefinition().ifPresent(r -> rests.addAll(r.getRests()));
            case "routeConfigurations", "routeConfiguration" -> parser.parseRouteConfigurationsDefinition()
                    .ifPresent(c -> configurations.addAll(c.getRouteConfigurations()));
            case "routeTemplates", "routeTemplate" -> parser.parseRouteTemplatesDefinition()
                    .ifPresent(t -> templates.addAll(t.getRouteTemplates()));
            case "templatedRoutes", "templatedRoute" -> parser.parseTemplatedRoutesDefinition()
                    .ifPresent(t -> templated.addAll(t.getTemplatedRoutes()));
            default -> {
                return "<" + root + "> is no Camel XML DSL root";
            }
        }
        routes.forEach(d -> d.setResource(resource));
        rests.forEach(d -> d.setResource(resource));
        configurations.forEach(d -> d.setResource(resource));
        templates.forEach(d -> d.setResource(resource));
        context.addRouteConfigurations(configurations);
        context.addRestDefinitions(rests, false);
        context.addRouteTemplateDefinitions(templates);
        context.addRouteDefinitions(routes);
        Model model = context.getCamelContextExtension().getContextPlugin(Model.class);
        for (BeanFactoryDefinition bean : beans) {
            bean.setResource(resource);
            model.addCustomBean(bean);
        }
        if (!templated.isEmpty()) {
            return "templated routes cannot be converted yet";
        }
        return null;
    }

    private static final Pattern YAML_SEMANTIC = Pattern.compile("(?m)^-\\s+semantic\\s*:");
    private static final Pattern JAVA_SEMANTIC = Pattern.compile("\\bsemanticQuestions\\s*\\(");

    // they live outside the route model the writers export: converting would lose them without a word
    private static final String SEMANTIC_REFUSED
            = "semantic declarations cannot be converted with the routes: keep them in a separate declaration resource"
              + " and convert only the routes";

    private static String loadYaml(DefaultCamelContext context, String content, Resource resource) throws Exception {
        if (YAML_SEMANTIC.matcher(content).find()) {
            return SEMANTIC_REFUSED;
        }
        // the beans are taken out and read as definitions: the YAML DSL would create them, running code of the project
        StringBuilder routes = new StringBuilder();
        StringBuilder beans = new StringBuilder();
        boolean inBeans = false;
        for (String line : content.split("\n", -1)) {
            if (line.startsWith("-")) {
                inBeans = YAML_BEANS.matcher(line).find();
            }
            (inBeans ? beans : routes).append(line).append('\n');
        }
        if (!routes.toString().isBlank()) {
            Resource r = ResourceHelper.fromString(resource.getLocation(), routes.toString());
            PluginHelper.getRoutesLoader(context).loadRoutes(r);
        }
        if (!beans.isEmpty()) {
            Model model = context.getCamelContextExtension().getContextPlugin(Model.class);
            for (BeanFactoryDefinition bean : yamlBeans(beans.toString())) {
                bean.setResource(resource);
                model.addCustomBean(bean);
            }
        }
        return null;
    }

    /** The beans of the YAML beans entries as definitions, read as plain data: no class is loaded or created. */
    @SuppressWarnings("unchecked")
    static List<BeanFactoryDefinition> yamlBeans(String yaml) {
        List<BeanFactoryDefinition> found = new ArrayList<>();
        Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        if (!(root instanceof List<?> entries)) {
            return found;
        }
        for (Object entry : entries) {
            if (entry instanceof Map<?, ?> m && m.get("beans") instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> b) {
                        found.add(bean((Map<String, Object>) b));
                    }
                }
            }
        }
        return found;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static BeanFactoryDefinition bean(Map<String, Object> b) {
        BeanFactoryDefinition def = new BeanFactoryDefinition();
        def.setName(string(b.get("name")));
        def.setType(string(b.get("type")));
        def.setInitMethod(string(b.get("initMethod")));
        def.setDestroyMethod(string(b.get("destroyMethod")));
        def.setFactoryMethod(string(b.get("factoryMethod")));
        def.setFactoryBean(string(b.get("factoryBean")));
        def.setBuilderClass(string(b.get("builderClass")));
        def.setBuilderMethod(string(b.get("builderMethod")));
        def.setScriptLanguage(string(b.get("scriptLanguage")));
        def.setScript(string(b.get("script")));
        if (b.get("properties") instanceof Map<?, ?> p) {
            def.setProperties(new LinkedHashMap<>((Map<String, Object>) p));
        }
        if (b.get("constructors") instanceof Map<?, ?> c) {
            Map<Integer, Object> constructors = new LinkedHashMap<>();
            ((Map) c).forEach((k, v) -> constructors.put(Integer.valueOf(String.valueOf(k)), v));
            def.setConstructors(constructors);
        }
        return def;
    }

    private static String string(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    /** The model of the context written in the format, as the route dump of a running application writes it. */
    private static String dump(DefaultCamelContext context, String format) throws IOException {
        Path dir = Files.createTempDirectory("camel-convert");
        try {
            Path target = dir.resolve("converted." + format);
            DefaultDumpRoutesStrategy dumper = new DefaultDumpRoutesStrategy();
            dumper.setCamelContext(context);
            dumper.setInclude("routes,rests,routeConfigurations,routeTemplates,beans,dataFormats");
            dumper.setLog(false);
            dumper.setResolvePlaceholders(false);
            dumper.setUriAsParameters(false);
            dumper.setOutput(target.toString());
            dumper.dumpRoutes(format);
            return Files.isRegularFile(target) ? Files.readString(target, StandardCharsets.UTF_8) : "";
        } finally {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private static final Pattern XML_ROOT = Pattern.compile("<([A-Za-z_][\\w.-]*:)?([A-Za-z_][\\w.-]*)[\\s>/]");

    /** The local name of the root element of an XML document, past its prolog and comments. */
    static String xmlRoot(String content) {
        Matcher m = XML_ROOT.matcher(content.replaceAll("(?s)<\\?.*?\\?>|<!--.*?-->|<!DOCTYPE[^>]*>", ""));
        return m.find() ? m.group(2) : null;
    }

    private static final Pattern XMLNS = Pattern.compile("\\sxmlns=\"([^\"]*)\"");

    private static String xmlNamespace(String content) {
        Matcher m = XMLNS.matcher(content);
        return m.find() ? m.group(1) : "";
    }

    /** The Java sources of the file's folder, for the constants a route takes from another class. */
    private static Map<String, Supplier<String>> javaSources(Path file) throws IOException {
        Map<String, Supplier<String>> answer = new LinkedHashMap<>();
        Path dir = file.toAbsolutePath().getParent();
        if (dir == null) {
            return answer;
        }
        try (Stream<Path> list = Files.list(dir)) {
            list.filter(p -> p.getFileName().toString().endsWith(".java"))
                    .forEach(p -> answer.putIfAbsent(p.toString(), () -> {
                        try {
                            return Files.readString(p, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            return null;
                        }
                    }));
        }
        return answer;
    }
}
