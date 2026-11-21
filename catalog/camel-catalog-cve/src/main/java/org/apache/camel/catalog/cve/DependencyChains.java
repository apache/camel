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
package org.apache.camel.catalog.cve;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Which dependency brings a JAR onto the classpath of an integration, and through which others: the chain from a
 * dependency of the project to it, such as {@code camel-kafka -> kafka-clients -> snappy-java}. The graph is made of
 * the dependencies the POMs embedded in the JARs ({@code META-INF/maven/<groupId>/<artifactId>/pom.xml}) declare, or
 * the POM beside a JAR in the Maven repository when it embeds none, limited to the JARs on the classpath; a dependency
 * declared in a parent POM only is not seen.
 */
public final class DependencyChains {

    // the dependencies a JAR declares, by its path: the JARs of the local Maven repository do not change
    private static final Map<String, Set<String>> DECLARED_CACHE = new ConcurrentHashMap<>();

    private DependencyChains() {
    }

    /**
     * The graph of the JARs: for each {@code groupId:artifactId}, those it declares that are on the classpath too.
     *
     * @param jars the JARs of the classpath by {@code groupId:artifactId}
     */
    public static Map<String, Set<String>> graph(Map<String, Path> jars) {
        Map<String, Set<String>> graph = new HashMap<>();
        for (Map.Entry<String, Path> e : jars.entrySet()) {
            String ga = e.getKey();
            Set<String> onClasspath = new TreeSet<>();
            for (String dep : declaredDependencies(e.getValue(), ga)) {
                if (jars.containsKey(dep) && !dep.equals(ga)) {
                    onClasspath.add(dep);
                }
            }
            graph.put(ga, onClasspath);
        }
        return graph;
    }

    /**
     * The shortest chain from one of the roots (the dependencies of the project) to the target, both included, all as
     * {@code groupId:artifactId}. When no root leads to it, the chain starts at a JAR that no other declares (one the
     * runtime adds, such as camel-main). Empty when the target is not in the graph.
     */
    public static List<String> chain(Map<String, Set<String>> graph, Collection<String> roots, String target) {
        if (roots.contains(target)) {
            return List.of(target);
        }
        List<String> chain = shortestPath(graph, new TreeSet<>(roots), target);
        if (chain.isEmpty()) {
            Set<String> declared = new HashSet<>();
            graph.values().forEach(declared::addAll);
            Set<String> tops = new TreeSet<>(graph.keySet());
            tops.removeAll(declared);
            chain = shortestPath(graph, tops, target);
        }
        return chain;
    }

    private static List<String> shortestPath(Map<String, Set<String>> graph, Set<String> roots, String target) {
        Map<String, String> previous = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        for (String root : roots) {
            if (graph.containsKey(root) && seen.add(root)) {
                queue.add(root);
            }
        }
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(target)) {
                List<String> path = new ArrayList<>();
                for (String at = target; at != null; at = previous.get(at)) {
                    path.add(0, at);
                }
                return path;
            }
            for (String next : graph.getOrDefault(current, Collections.emptySet())) {
                if (seen.add(next)) {
                    previous.put(next, current);
                    queue.add(next);
                }
            }
        }
        return Collections.emptyList();
    }

    /** The dependencies the POM embedded in the JAR declares, as {@code groupId:artifactId}; empty when it has none. */
    public static Set<String> declaredDependencies(Path jar, String ga) {
        return DECLARED_CACHE.computeIfAbsent(jar.toString(), k -> readDeclared(jar, ga));
    }

    private static Set<String> readDeclared(Path jarPath, String ga) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // the POM of the JAR itself: a shaded JAR has the POMs of what it shades too
            ZipEntry entry = jar.getEntry("META-INF/maven/" + ga.replace(':', '/') + "/pom.xml");
            if (entry != null) {
                try (InputStream is = jar.getInputStream(entry)) {
                    return parseDeclared(is);
                }
            }
            // a JAR built without Maven (such as with Gradle) embeds no POM: the POM beside it in the Maven repository
            Path pom = siblingPom(jarPath);
            if (pom != null) {
                try (InputStream is = Files.newInputStream(pom)) {
                    return parseDeclared(is);
                }
            }
            entry = jar.stream()
                    .filter(e -> e.getName().startsWith("META-INF/maven/") && e.getName().endsWith("/pom.xml"))
                    .findFirst().orElse(null);
            if (entry != null) {
                try (InputStream is = jar.getInputStream(entry)) {
                    return parseDeclared(is);
                }
            }
            return Collections.emptySet();
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    /** {@code kafka-clients-3.9.0.pom} beside {@code kafka-clients-3.9.0.jar}; null when there is none. */
    private static Path siblingPom(Path jarPath) {
        String name = jarPath.getFileName().toString();
        if (!name.endsWith(".jar")) {
            return null;
        }
        Path pom = jarPath.resolveSibling(name.substring(0, name.length() - 4) + ".pom");
        return Files.isRegularFile(pom) ? pom : null;
    }

    // a POM is read with secure processing and without external entities
    private static DocumentBuilderFactory createDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setIgnoringComments(true);
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
        } catch (ParserConfigurationException e) {
            // not supported by this parser
        }
        try {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        } catch (ParserConfigurationException e) {
            // not supported by this parser
        }
        try {
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (ParserConfigurationException e) {
            // not supported by this parser
        }
        return factory;
    }

    /**
     * The dependencies a POM declares in {@code project/dependencies}, as {@code groupId:artifactId}: not those of
     * {@code dependencyManagement} or of plugins, and not the test, provided, system or optional ones, which do not
     * come along with it.
     */
    public static Set<String> parseDeclared(InputStream pom) throws Exception {
        DocumentBuilderFactory dbf = createDocumentBuilderFactory();
        Document dom = dbf.newDocumentBuilder().parse(pom);
        Element project = dom.getDocumentElement();
        String projectGroupId = childText(project, "groupId");
        if (projectGroupId == null) {
            Element parent = child(project, "parent");
            projectGroupId = parent != null ? childText(parent, "groupId") : null;
        }
        Set<String> deps = new LinkedHashSet<>();
        Element dependencies = child(project, "dependencies");
        if (dependencies == null) {
            return deps;
        }
        for (Node n = dependencies.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element dep) || !"dependency".equals(dep.getTagName())) {
                continue;
            }
            String scope = childText(dep, "scope");
            if ("test".equals(scope) || "provided".equals(scope) || "system".equals(scope) || "import".equals(scope)
                    || "true".equals(childText(dep, "optional"))) {
                continue;
            }
            String g = childText(dep, "groupId");
            String a = childText(dep, "artifactId");
            if (("${project.groupId}".equals(g) || "${pom.groupId}".equals(g)) && projectGroupId != null) {
                g = projectGroupId;
            }
            if (g != null && a != null && !g.contains("${") && !a.contains("${")) {
                deps.add(g + ":" + a);
            }
        }
        return deps;
    }

    private static Element child(Element parent, String tag) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && tag.equals(e.getTagName())) {
                return e;
            }
        }
        return null;
    }

    private static String childText(Element parent, String tag) {
        Element e = child(parent, tag);
        return e != null ? e.getTextContent().trim() : null;
    }
}
