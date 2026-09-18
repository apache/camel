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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Resolves Spring Boot configuration metadata statically from JARs in the local Maven repository. This enables
 * autocomplete for Spring Boot properties even when the application is not running (phantom/stopped projects).
 */
final class SpringBootMetadataResolver {

    private SpringBootMetadataResolver() {
    }

    record MetadataResult(Map<String, JsonObject> properties, Map<String, List<String>> hints) {
    }

    static MetadataResult loadFromPom(Path pomFile, String camelVersion) {
        Map<String, JsonObject> properties = new HashMap<>();
        Map<String, List<String>> hints = new HashMap<>();

        String springBootVersion = DependencyLoader.detectSpringBootVersion(pomFile);
        if (springBootVersion == null && camelVersion != null) {
            springBootVersion = detectSpringBootVersionFromCamelParent(camelVersion);
        }

        if (springBootVersion != null) {
            // core JARs always present in Spring Boot projects
            readJarIfExists("org.springframework.boot", "spring-boot", springBootVersion, properties, hints);
            readJarIfExists("org.springframework.boot", "spring-boot-autoconfigure", springBootVersion, properties, hints);
        }

        Set<String> scanned = new HashSet<>();
        List<DependencyLoader.DepEntry> deps = DependencyLoader.loadFromPomXml(pomFile);
        for (DependencyLoader.DepEntry dep : deps) {
            String v = dep.version() != null ? dep.version() : springBootVersion;
            if (v == null) {
                continue;
            }
            String key = dep.groupId() + ":" + dep.artifactId() + ":" + v;
            if (!scanned.add(key)) {
                continue;
            }
            readJarIfExists(dep.groupId(), dep.artifactId(), v, properties, hints);

            // for starter dependencies, resolve one level of transitives from the starter pom
            if (dep.artifactId().contains("starter")) {
                resolveStarterTransitives(dep.groupId(), dep.artifactId(), v, scanned, properties, hints);
            }
        }

        return new MetadataResult(properties, hints);
    }

    private static void resolveStarterTransitives(
            String groupId, String artifactId, String version,
            Set<String> scanned,
            Map<String, JsonObject> properties, Map<String, List<String>> hints) {
        Path pomPath = localRepoPomPath(groupId, artifactId, version);
        if (pomPath == null) {
            return;
        }
        try {
            String content = Files.readString(pomPath);
            // simple XML parsing for dependency elements in starter poms
            int pos = 0;
            while (true) {
                int depStart = content.indexOf("<dependency>", pos);
                if (depStart < 0) {
                    break;
                }
                int depEnd = content.indexOf("</dependency>", depStart);
                if (depEnd < 0) {
                    break;
                }
                String depBlock = content.substring(depStart, depEnd);
                pos = depEnd + 1;

                String g = extractXmlElement(depBlock, "groupId");
                String a = extractXmlElement(depBlock, "artifactId");
                if (g == null || a == null) {
                    continue;
                }
                // use same version for Spring Boot group deps
                String v = "org.springframework.boot".equals(g) ? version : extractXmlElement(depBlock, "version");
                if (v == null) {
                    continue;
                }
                String key = g + ":" + a + ":" + v;
                if (!scanned.add(key)) {
                    continue;
                }
                readJarIfExists(g, a, v, properties, hints);
                // recurse into Spring Boot module deps (e.g. spring-boot-jdbc -> spring-boot-sql)
                if ("org.springframework.boot".equals(g)) {
                    resolveStarterTransitives(g, a, v, scanned, properties, hints);
                }
            }
        } catch (Exception e) {
            // skip
        }
    }

    private static String extractXmlElement(String xml, String element) {
        String open = "<" + element + ">";
        String close = "</" + element + ">";
        int start = xml.indexOf(open);
        if (start < 0) {
            return null;
        }
        start += open.length();
        int end = xml.indexOf(close, start);
        return end > start ? xml.substring(start, end).trim() : null;
    }

    private static void readJarIfExists(
            String groupId, String artifactId, String version,
            Map<String, JsonObject> properties, Map<String, List<String>> hints) {
        Path jarPath = localRepoJarPath(groupId, artifactId, version);
        if (jarPath != null) {
            readMetadataFromJar(jarPath, properties, hints);
        }
    }

    private static String detectSpringBootVersionFromCamelParent(String camelVersion) {
        Path localRepo = Path.of(System.getProperty("user.home"), ".m2", "repository");
        Path parentPom = localRepo
                .resolve("org/apache/camel/camel-parent")
                .resolve(camelVersion)
                .resolve("camel-parent-" + camelVersion + ".pom");
        if (!Files.isRegularFile(parentPom)) {
            return null;
        }
        try {
            String content = Files.readString(parentPom);
            int start = content.indexOf("<spring-boot-version>");
            if (start < 0) {
                return null;
            }
            start += "<spring-boot-version>".length();
            int end = content.indexOf("</spring-boot-version>", start);
            if (end < 0) {
                return null;
            }
            return content.substring(start, end).trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static void readMetadataFromJar(
            Path jarPath, Map<String, JsonObject> properties, Map<String, List<String>> hints) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            readMetadataEntry(jar, "META-INF/spring-configuration-metadata.json", properties, hints);
            readMetadataEntry(jar, "META-INF/additional-spring-configuration-metadata.json", properties, hints);
        } catch (Exception e) {
            // skip unreadable JARs
        }
    }

    private static void readMetadataEntry(
            JarFile jar, String entryName, Map<String, JsonObject> properties, Map<String, List<String>> hints) {
        JarEntry entry = jar.getJarEntry(entryName);
        if (entry == null) {
            return;
        }
        try (InputStream is = jar.getInputStream(entry)) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Object parsed = Jsoner.deserialize(content);
            if (!(parsed instanceof JsonObject root)) {
                return;
            }

            Object propsObj = root.get("properties");
            if (propsObj instanceof JsonArray arr) {
                for (int i = 0; i < arr.size(); i++) {
                    if (arr.get(i) instanceof JsonObject prop) {
                        String name = prop.getString("name");
                        if (name != null && !name.isEmpty()) {
                            properties.putIfAbsent(name, prop);
                        }
                    }
                }
            }

            Object hintsObj = root.get("hints");
            if (hintsObj instanceof JsonArray arr) {
                for (int i = 0; i < arr.size(); i++) {
                    if (arr.get(i) instanceof JsonObject hint) {
                        String name = hint.getString("name");
                        Object valuesObj = hint.get("values");
                        if (name != null && valuesObj instanceof JsonArray valuesArr && !valuesArr.isEmpty()) {
                            List<String> values = new ArrayList<>();
                            for (int j = 0; j < valuesArr.size(); j++) {
                                if (valuesArr.get(j) instanceof JsonObject valueObj) {
                                    Object v = valueObj.get("value");
                                    if (v != null) {
                                        values.add(v.toString());
                                    }
                                }
                            }
                            if (!values.isEmpty()) {
                                hints.putIfAbsent(name, values);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // skip unparseable metadata
        }
    }

    static Path localRepoJarPath(String groupId, String artifactId, String version) {
        Path localRepo = Path.of(System.getProperty("user.home"), ".m2", "repository");
        Path jarPath = localRepo
                .resolve(groupId.replace('.', File.separatorChar))
                .resolve(artifactId)
                .resolve(version)
                .resolve(artifactId + "-" + version + ".jar");
        return Files.isRegularFile(jarPath) ? jarPath : null;
    }

    private static Path localRepoPomPath(String groupId, String artifactId, String version) {
        Path localRepo = Path.of(System.getProperty("user.home"), ".m2", "repository");
        Path pomPath = localRepo
                .resolve(groupId.replace('.', File.separatorChar))
                .resolve(artifactId)
                .resolve(version)
                .resolve(artifactId + "-" + version + ".pom");
        return Files.isRegularFile(pomPath) ? pomPath : null;
    }
}
