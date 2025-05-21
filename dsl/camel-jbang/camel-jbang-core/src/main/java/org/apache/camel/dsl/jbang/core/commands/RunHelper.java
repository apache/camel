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
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.util.ReflectionHelper;
import org.apache.camel.util.StringHelper;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

public final class RunHelper {

    private RunHelper() {
    }

    public static String mavenArtifactId() {
        Path pomPath = Paths.get("pom.xml");
        if (Files.exists(pomPath) && Files.isRegularFile(pomPath)) {
            // find additional dependencies form pom.xml
            MavenXpp3Reader mavenReader = new MavenXpp3Reader();
            try (Reader reader = Files.newBufferedReader(pomPath)) {
                Model model = mavenReader.read(reader);
                model.setPomFile(pomPath.toFile()); // Still need File for Maven Model

                return model.getArtifactId();
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    public static List<String> scanMavenDependenciesFromPom() {
        List<String> answer = new ArrayList<>();

        Path pomPath = Paths.get("pom.xml");
        if (Files.exists(pomPath) && Files.isRegularFile(pomPath)) {
            CamelCatalog catalog = new DefaultCamelCatalog();

            // find additional dependencies form pom.xml
            MavenXpp3Reader mavenReader = new MavenXpp3Reader();
            try (Reader reader = Files.newBufferedReader(pomPath)) {
                Model model = mavenReader.read(reader);
                model.setPomFile(pomPath.toFile()); // Still need File for Maven Model

                for (Dependency d : model.getDependencies()) {
                    String g = d.getGroupId();
                    String scope = d.getScope();
                    boolean accept = scope == null || "compile".equals(scope);
                    // 3rd party dependencies
                    if (accept && !g.startsWith("org.apache.camel")) {
                        String v = d.getVersion();
                        if (v != null && v.startsWith("${") && v.endsWith("}")) {
                            // version uses placeholder, so try to find them
                            v = v.substring(2, v.length() - 1);
                            v = findMavenProperty((Path) pomPath, v);
                        }
                        if (v != null) {
                            String gav = "mvn:" + g + ":" + d.getArtifactId() + ":" + v;
                            if (!answer.contains(gav)) {
                                answer.add(gav);
                            }
                        }
                    } else if (accept && g.startsWith("org.apache.camel")) {
                        // camel dependencies
                        String a = d.getArtifactId();

                        if (!isInCamelCatalog(catalog, a)) {
                            // not a known camel artifact
                            continue;
                        }

                        if (a.endsWith("-starter")) {
                            a = StringHelper.before(a, "-starter");
                        }
                        if (a.startsWith("camel-quarkus-")) {
                            a = StringHelper.after(a, "camel-quarkus");
                        }
                        if (a.startsWith("camel-")) {
                            a = StringHelper.after(a, "camel-");
                        }
                        String gav = "camel:" + a;
                        if (!answer.contains(gav)) {
                            answer.add(gav);
                        }
                    }
                }
            } catch (Exception ex) {
                // do something better here
            }
        }
        return answer;
    }

    public static List<String> scanMavenOrGradleProject() {
        List<String> answer = new ArrayList<>();

        // scan as maven based project
        Stream<Path> s = Stream.concat(walk(Path.of("src/main/java")), walk(Path.of("src/main/resources")));
        s.filter(Files::isRegularFile)
                .map(Path::toString)
                .forEach(answer::add);
        return answer;
    }

    public static String findMavenProperty(Path pomPath, String placeholder) {
        if (Files.exists(pomPath) && Files.isRegularFile(pomPath)) {
            // find additional dependencies form pom.xml
            MavenXpp3Reader mavenReader = new MavenXpp3Reader();
            try (Reader reader = Files.newBufferedReader(pomPath)) {
                Model model = mavenReader.read(reader);
                model.setPomFile(pomPath.toFile()); // Still need File for Maven Model
                String p = model.getProperties().getProperty(placeholder);
                if (p != null) {
                    return p;
                } else if (model.getParent() != null) {
                    p = model.getParent().getRelativePath();
                    if (p != null) {
                        Path parentPath = pomPath.getParent().resolve(p);
                        return findMavenProperty(parentPath, placeholder);
                    }
                }
            } catch (Exception ex) {
                // ignore
            }
        }
        return null;
    }

    // Keep for backward compatibility
    @Deprecated
    public static String findMavenProperty(java.io.File f, String placeholder) {
        return findMavenProperty(f.toPath(), placeholder);
    }

    public static Stream<Path> walk(Path dir) {
        try {
            if (Files.isDirectory(dir)) {
                return Files.walk(dir);
            } else {
                return Stream.empty();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isInCamelCatalog(String artifactId) {
        return isInCamelCatalog(new DefaultCamelCatalog(), artifactId);
    }

    public static boolean isInCamelCatalog(CamelCatalog catalog, String artifactId) {
        for (String n : catalog.findComponentNames()) {
            String a = catalog.componentModel(n).getArtifactId();
            if (artifactId.equals(a)) {
                return true;
            }
        }
        for (String n : catalog.findLanguageNames()) {
            String a = catalog.languageModel(n).getArtifactId();
            if (artifactId.equals(a)) {
                return true;
            }
        }
        for (String n : catalog.findDataFormatNames()) {
            String a = catalog.dataFormatModel(n).getArtifactId();
            if (artifactId.equals(a)) {
                return true;
            }
        }
        for (String n : catalog.findOtherNames()) {
            String a = catalog.otherModel(n).getArtifactId();
            if (artifactId.equals(a)) {
                return true;
            }
        }
        return false;
    }

    public static void doWithFields(Class<?> clazz, ReflectionHelper.FieldCallback fc) throws IllegalArgumentException {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            try {
                fc.doWith(field);
            } catch (IllegalAccessException ex) {
                // ignore
            }
        }
    }
}
