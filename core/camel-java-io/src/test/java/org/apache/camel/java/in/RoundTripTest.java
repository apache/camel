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
package org.apache.camel.java.in;

import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.java.LwModelToJavaDumper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.xml.in.ModelParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The routes of the XML test corpus of camel-xml-io are dumped as Java, parsed back, and dumped again: the two dumps
 * must be the same, so the parser gives the model the routes had (CAMEL-25148). XML routes are the reference because
 * every expression in them is a language; routes built in Java with a ValueBuilder have no Java form the dumper can
 * write.
 */
class RoundTripTest {

    /** XML routes of the core modules' tests: camel-xml-io's parser tests, camel-core's model tests, and ours. */
    private static final List<Path> CORPUS = List.of(
            Path.of("../camel-xml-io/src/test/resources"),
            Path.of("../camel-core/src/test/resources/org/apache/camel/model"),
            Path.of("src/test/resources"));

    /**
     * Routes that do not read back yet, and why. Each is a known gap of the parser or of the Java the dumper writes;
     * the list only shrinks.
     */
    private static final Map<String, String> KNOWN = Map.of(
            "circuitBreakerResilience4j.xml", "failureRateThreshold(float) keeps 30 as 30.0: the same value, written apart");

    static String dump(RouteDefinition route) {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            return new LwModelToJavaDumper().dumpModelAsJava(context, route);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<RouteDefinition> routes(Path file) {
        for (String ns : List.of("http://camel.apache.org/schema/xml-io", "http://camel.apache.org/schema/spring")) {
            try (InputStream in = Files.newInputStream(file)) {
                RoutesDefinition routes = new ModelParser(in, ns).parseRoutesDefinition().orElse(null);
                if (routes != null && !routes.getRoutes().isEmpty()) {
                    return routes.getRoutes();
                }
            } catch (Exception e) {
                // not a routes file in this namespace
            }
        }
        return List.of();
    }

    @Test
    void xmlCorpus() throws Exception {
        assumeTrue(Files.isDirectory(CORPUS.get(0)), "the core test routes are next to this module");
        List<String> failures = new ArrayList<>();
        int routes = 0;
        List<Path> files = new ArrayList<>();
        for (Path dir : CORPUS) {
            try (Stream<Path> s = Files.list(dir)) {
                files.addAll(s.filter(p -> p.toString().endsWith(".xml")).sorted().toList());
            }
        }
        for (Path file : files) {
            for (RouteDefinition route : routes(file)) {
                String java;
                try {
                    java = dump(route);
                } catch (RuntimeException e) {
                    // the dumper cannot write it: not the parser's to read back
                    continue;
                }
                if (!java.startsWith("from(")) {
                    // intercept(), interceptFrom(), errorHandler() before the route: not a route to compare
                    continue;
                }
                routes++;
                JavaParseResult parsed = new LwJavaParser().parse(java + ";");
                String back = parsed.routes().getRoutes().isEmpty() ? "" : dump(parsed.routes().getRoutes().get(0));
                if ((!parsed.unresolved().isEmpty() || !back.equals(java))
                        && !KNOWN.containsKey(file.getFileName().toString())) {
                    failures.add(file.getFileName() + "\n" + java + "\n--- read back as ---\n" + back
                                 + "\n--- unresolved: " + parsed.unresolved());
                }
            }
        }
        assertThat(routes).isGreaterThan(100);
        assertThat(failures).as("%d of %d routes do not read back:%n%s", failures.size(), routes,
                String.join("\n\n", failures)).isEmpty();
    }

    /** The Java the dumper is tested to write (java-dsl-*.txt) reads back into the same Java. */
    @Test
    void dumperGoldenFiles() throws Exception {
        List<Path> files;
        try (Stream<Path> s = Files.list(Path.of("src/test/resources"))) {
            files = s.filter(p -> p.getFileName().toString().startsWith("java-dsl-")).sorted().toList();
        }
        assertThat(files).isNotEmpty();
        for (Path file : files) {
            String java = Files.readString(file).strip();
            JavaParseResult parsed = new LwJavaParser().parse(java);
            assertThat(parsed.unresolved()).as(file.getFileName().toString()).isEmpty();
            assertThat(dump(parsed.routes().getRoutes().get(0)).strip()).as(file.getFileName().toString())
                    .isEqualTo(java);
        }
    }

    private static final Path SPRING_XML = Path.of("../../components/camel-spring-parent/camel-spring-xml/src/test/resources");
    private static final String SPRING_NS = "http://camel.apache.org/schema/spring";

    /** The routes of a Spring XML file (camelContext, routeContext, routes), each as a routes document of its own. */
    private static List<String> springRoutes(Path file) {
        List<String> answer = new ArrayList<>();
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = dbf.newDocumentBuilder().parse(file.toFile());
            NodeList list = doc.getElementsByTagNameNS(SPRING_NS, "route");
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            for (int i = 0; i < list.getLength(); i++) {
                Document single = dbf.newDocumentBuilder().newDocument();
                Element routes = single.createElementNS(SPRING_NS, "routes");
                single.appendChild(routes);
                routes.appendChild(single.importNode(list.item(i), true));
                StringWriter w = new StringWriter();
                t.transform(new DOMSource(single), new StreamResult(w));
                answer.add(w.toString());
            }
        } catch (Exception e) {
            // not a file the corpus can use
        }
        return answer;
    }

    /**
     * The routes of camel-spring-xml's tests, many EIPs and their edge cases: read the same way, and summed up by what
     * differs, as a measure of how far the parser gets.
     */
    @Test
    void springXmlCorpus() throws Exception {
        assumeTrue(Files.isDirectory(SPRING_XML), "camel-spring-xml is in the source tree");
        List<Path> files;
        try (Stream<Path> s = Files.walk(SPRING_XML)) {
            files = s.filter(p -> p.toString().endsWith(".xml")).sorted().toList();
        }
        int routes = 0;
        int same = 0;
        int dumpFailed = 0;
        Map<String, Integer> reasons = new TreeMap<>();
        List<String> examples = new ArrayList<>();
        for (Path file : files) {
            for (String xml : springRoutes(file)) {
                RoutesDefinition defs;
                try {
                    defs = new ModelParser(new StringReader(xml), SPRING_NS).parseRoutesDefinition().orElse(null);
                } catch (Exception e) {
                    continue;
                }
                if (defs == null || defs.getRoutes().isEmpty()) {
                    continue;
                }
                String java;
                try {
                    java = dump(defs.getRoutes().get(0));
                } catch (RuntimeException e) {
                    dumpFailed++;
                    continue;
                }
                if (!java.startsWith("from(")) {
                    continue;
                }
                routes++;
                JavaParseResult parsed = new LwJavaParser().parse(java + ";");
                String back = parsed.routes().getRoutes().isEmpty() ? "" : dump(parsed.routes().getRoutes().get(0));
                if (parsed.unresolved().isEmpty() && back.equals(java)) {
                    same++;
                    continue;
                }
                String reason = parsed.unresolved().isEmpty()
                        ? "reads back differently"
                        : parsed.unresolved().get(0).reason() + ": "
                          + parsed.unresolved().get(0).text().replaceAll("\\(.*", "(");
                reasons.merge(reason, 1, Integer::sum);
                if (reasons.get(reason) == 1 || reason.startsWith("reads back")) {
                    // one example of each
                    examples.add(SPRING_XML.relativize(file) + " [" + reason + "]\n" + java + "\n--- read back as ---\n"
                                 + back + "\n--- unresolved: " + parsed.unresolved());
                }
            }
        }
        String report = String.format("%d of %d routes read back (%d the dumper could not write)%n%s%n%n%s", same, routes,
                dumpFailed, reasons, String.join("\n\n", examples));
        Files.writeString(Path.of("target/spring-xml-corpus-report.txt"), report);
        assertThat(routes).isGreaterThan(500);
        // the rest has no Java DSL form (throwException with a ref, options of marshal and unmarshal, a logger ref)
        // or is written apart from what XML holds; raise the floor as it improves
        assertThat(same).as(report).isGreaterThanOrEqualTo(815);
    }
}
