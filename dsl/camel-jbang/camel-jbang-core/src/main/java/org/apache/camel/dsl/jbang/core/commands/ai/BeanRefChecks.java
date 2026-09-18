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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.SourceValidator.BeanDeclarations;
import org.apache.camel.tooling.model.EipModel;

import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.YAML_URI_PATTERN;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.countLeadingSpaces;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.unquote;

/**
 * The bean and resource reference checks of {@link SourceValidator}: every bean the YAML refers to is declared,
 * implements what the option needs, and names a method when it has several; every stylesheet or template file exists.
 */
final class BeanRefChecks {

    private BeanRefChecks() {
    }

    static final Pattern BEAN_NAME_PATTERN = Pattern.compile("^\\s*-?\\s*name:\\s*(\\S+)\\s*$");

    static final Pattern BEAN_TYPE_PATTERN = Pattern.compile("^\\s*type:\\s*[\"']?#class:([\\w.$]+)");

    /** The beans declared under {@code beans:} with a {@code #class:} type, name to fully qualified class name. */
    static Map<String, String> declaredBeanTypes(String content) {
        Map<String, String> types = new LinkedHashMap<>();
        if (content == null) {
            return types;
        }
        String last = null;
        for (String line : content.split("\n", -1)) {
            Matcher nm = BEAN_NAME_PATTERN.matcher(line);
            if (nm.find()) {
                last = unquote(nm.group(1));
                continue;
            }
            Matcher tm = BEAN_TYPE_PATTERN.matcher(line);
            if (tm.find() && last != null) {
                types.put(last, tm.group(1));
            }
        }
        return types;
    }

    private static final Map<CamelCatalog, Map<String, String>> REQUIRED_TYPES_BY_CATALOG
            = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final Map<CamelCatalog, Pattern> BEAN_REF_PATTERN_BY_CATALOG
            = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * The interface the bean of an option must implement, from the EIP models of the catalog: every object option whose
     * javaType is a Camel interface (aggregationStrategy needs org.apache.camel.AggregationStrategy,
     * idempotentRepository needs org.apache.camel.spi.IdempotentRepository, ...), the same metadata the visual editors
     * use to offer the built-in beans.
     */
    static String requiredType(CamelCatalog catalog, String option) {
        return requiredTypes(catalog).get(option);
    }

    private static Map<String, String> requiredTypes(CamelCatalog catalog) {
        return REQUIRED_TYPES_BY_CATALOG.computeIfAbsent(catalog, c -> {
            Map<String, String> answer = new java.util.HashMap<>();
            for (String name : c.findModelNames()) {
                EipModel model = c.eipModel(name);
                if (model == null) {
                    continue;
                }
                for (var o : model.getOptions()) {
                    String jt = o.getJavaType();
                    if ("object".equals(o.getType()) && jt != null && jt.startsWith("org.apache.camel.")
                            && !jt.contains(".model.") && !"ref".equals(o.getName())) {
                        answer.putIfAbsent(o.getName(), jt);
                    }
                }
            }
            return answer;
        });
    }

    /** The options whose value is a bean name whatever the catalog says: ref, bean and the *Ref options. */
    private static final List<String> REF_OPTIONS = List.of("ref", "aggregationStrategy", "loadBalancerRef",
            "executorServiceRef", "onPrepareRef", "onRedeliveryRef", "aggregationRepositoryRef", "comparatorRef", "bean");

    /**
     * The lines that reference a bean: option: name, for {@link #REF_OPTIONS} and every option the catalog's EIP models
     * type with a Camel interface.
     */
    static Pattern beanRefPattern(CamelCatalog catalog) {
        return BEAN_REF_PATTERN_BY_CATALOG.computeIfAbsent(catalog, c -> {
            Set<String> names = new TreeSet<>(requiredTypes(c).keySet());
            names.addAll(REF_OPTIONS);
            return Pattern.compile("^\\s*-?\\s*(" + String.join("|", names) + "):\\s*(\\S+)\\s*$");
        });
    }

    /** The bean names declared under {@code beans:} in the YAML content. */
    public static Set<String> declaredBeans(String content) {
        Set<String> names = new HashSet<>();
        if (content == null) {
            return names;
        }
        String[] lines = content.split("\n", -1);
        int blockIndent = -1;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            int indent = countLeadingSpaces(line);
            if (blockIndent >= 0 && indent <= blockIndent) {
                blockIndent = -1;
            }
            if (blockIndent < 0) {
                if (trimmed.equals("- beans:") || trimmed.equals("beans:")) {
                    blockIndent = indent;
                }
                continue;
            }
            Matcher m = BEAN_NAME_PATTERN.matcher(line);
            if (m.find()) {
                names.add(unquote(m.group(1)));
            }
        }
        return names;
    }

    /**
     * Bean references in the YAML that nothing declares, each with how to declare it. A reference that is a
     * {@code #class:}, {@code #type:} or {@code #bean:} value, a property placeholder, or a class name is left alone.
     */
    static final Pattern SIMPLE_BEAN_FUNCTION = Pattern.compile("\\$\\{bean:([A-Za-z_][\\w-]*)");

    public static List<String> validateYamlBeanRefs(String content, BeanDeclarations external, CamelCatalog catalog) {
        List<String> msgs = new ArrayList<>();
        if (content == null) {
            return msgs;
        }
        Set<String> declared = new HashSet<>(declaredBeans(content));
        if (external != null) {
            declared.addAll(external.names());
        }
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            // ${bean:name.method} in a simple expression is a reference too; the catalog cannot check it
            Matcher bm = SIMPLE_BEAN_FUNCTION.matcher(lines[i]);
            while (bm.find()) {
                String ref = bm.group(1);
                int after = bm.end();
                if (after < lines[i].length() && lines[i].charAt(after) == ':'
                        && !(after + 1 < lines[i].length() && lines[i].charAt(after + 1) == ':')) {
                    // ${bean:name:method}: the method is not separated by a colon
                    String method = lines[i].substring(after + 1).split("[^\\w]", 2)[0];
                    msgs.add("Line " + (i + 1) + ": ${bean:" + ref + ":" + method + "}: the method is written as ${bean:" + ref
                             + "." + method + "}, ${bean:" + ref + "?method=" + method + "} or ${bean:" + ref + "::" + method
                             + "}, not with a single colon (the whole '" + ref + ":" + method
                             + "' would be looked up as the bean name)");
                    continue;
                }
                if (!declared.contains(ref)) {
                    addUndeclared(msgs, i, "${bean:" + ref + "}", ref, external);
                }
            }
            Matcher tm = BEAN_TYPE_PATTERN.matcher(lines[i]);
            if (tm.find()) {
                String missing = classNotFound(tm.group(1), external);
                if (missing != null) {
                    msgs.add("Line " + (i + 1) + ": type: " + missing);
                }
                continue;
            }
            Matcher m = beanRefPattern(catalog).matcher(lines[i]);
            if (!m.find()) {
                continue;
            }
            String option = m.group(1);
            String ref = unquote(m.group(2));
            if (ref.isEmpty() || ref.contains("{{") || ref.contains("${") || ref.startsWith("&") || ref.startsWith("*")
                    || (ref.contains(":") && !ref.startsWith("#class:"))) {
                continue;
            }
            String required = requiredType(catalog, option);
            if (ref.startsWith("#class:")) {
                String missing = classNotFound(ref.substring("#class:".length()), external);
                if (missing != null) {
                    msgs.add("Line " + (i + 1) + ": " + option + ": " + missing
                             + (required != null ? builtInHint(catalog, required) : ""));
                }
            }
            if (required != null && external != null) {
                // a class is given or declared: does it implement what the option needs
                String fqcn
                        = ref.startsWith("#class:") ? ref.substring("#class:".length()) : declaredBeanTypes(content).get(ref);
                if (fqcn != null) {
                    String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
                    String signature = external.javaSignatures().get(simple);
                    String requiredSimple = required.substring(required.lastIndexOf('.') + 1);
                    if (signature != null && !signature.contains(requiredSimple)) {
                        Integer methods = external.javaPublicMethods().get(simple);
                        if (option.equals("aggregationStrategy") && methods != null && methods == 1) {
                            // a POJO with one public method is adapted by the runtime
                            continue;
                        }
                        if (option.equals("aggregationStrategy") && methods != null && methods > 1) {
                            if (!content.contains("aggregationStrategyMethodName")) {
                                msgs.add("Line " + (i + 1) + ": " + option + ": " + fqcn + " does not implement "
                                         + required + " and has " + methods + " public methods: the runtime"
                                         + " cannot pick one, set aggregationStrategyMethodName: <method> or implement"
                                         + " the interface");
                            }
                            continue;
                        }
                        msgs.add("Line " + (i + 1) + ": " + option + ": " + fqcn + " must implement " + required
                                 + " (its declaration is: " + signature.replaceAll("\\s+", " ").trim() + ")"
                                 + (option.equals("aggregationStrategy")
                                         ? ", or be a POJO with one public method such as append(String a, String b)"
                                         : "")
                                 + builtInHint(catalog, required));
                    }
                }
            }
            if ((option.equals("ref") || option.equals("bean")) && external != null && !hasMethodNearby(lines, i)) {
                // - bean: {ref: x} on a class with several public methods: the runtime cannot pick one
                String fqcn
                        = ref.startsWith("#class:") ? ref.substring("#class:".length()) : declaredBeanTypes(content).get(ref);
                if (fqcn != null) {
                    String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
                    List<String> methodNames = external.javaPublicMethodNames().get(simple);
                    if (methodNames != null && methodNames.size() > 1 && isBeanStep(lines, i)) {
                        msgs.add("Line " + (i + 1) + ": bean " + ref + " has " + methodNames.size() + " public methods ("
                                 + String.join(", ", methodNames) + ") and the step names none: add method: <name>"
                                 + " next to ref:, or annotate the one to call with @Handler");
                    }
                }
            }
            int dot = ref.lastIndexOf('.');
            if (dot > 0 && dot < ref.length() - 1 && Character.isUpperCase(ref.charAt(dot + 1))) {
                // a fully qualified class name
                continue;
            }
            if (ref.startsWith("#")) {
                continue;
            }
            if (declared.contains(ref)) {
                continue;
            }
            addUndeclared(msgs, i, option, ref, external);
        }
        return msgs;
    }

    /** "; the built-in ones are StringAggregationStrategy (org...), ..." from the catalog's bean metadata, or empty. */
    static String builtInHint(CamelCatalog catalog, String interfaceName) {
        List<String> beans = CatalogDocs.beansOfInterface(catalog, interfaceName);
        if (beans.isEmpty()) {
            return "";
        }
        List<String> shown = beans.size() > 6 ? beans.subList(0, 6) : beans;
        return "; the built-in ones are " + String.join(", ", shown)
               + (beans.size() > 6
                       ? " and " + (beans.size() - 6) + " more (camel_catalog_find with kind bean lists them)" : "");
    }

    /** Whether a method: line sits in the same block as the ref: line at index i (same indentation, adjacent). */
    static boolean hasMethodNearby(String[] lines, int i) {
        int indent = indentOf(lines[i]);
        for (int d = -1; d <= 1; d += 2) {
            for (int j = i + d; j >= 0 && j < lines.length; j += d) {
                if (lines[j].isBlank()) {
                    continue;
                }
                int ind = indentOf(lines[j]);
                if (ind < indent || lines[j].trim().startsWith("- ") && ind <= indent) {
                    break;
                }
                if (ind == indent && lines[j].trim().startsWith("method:")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether the ref: line at index i belongs to a - bean: step (the line above is the bean: key). */
    static boolean isBeanStep(String[] lines, int i) {
        if (lines[i].trim().startsWith("bean:")) {
            return true;
        }
        for (int j = i - 1; j >= 0; j--) {
            if (lines[j].isBlank()) {
                continue;
            }
            if (indentOf(lines[j]) < indentOf(lines[i])) {
                return lines[j].trim().matches("-?\\s*bean:\\s*");
            }
            if (lines[j].trim().startsWith("- ")) {
                return lines[j].trim().matches("-\\s*bean:\\s*");
            }
        }
        return false;
    }

    static int indentOf(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    /**
     * The classes camel run resolves to a Maven dependency and downloads on demand (camel-kamelet-main's
     * camel-main-known-dependencies.properties, camel-component-known-dependencies.properties and the generated
     * camel-thirdparty-known-dependencies.properties of CAMEL-24809), so a #class:org.postgresql.ds.PGSimpleDataSource
     * bean is fine without a dependency declared even though the class is not on the CLI classpath. Matched the way the
     * runtime matches: the class name, then each enclosing package.
     */
    /**
     * Lazily loaded without a lock, on purpose: two threads that both find it null both build the same immutable-in-use
     * map from the same resources and both publish it through the volatile write; the last one wins and every caller
     * sees a complete map. A benign race, cheaper than synchronising every lookup.
     */
    private static volatile Map<String, String> knownDependencies;

    static String knownDependency(String fqcn) {
        Map<String, String> known = knownDependencies;
        if (known == null) {
            known = loadKnownDependencies(BeanRefChecks.class.getClassLoader());
            knownDependencies = known;
        }
        return findKnown(known, fqcn);
    }

    /**
     * The lookup against the mapping files of one class loader, uncached; a loader without the files answers nothing.
     */
    static String knownDependency(String fqcn, ClassLoader loader) {
        return findKnown(loadKnownDependencies(loader), fqcn);
    }

    private static Map<String, String> loadKnownDependencies(ClassLoader loader) {
        Map<String, String> known = new HashMap<>();
        {
            for (String name : new String[] {
                    "camel-main-known-dependencies.properties", "camel-component-known-dependencies.properties",
                    "camel-thirdparty-known-dependencies.properties" }) {
                try {
                    Enumeration<URL> resources = loader.getResources(name);
                    while (resources.hasMoreElements()) {
                        try (InputStream is = resources.nextElement().openStream()) {
                            Properties prop = new Properties();
                            prop.load(is);
                            for (String key : prop.stringPropertyNames()) {
                                known.put(key, prop.getProperty(key));
                            }
                        }
                    }
                } catch (Exception e) {
                    // the mapping is an optimisation of the message, not a requirement
                }
            }
        }
        return known;
    }

    private static String findKnown(Map<String, String> known, String fqcn) {
        String prefix = fqcn;
        String gav = known.get(prefix);
        while (gav == null && prefix.lastIndexOf('.') != -1) {
            prefix = prefix.substring(0, prefix.lastIndexOf('.'));
            gav = known.get(prefix);
        }
        return gav;
    }

    /**
     * A class named with its package that is neither next to the route, nor on the CLI classpath, nor one camel run
     * downloads: the wrong package (org.apache.camel.support.StringAggregationStrategy) or a missing dependency. Null
     * when the class is fine.
     */
    static String classNotFound(String fqcn, BeanDeclarations external) {
        if (external == null || external == BeanDeclarations.NONE
                || fqcn == null || fqcn.contains("{{") || fqcn.contains("${")) {
            // without the directory the class may well be next to the route: do not judge
            return null;
        }
        if (fqcn.contains("$")) {
            // Outer$Inner: camel run compiles the files next to the route as top-level classes
            String outer = fqcn.substring(0, fqcn.indexOf('$'));
            String inner = fqcn.substring(fqcn.indexOf('$') + 1);
            return "class " + fqcn + " is an inner class of " + outer + ": a bean type must be a top-level class, move "
                   + inner + " to its own file " + inner + ".java next to the route and write #class:" + inner;
        }
        if (!fqcn.contains(".")) {
            return null;
        }
        String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
        if (external != null && external.javaClasses().containsKey(simple.toLowerCase(Locale.ROOT))) {
            return null;
        }
        try {
            Class.forName(fqcn, false, SourceValidator.class.getClassLoader());
            return null;
        } catch (Throwable e) {
            // not on the classpath
        }
        if (knownDependency(fqcn) != null) {
            // camel run downloads the dependency for this class; the runtime and the validator must agree
            return null;
        }
        String hint = "";
        if (simple.endsWith("AggregationStrategy") && !fqcn.startsWith("org.apache.camel.processor.aggregate.")) {
            String candidate = "org.apache.camel.processor.aggregate." + simple;
            try {
                Class.forName(candidate, false, SourceValidator.class.getClassLoader());
                hint = " (did you mean " + candidate + "?)";
            } catch (Throwable e) {
                // not a built-in strategy
            }
        }
        if (hint.isEmpty()) {
            hint = " (check the package name; a class of your own goes in a .java file next to the route; a class from"
                   + " another library needs its dependency declared, camel.jbang.dependencies=<groupId>:<artifactId>:<version>"
                   + " in application.properties or --dep on camel run)";
        }
        return "class " + fqcn + " was not found" + hint;
    }

    static void addUndeclared(List<String> msgs, int i, String option, String ref, BeanDeclarations external) {
        String fqcn = external != null ? external.javaClasses().get(ref.toLowerCase(Locale.ROOT)) : null;
        if (fqcn != null) {
            String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
            msgs.add("Line " + (i + 1) + ": " + option + ": bean '" + ref + "' is not declared: " + simple
                     + ".java is in the directory, but a Java class is not a bean until it is declared; add\n"
                     + "- beans:\n  - name: " + ref + "\n    type: \"#class:" + fqcn + "\"\n"
                     + (option.startsWith("${bean:")
                             ? "" : "or reference the class directly: " + option + ": \"#class:" + fqcn + "\""));
        } else {
            msgs.add("Line " + (i + 1) + ": " + option + ": bean '" + ref + "' is not declared in this file or its"
                     + " directory: declare it under - beans: (name: " + ref
                     + ", type: \"#class:<fully qualified class>\")"
                     + (option.startsWith("${bean:")
                             ? "" : ", or reference the class directly with #class:<fully qualified class>"));
        }
    }

    /** The endpoint URIs of a YAML route (uri plus a parameters: map) checked against the catalog. */
    /** Components whose endpoint path is a file next to the route (a stylesheet, a template). */
    static final Set<String> RESOURCE_SCHEMES
            = Set.of("xslt", "xslt-saxon", "velocity", "freemarker", "mustache", "jslt", "thymeleaf", "string-template",
                    "chunk", "language");

    /**
     * An xslt:stylesheets/x.xsl (or velocity, freemarker...) whose file is not in the directory fails when the route
     * starts; with camel run the files next to the route are on the classpath root, so the endpoint names the file
     * without a folder. Says what is missing and, when a file of that name is in the directory, the endpoint to write.
     */
    public static List<String> validateResourceRefs(String content, Path directory) {
        List<String> errors = new ArrayList<>();
        if (content == null || directory == null || !Files.isDirectory(directory)) {
            return errors;
        }
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = YAML_URI_PATTERN.matcher(lines[i]);
            if (!m.find() || lines[i].trim().startsWith("#")) {
                continue;
            }
            String uri = m.group(1);
            if (uri.endsWith("\"")) {
                uri = uri.substring(0, uri.length() - 1);
            }
            int colon = uri.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String scheme = uri.substring(0, colon);
            if (!RESOURCE_SCHEMES.contains(scheme)) {
                continue;
            }
            String path = uri.substring(colon + 1);
            if (path.startsWith("//")) {
                path = path.substring(2);
            }
            int q = path.indexOf('?');
            if (q >= 0) {
                path = path.substring(0, q);
            }
            if (scheme.equals("language")) {
                // language:xpath:resource:classpath:foo.xpath or language:xslt:file:foo.xsl
                int c = path.indexOf(':');
                if (c < 0) {
                    continue;
                }
                path = path.substring(c + 1);
                if (path.startsWith("resource:")) {
                    path = path.substring("resource:".length());
                } else {
                    continue;
                }
            }
            if (path.startsWith("file:")) {
                path = path.substring("file:".length());
            } else if (path.startsWith("classpath:")) {
                path = path.substring("classpath:".length());
            } else if (path.contains(":") || path.startsWith("{{") || path.isBlank()) {
                continue; // http:, ref:, bean:, a placeholder
            }
            if (Files.exists(directory.resolve(path))) {
                continue;
            }
            String base = path.substring(path.lastIndexOf('/') + 1);
            String hint;
            if (!base.isEmpty() && Files.exists(directory.resolve(base))) {
                hint = " (the directory has " + base + ": the files next to the route are found by name, write "
                       + scheme + ":" + base + ")";
            } else {
                hint = " (add the file next to the route files and name it by file name alone: " + scheme + ":<name>)";
            }
            errors.add("Line " + (i + 1) + ": " + scheme + ": the file " + path + " does not exist in the directory"
                       + hint);
        }
        return errors;
    }

}
