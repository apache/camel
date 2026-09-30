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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.java.in.JavaParseResult;
import org.apache.camel.java.in.LwJavaParser;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.app.BeansDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.xml.in.ModelParser;
import org.apache.camel.xml.io.XmlPullParserException;
import org.apache.camel.xml.io.XmlPullParserLocationException;

/**
 * The routes of a Java or XML DSL source read into the Camel model, with the line of each step, without compiling or
 * running anything: Java by the Java DSL parser of camel-java-io (the endpoint DSL and constants resolved through the
 * catalog), XML by the parser of camel-xml-io the XML DSL loads routes with. What could not be read is kept with its
 * line, so a tool can say which parts it did not check.
 * <p/>
 * The model is the same whatever the DSL, which is what lets one set of checks and one quick doc serve Java and XML
 * (CAMEL-25208).
 */
public final class RouteModel {

    /** The DSL of a source. */
    public enum Dsl {
        JAVA,
        XML
    }

    /**
     * A part of the source that was not read into the model.
     *
     * @param line   the line, 1-based; 0 when not known
     * @param text   the source text of the part, or null
     * @param reason why it was not read
     * @param error  whether the source is wrong there (an XML element the XML DSL does not have), rather than beyond
     *               what a parse can read (a lambda, a value computed at runtime)
     */
    public record Unread(int line, String text, String reason, boolean error) {
    }

    /** The root elements of a Camel XML DSL file, as the XML DSL loads them. */
    private static final List<String> XML_ROOTS = List.of(
            "camel", "beans", "routes", "route", "routeConfigurations", "routeConfiguration", "routeTemplates",
            "routeTemplate", "rests", "rest");

    /** The first element of an XML document, after the prolog, comments and doctype. */
    private static final Pattern XML_ROOT = Pattern.compile("<(?:[A-Za-z_][\\w.-]*:)?([A-Za-z_][\\w.-]*)([^>]*)>");
    private static final Pattern XML_NS = Pattern.compile("\\bxmlns\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final Pattern CAMEL_CONTENT
            = Pattern.compile("<(route|rest|routeConfiguration|routeTemplate|templatedRoute)[\\s>]");
    private static final Pattern XML_SKIPPED = Pattern.compile("(?s)<\\?.*?\\?>|<!--.*?-->|<!DOCTYPE[^>]*>");

    private final Dsl dsl;
    private final List<RouteDefinition> routes;
    private final List<RouteConfigurationDefinition> routeConfigurations;
    private final List<RestDefinition> rests;
    private final boolean templates;
    private final List<Unread> unread;

    private RouteModel(Dsl dsl, List<RouteDefinition> routes, List<RouteConfigurationDefinition> routeConfigurations,
                       List<RestDefinition> rests, boolean templates, List<Unread> unread) {
        this.dsl = dsl;
        this.routes = routes;
        this.routeConfigurations = routeConfigurations;
        this.rests = rests;
        this.templates = templates;
        this.unread = unread;
    }

    public Dsl dsl() {
        return dsl;
    }

    /** The routes, and the routes of the route templates. */
    public List<RouteDefinition> routes() {
        return routes;
    }

    public List<RouteConfigurationDefinition> routeConfigurations() {
        return routeConfigurations;
    }

    public List<RestDefinition> rests() {
        return rests;
    }

    /**
     * Whether the source has route templates, or routes made from them: what the routes made from a template consume is
     * only known when it is used.
     */
    public boolean hasTemplates() {
        return templates;
    }

    /** What was not read, in source order. */
    public List<Unread> unread() {
        return unread;
    }

    /** The DSL of a file by its name and content, or null when it is not a Java or XML DSL source. */
    public static Dsl dslOf(String fileName, String content) {
        if (fileName == null || content == null) {
            return null;
        }
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".java")) {
            // a RouteBuilder, EndpointRouteBuilder, RouteConfigurationBuilder...
            return content.contains("RouteBuilder") || content.contains("RouteConfigurationBuilder") ? Dsl.JAVA : null;
        }
        if (name.endsWith(".xml")) {
            String[] root = xmlRoot(content);
            if (root == null || !XML_ROOTS.contains(root[0]) || !isCamelNamespace(root[1])) {
                return null;
            }
            // a beans root without a namespace is Camel's when it holds routes (a beans.xml of CDI does not)
            return !"beans".equals(root[0]) || !root[1].isEmpty() || CAMEL_CONTENT.matcher(content).find() ? Dsl.XML : null;
        }
        return null;
    }

    /**
     * Reads a Java or XML DSL source.
     *
     * @param  javaSources the Java sources of the project by path, for the constants of other classes; may be empty
     * @return             the model, or null when the file is not a Java or XML DSL source
     */
    public static RouteModel read(
            String fileName, String content, CamelCatalog catalog, Map<String, Supplier<String>> javaSources) {
        Dsl dsl = dslOf(fileName, content);
        if (dsl == Dsl.JAVA) {
            return readJava(content, catalog, javaSources);
        } else if (dsl == Dsl.XML) {
            return readXml(content);
        }
        return null;
    }

    private static RouteModel readJava(String content, CamelCatalog catalog, Map<String, Supplier<String>> javaSources) {
        JavaParseResult result = ProjectRoutes.parseJava(content, javaSources != null ? javaSources : Map.of(), catalog);
        List<RouteDefinition> routes = new ArrayList<>(result.routes().getRoutes());
        for (RouteTemplateDefinition t : result.routeTemplates().getRouteTemplates()) {
            if (t.getRoute() != null) {
                routes.add(t.getRoute());
            }
        }
        List<Unread> unread = new ArrayList<>();
        for (JavaParseResult.Unresolved u : result.unresolved()) {
            if (!JavaParseResult.configuresTheContext(u)) {
                // a statement that sets up the CamelContext (components, beans) is not a part of a route
                unread.add(new Unread(u.line(), u.text(), u.reason(), false));
            }
        }
        return new RouteModel(
                Dsl.JAVA, routes, result.routeConfigurations().getRouteConfigurations(),
                result.rests().getRests(), !result.routeTemplates().getRouteTemplates().isEmpty(), unread);
    }

    private static RouteModel readXml(String content) {
        String[] root = xmlRoot(content);
        String namespace = root != null ? root[1] : "";
        List<RouteDefinition> routes = new ArrayList<>();
        List<RouteConfigurationDefinition> configurations = new ArrayList<>();
        List<RestDefinition> rests = new ArrayList<>();
        List<Unread> unread = new ArrayList<>();
        boolean templates = false;
        try {
            ModelParser parser = new ModelParser(new StringReader(content), namespace);
            switch (root != null ? root[0] : "") {
                case "camel", "beans" -> {
                    BeansDefinition beans = parser.parseBeansDefinition().orElse(null);
                    if (beans != null) {
                        routes.addAll(beans.getRoutes());
                        for (RouteTemplateDefinition t : beans.getRouteTemplates()) {
                            if (t.getRoute() != null) {
                                routes.add(t.getRoute());
                            }
                        }
                        configurations.addAll(beans.getRouteConfigurations());
                        rests.addAll(beans.getRests());
                        templates = !beans.getRouteTemplates().isEmpty() || !beans.getTemplatedRoutes().isEmpty();
                    }
                }
                case "routeConfigurations", "routeConfiguration" -> parser.parseRouteConfigurationsDefinition()
                        .ifPresent(c -> configurations.addAll(c.getRouteConfigurations()));
                case "routeTemplates", "routeTemplate" -> {
                    var t = parser.parseRouteTemplatesDefinition().orElse(null);
                    if (t != null) {
                        for (RouteTemplateDefinition template : t.getRouteTemplates()) {
                            if (template.getRoute() != null) {
                                routes.add(template.getRoute());
                            }
                        }
                        templates = true;
                    }
                }
                case "rests", "rest" -> parser.parseRestsDefinition().ifPresent(r -> rests.addAll(r.getRests()));
                default -> parser.parseRoutesDefinition().ifPresent(r -> routes.addAll(r.getRoutes()));
            }
        } catch (XmlPullParserLocationException e) {
            // an element or attribute the XML DSL does not have: the XML DSL refuses the file the same way
            unread.add(new Unread(e.getLineNumber(), null, firstLine(e.getCause() != null ? e.getCause() : e), true));
        } catch (XmlPullParserException e) {
            unread.add(new Unread(Math.max(0, e.getLineNumber()), null, firstLine(e), true));
        } catch (Exception e) {
            unread.add(new Unread(0, null, firstLine(e), true));
        }
        return new RouteModel(Dsl.XML, routes, configurations, rests, templates, unread);
    }

    private static String firstLine(Throwable e) {
        String msg = String.valueOf(e.getMessage());
        int nl = msg.indexOf('\n');
        return nl > 0 ? msg.substring(0, nl).trim() : msg.trim();
    }

    /** The local name and the default namespace of the root element; null when there is none. */
    static String[] xmlRoot(String content) {
        String body = XML_SKIPPED.matcher(content).replaceAll("");
        Matcher m = XML_ROOT.matcher(body);
        if (!m.find()) {
            return null;
        }
        Matcher ns = XML_NS.matcher(m.group(2));
        return new String[] { m.group(1), ns.find() ? ns.group(1) : "" };
    }

    /**
     * Whether the root element is of the XML DSL: no namespace, or a Camel one. A Spring or Blueprint file keeps its
     * routes in a camelContext the XML DSL does not read, and a beans.xml of CDI is not about Camel.
     */
    private static boolean isCamelNamespace(String namespace) {
        return namespace.isEmpty() || namespace.contains("camel.apache.org");
    }

    /** Whether a value in the model is one the Java DSL parser did not know, written ?{source text}. */
    static boolean hasUnknownValue(String value) {
        if (value == null) {
            return false;
        }
        int i = value.indexOf(LwJavaParser.UNRESOLVED_PREFIX);
        while (i >= 0) {
            // ?{{key}} is a property placeholder after the ? of the options, not an unknown value
            if (i + 2 >= value.length() || value.charAt(i + 2) != '{') {
                return true;
            }
            i = value.indexOf(LwJavaParser.UNRESOLVED_PREFIX, i + 3);
        }
        return false;
    }
}
