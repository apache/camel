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

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-27328: the chain of dependencies that brings a JAR onto the classpath, from the POMs embedded in the JARs.
 */
class DependencyChainsTest {

    @TempDir
    Path dir;

    @Test
    void theDeclaredDependenciesAreThoseThatComeAlong() throws Exception {
        String pom = """
                <project>
                  <parent><groupId>org.apache.camel</groupId><artifactId>components</artifactId></parent>
                  <artifactId>camel-kafka</artifactId>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>com.example</groupId><artifactId>managed</artifactId></dependency>
                  </dependencies></dependencyManagement>
                  <dependencies>
                    <dependency><groupId>${project.groupId}</groupId><artifactId>camel-support</artifactId></dependency>
                    <dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId></dependency>
                    <dependency><groupId>com.example</groupId><artifactId>tested</artifactId><scope>test</scope></dependency>
                    <dependency><groupId>com.example</groupId><artifactId>opt</artifactId><optional>true</optional></dependency>
                    <dependency><groupId>com.example</groupId><artifactId>given</artifactId><scope>provided</scope></dependency>
                  </dependencies>
                  <build><plugins><plugin><artifactId>p</artifactId><dependencies>
                    <dependency><groupId>com.example</groupId><artifactId>plugin-dep</artifactId></dependency>
                  </dependencies></plugin></plugins></build>
                </project>
                """;
        Set<String> deps = DependencyChains.parseDeclared(new ByteArrayInputStream(pom.getBytes(StandardCharsets.UTF_8)));
        assertThat(deps).containsExactly("org.apache.camel:camel-support", "org.apache.kafka:kafka-clients");
    }

    @Test
    void theShortestChainFromADependencyOfTheProject() {
        Map<String, Set<String>> graph = Map.of(
                "org.apache.camel:camel-main", Set.of("org.apache.camel:camel-core"),
                "org.apache.camel:camel-core", Set.of(),
                "org.apache.camel:camel-kafka", Set.of("org.apache.kafka:kafka-clients", "org.apache.camel:camel-core"),
                "org.apache.kafka:kafka-clients", Set.of("org.xerial.snappy:snappy-java"),
                "org.xerial.snappy:snappy-java", Set.of());
        List<String> roots = List.of("org.apache.camel:camel-kafka");

        assertThat(DependencyChains.chain(graph, roots, "org.xerial.snappy:snappy-java")).containsExactly(
                "org.apache.camel:camel-kafka", "org.apache.kafka:kafka-clients", "org.xerial.snappy:snappy-java");
        // a dependency of the project is its own chain
        assertThat(DependencyChains.chain(graph, roots, "org.apache.camel:camel-kafka"))
                .containsExactly("org.apache.camel:camel-kafka");
        // camel-core comes along with camel-kafka too
        assertThat(DependencyChains.chain(graph, roots, "org.apache.camel:camel-core"))
                .containsExactly("org.apache.camel:camel-kafka", "org.apache.camel:camel-core");
        assertThat(DependencyChains.chain(graph, roots, "com.example:absent")).isEmpty();
    }

    @Test
    void withoutARootItStartsAtAJarNoOtherDeclares() {
        Map<String, Set<String>> graph = Map.of(
                "org.apache.camel:camel-main", Set.of("org.apache.camel:camel-core"),
                "org.apache.camel:camel-core", Set.of("com.example:lib"),
                "com.example:lib", Set.of());

        assertThat(DependencyChains.chain(graph, Set.of(), "com.example:lib")).containsExactly(
                "org.apache.camel:camel-main", "org.apache.camel:camel-core", "com.example:lib");
    }

    @Test
    void theGraphOfTheJarsOnTheClasspath() throws Exception {
        Path a = jar("a.jar", "com.example", "a", "com.example:b", "com.example:not-on-classpath");
        Path b = jar("b.jar", "com.example", "b", "com.example:c");
        Path c = jar("c.jar", "com.example", "c");

        Map<String, Set<String>> graph = DependencyChains.graph(
                Map.of("com.example:a", a, "com.example:b", b, "com.example:c", c));

        assertThat(graph.get("com.example:a")).containsExactly("com.example:b");
        assertThat(graph.get("com.example:b")).containsExactly("com.example:c");
        assertThat(graph.get("com.example:c")).isEmpty();
        assertThat(DependencyChains.chain(graph, Set.of("com.example:a"), "com.example:c"))
                .containsExactly("com.example:a", "com.example:b", "com.example:c");
    }

    @Test
    void aJarWithoutAnEmbeddedPomIsReadFromThePomBesideIt() throws Exception {
        // as kafka-clients, built with Gradle
        Path jar = dir.resolve("kafka-clients-3.9.0.jar");
        try (OutputStream os = Files.newOutputStream(jar); JarOutputStream out = new JarOutputStream(os)) {
            out.putNextEntry(new JarEntry("org/apache/kafka/Some.class"));
            out.closeEntry();
        }
        Files.writeString(dir.resolve("kafka-clients-3.9.0.pom"), """
                <project><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId>
                  <dependencies>
                    <dependency><groupId>org.xerial.snappy</groupId><artifactId>snappy-java</artifactId>
                      <scope>runtime</scope></dependency>
                  </dependencies>
                </project>
                """);

        assertThat(DependencyChains.declaredDependencies(jar, "org.apache.kafka:kafka-clients"))
                .containsExactly("org.xerial.snappy:snappy-java");
    }

    private Path jar(String name, String groupId, String artifactId, String... deps) throws Exception {
        StringBuilder pom = new StringBuilder("<project><groupId>").append(groupId).append("</groupId><artifactId>")
                .append(artifactId).append("</artifactId><dependencies>");
        for (String dep : deps) {
            String[] ga = dep.split(":");
            pom.append("<dependency><groupId>").append(ga[0]).append("</groupId><artifactId>").append(ga[1])
                    .append("</artifactId></dependency>");
        }
        pom.append("</dependencies></project>");
        Path jar = dir.resolve(name);
        try (OutputStream os = Files.newOutputStream(jar); JarOutputStream out = new JarOutputStream(os)) {
            out.putNextEntry(new JarEntry("META-INF/maven/" + groupId + "/" + artifactId + "/pom.xml"));
            out.write(pom.toString().getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return jar;
    }
}
