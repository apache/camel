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
package org.apache.camel.java.out;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.xml.in.ModelParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Reads XML route definitions from camel-xml-io test resources, converts them to Java DSL using
 * {@link JavaDslModelWriter}, and compiles the result to verify syntactic correctness.
 *
 * Files that use constructs the writer doesn't yet support (data formats, load-balancer subtypes, process refs, etc.)
 * are listed in {@link #KNOWN_FAILURES} and skipped. As the writer improves, move files out of that set.
 */
public class JavaDslCompileTest {

    private static final Logger LOG = LoggerFactory.getLogger(JavaDslCompileTest.class);

    private static final String NAMESPACE = "http://camel.apache.org/schema/xml-io";

    // Non-route XML files: REST, templates, beans, configurations
    private static final Set<String> NON_ROUTE_FILES = Set.of(
            "barRest.xml", "simpleRest.xml", "simpleRestToD.xml", "restAllowedValues.xml",
            "barTemplate.xml", "barTemplatedRoute.xml", "barRestConfiguration.xml",
            "errorHandlerConfiguration.xml", "errorHandlerConfigurationRedeliveryPolicyRef.xml");

    // REST XML files to test (subset of NON_ROUTE_FILES that contain <rests>)
    private static final Set<String> REST_FILES = Set.of(
            "barRest.xml", "simpleRest.xml");

    // Files that don't compile yet due to unsupported constructs in the writer.
    // Categorized by root cause — as the writer improves, move files out of this set.
    private static final Set<String> KNOWN_FAILURES = Set.of();

    private static final Path XML_IO_RESOURCES = Paths.get("../camel-xml-io/src/test/resources");
    private static final Path LOCAL_RESOURCES = Paths.get("src/test/resources");

    static Stream<String> xmlRouteFiles() throws Exception {
        List<String> files = new ArrayList<>();
        collectXmlRouteFiles(XML_IO_RESOURCES, files);
        collectXmlRouteFiles(LOCAL_RESOURCES, files);
        return files.stream().sorted();
    }

    private static void collectXmlRouteFiles(Path dir, List<String> result) throws Exception {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> list = Files.list(dir)) {
            list.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".xml"))
                    .filter(n -> !n.startsWith("beans"))
                    .filter(n -> !NON_ROUTE_FILES.contains(n))
                    .forEach(result::add);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("xmlRouteFiles")
    void xmlToJavaDslCompiles(String xmlFile) throws Exception {
        assumeFalse(KNOWN_FAILURES.contains(xmlFile),
                "Skipping known failure: " + xmlFile);

        Path xmlPath = LOCAL_RESOURCES.resolve(xmlFile);
        if (!Files.exists(xmlPath)) {
            xmlPath = XML_IO_RESOURCES.resolve(xmlFile);
        }

        ModelParser parser = new ModelParser(Files.newInputStream(xmlPath), NAMESPACE);
        RoutesDefinition routesDef = parser.parseRoutesDefinition().orElse(null);
        if (routesDef == null || routesDef.getRoutes() == null || routesDef.getRoutes().isEmpty()) {
            LOG.info("Skipping {} - no routes parsed", xmlFile);
            return;
        }

        JavaDslModelWriter writer = new JavaDslModelWriter();
        List<String> routeSnippets = new ArrayList<>();
        for (RouteDefinition route : routesDef.getRoutes()) {
            String javaDsl = writer.writeRouteDefinition(route);
            routeSnippets.add(javaDsl);
        }

        String className = toClassName(xmlFile);
        String source = wrapInRouteBuilder(className, routeSnippets);

        LOG.debug("Generated Java DSL for {}:\n{}", xmlFile, source);

        List<String> errors = compile(className, source);
        assertTrue(errors.isEmpty(),
                "Compilation failed for " + xmlFile + ":\n" + String.join("\n", errors)
                                     + "\n\nGenerated source:\n" + source);
    }

    static Stream<String> xmlRestFiles() {
        return REST_FILES.stream().sorted();
    }

    @ParameterizedTest(name = "rest:{0}")
    @MethodSource("xmlRestFiles")
    void xmlToRestJavaDslCompiles(String xmlFile) throws Exception {
        Path xmlPath = XML_IO_RESOURCES.resolve(xmlFile);

        ModelParser parser = new ModelParser(Files.newInputStream(xmlPath), NAMESPACE);
        RestsDefinition restsDef = parser.parseRestsDefinition().orElse(null);
        if (restsDef == null || restsDef.getRests() == null || restsDef.getRests().isEmpty()) {
            LOG.info("Skipping {} - no rests parsed", xmlFile);
            return;
        }

        JavaDslModelWriter writer = new JavaDslModelWriter();
        List<String> restSnippets = new ArrayList<>();
        for (RestDefinition rest : restsDef.getRests()) {
            String javaDsl = writer.writeRest(rest);
            restSnippets.add(javaDsl);
        }

        String className = toClassName(xmlFile);
        String source = wrapInRouteBuilder(className, restSnippets);

        LOG.debug("Generated REST Java DSL for {}:\n{}", xmlFile, source);

        List<String> errors = compile(className, source);
        assertTrue(errors.isEmpty(),
                "Compilation failed for " + xmlFile + ":\n" + String.join("\n", errors)
                                     + "\n\nGenerated source:\n" + source);
    }

    private static String toClassName(String xmlFile) {
        String base = xmlFile.replace(".xml", "");
        StringBuilder sb = new StringBuilder("Route_");
        boolean upper = true;
        for (char c : base.toCharArray()) {
            if (c == '-' || c == '_' || c == '.') {
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String wrapInRouteBuilder(String className, List<String> routeSnippets) {
        StringBuilder sb = new StringBuilder();
        sb.append("package test.generated;\n\n");
        sb.append("import org.apache.camel.ExchangePattern;\n");
        sb.append("import org.apache.camel.LoggingLevel;\n");
        sb.append("import org.apache.camel.Predicate;\n");
        sb.append("import org.apache.camel.builder.RouteBuilder;\n");
        sb.append("import org.apache.camel.model.ClaimCheckOperation;\n");
        sb.append("import org.apache.camel.model.tokenizer.LangChain4jTokenizerDefinition;\n");
        sb.append("import static org.apache.camel.builder.Builder.language;\n\n");
        sb.append("public class ").append(className).append(" extends RouteBuilder {\n");
        sb.append("    @Override\n");
        sb.append("    public void configure() throws Exception {\n");
        for (String snippet : routeSnippets) {
            sb.append("        ");
            sb.append(snippet.replace("\n", "\n        "));
            sb.append("\n\n");
        }
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static List<String> compile(String className, String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return List.of("No Java compiler available (need JDK, not JRE)");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject file = new InMemoryJavaFileObject("test.generated." + className, source);

        String classpath = System.getProperty("java.class.path");
        List<String> options = Arrays.asList("-classpath", classpath);

        // Use a file manager that discards .class output so no files leak to disk
        JavaFileManager stdFm = compiler.getStandardFileManager(diagnostics, null, null);
        JavaFileManager discardingFm = new ForwardingJavaFileManager<>(stdFm) {
            @Override
            public JavaFileObject getJavaFileForOutput(
                    Location location, String name, JavaFileObject.Kind kind, FileObject sibling)
                    throws IOException {
                return new SimpleJavaFileObject(URI.create("mem:///" + name), kind) {
                    @Override
                    public java.io.OutputStream openOutputStream() {
                        return java.io.OutputStream.nullOutputStream();
                    }
                };
            }
        };

        JavaCompiler.CompilationTask task = compiler.getTask(
                new StringWriter(), discardingFm, diagnostics, options, null, List.of(file));

        boolean success = task.call();
        if (success) {
            return List.of();
        }

        List<String> errors = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() == Diagnostic.Kind.ERROR) {
                errors.add("Line " + d.getLineNumber() + ": " + d.getMessage(null));
            }
        }
        return errors;
    }

    private static class InMemoryJavaFileObject extends SimpleJavaFileObject {
        private final String code;

        InMemoryJavaFileObject(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension),
                  Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
