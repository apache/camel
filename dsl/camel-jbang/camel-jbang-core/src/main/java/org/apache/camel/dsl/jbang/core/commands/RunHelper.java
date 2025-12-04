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
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Stream;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ReflectionHelper;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

public final class RunHelper {

    private RunHelper() {}

    public static String mavenArtifactId(Path pomPath) {
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

    public static Model loadMavenModel(Path pomPath) throws Exception {
        Model answer = null;
        if (Files.exists(pomPath) && Files.isRegularFile(pomPath)) {
            // find additional dependencies form pom.xml
            MavenXpp3Reader mavenReader = new MavenXpp3Reader();
            try (Reader reader = Files.newBufferedReader(pomPath)) {
                answer = mavenReader.read(reader);
                answer.setPomFile(pomPath.toFile()); // Still need File for Maven Model
            }
        }
        return answer;
    }

    public static List<String> scanMavenDependenciesFromPom(Path pomPath) throws Exception {
        Model model = loadMavenModel(pomPath);
        if (model != null) {
            return scanMavenDependenciesFromModel(pomPath, model, false);
        }
        return Collections.EMPTY_LIST;
    }

    public static List<String> scanMavenDependenciesFromModel(
            Path pomPath, Model model, boolean includeDependencyManagement) throws Exception {
        String camelVersion = null;
        String camelSpringBootVersion = null;
        String springBootVersion = null;
        String quarkusVersion = null;

        StringJoiner sj = new StringJoiner(",");
        for (Repository r : model.getRepositories()) {
            sj.add(r.getUrl());
        }

        MavenDependencyDownloader downloader = new MavenDependencyDownloader();
        if (sj.length() > 0) {
            downloader.setRepositories(sj.toString());
        }
        downloader.build();

        List<String> answer = new ArrayList<>();
        if (model.getDependencyManagement() != null) {
            for (Dependency d : model.getDependencyManagement().getDependencies()) {
                String g = resolveDependencyPlaceholder(d.getGroupId(), pomPath, downloader);
                String a = resolveDependencyPlaceholder(d.getArtifactId(), pomPath, downloader);
                String v = resolveDependencyPlaceholder(d.getVersion(), pomPath, downloader);
                if (v != null) {
                    if ("camel-bom".equals(a)) {
                        camelVersion = v;
                    } else if ("camel-spring-boot-bom".equals(a)) {
                        camelSpringBootVersion = v;
                    } else if ("org.springframework.boot".equals(g) && "spring-boot-dependencies".equals(a)) {
                        springBootVersion = v;
                    } else if ("quarkus-bom".equals(a)) {
                        quarkusVersion = v;
                    }
                    if (includeDependencyManagement) {
                        String gav = "mvn:" + g + ":" + a + ":" + v;
                        if (!answer.contains(gav)) {
                            answer.add(gav);
                        }
                    }
                }
            }
        }

        for (Dependency d : model.getDependencies()) {
            String scope = d.getScope();
            boolean accept = scope == null || "compile".equals(scope);
            if (accept) {
                String g = resolveDependencyPlaceholder(d.getGroupId(), pomPath, downloader);
                String a = resolveDependencyPlaceholder(d.getArtifactId(), pomPath, downloader);
                String v = resolveDependencyPlaceholder(d.getVersion(), pomPath, downloader);
                if (v == null && ("org.apache.camel".equals(g))) {
                    v = camelVersion;
                } else if (v == null && ("org.apache.camel.springboot".equals(g))) {
                    v = camelSpringBootVersion;
                } else if (v == null && "org.springframework.boot".equals(g)) {
                    v = springBootVersion;
                } else if (v == null && "io.quarkus.platform".equals(g)) {
                    v = quarkusVersion;
                }
                String gav = "mvn:" + g + ":" + a;
                if (v != null) {
                    gav += ":" + v;
                }
                if (!answer.contains(gav)) {
                    answer.add(gav);
                }
            }
        }
        return answer;
    }

    private static String resolveDependencyPlaceholder(
            String value, Path pomPath, MavenDependencyDownloader downloader) {
        if (value != null && value.startsWith("${") && value.endsWith("}")) {
            // version uses placeholder, so try to find them
            value = value.substring(2, value.length() - 1);
            value = findMavenProperty(pomPath, value, downloader);
        }
        return value;
    }

    public static List<String> scanMavenOrGradleProject(Path parentPath) {
        List<String> answer = new ArrayList<>();

        // scan as maven based project
        Stream<Path> s = Stream.concat(
                walk(Path.of(parentPath.toFile().getAbsolutePath(), "src/main/java")),
                walk(Path.of(parentPath.toFile().getAbsolutePath(), "src/main/resources")));
        s.filter(Files::isRegularFile).map(Path::toString).forEach(answer::add);
        return answer;
    }

    public static String findMavenProperty(Path pomPath, String placeholder, MavenDependencyDownloader downloader) {
        if (Files.exists(pomPath) && Files.isRegularFile(pomPath)) {
            // find additional dependencies form pom.xml
            MavenXpp3Reader mavenReader = new MavenXpp3Reader();
            try (Reader reader = Files.newBufferedReader(pomPath)) {
                Model model = mavenReader.read(reader);
                model.setPomFile(pomPath.toFile()); // Still need File for Maven Model
                String p = model.getProperties().getProperty(placeholder);
                if (p != null && p.startsWith("${") && p.endsWith("}")) {
                    p = p.substring(2, p.length() - 1);
                    p = model.getProperties().getProperty(p);
                }
                if ("project.version".equals(p) || "project.version".equals(placeholder)) {
                    p = model.getVersion();
                    if (p == null && model.getParent() != null) {
                        p = model.getParent().getVersion();
                    }
                }
                if (p != null) {
                    return p;
                } else if (model.getParent() != null) {
                    p = model.getParent().getRelativePath();
                    if (p != null) {
                        String dir = FileUtil.onlyPath(pomPath.toAbsolutePath().toString());
                        p = FileUtil.compactPath(dir + "/" + p);
                        pomPath = Paths.get(p);
                        boolean exists = Files.exists(pomPath) && Files.isRegularFile(pomPath);
                        if (exists) {
                            return findMavenProperty(pomPath, placeholder, downloader);
                        } else if (downloader != null) {
                            // download dependency as it's not in local path
                            MavenArtifact ma = downloader.downloadArtifact(
                                    model.getParent().getGroupId(),
                                    model.getParent().getArtifactId() + ":pom",
                                    model.getParent().getVersion());
                            if (ma != null) {
                                return findMavenProperty(ma.getFile(), placeholder, downloader);
                            }
                        }
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
    public static String findMavenProperty(java.io.File f, String placeholder, MavenDependencyDownloader downloader) {
        return findMavenProperty(f.toPath(), placeholder, downloader);
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

    /**
     * When using camel run . or camel export . then dot should include all the files in the current folder.
     */
    public static void dirToFiles(String dir, List<String> files) {
        files.clear();
        try (Stream<Path> paths = Files.list(Paths.get(dir))) {
            paths.filter(p -> {
                        try {
                            return Files.isRegularFile(p) && !Files.isHidden(p);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(f -> files.add(dir + "/" + f.getFileName()));
        } catch (IOException e) {
            // Ignore
        }
    }

    /**
     * Adds camel to the start of the list of commands to make it possible to run camel-jbang using a spawned process
     * (to run in background).
     */
    public static void addCamelJBangCommand(List<String> cmds) {
        if (FileUtil.isWindows()) {
            String jbangDir = System.getenv().getOrDefault("JBANG_DIR", System.getProperty("user.home") + "\\.jbang");
            cmds.add(0, jbangDir + "\\bin\\camel.cmd");
        } else {
            cmds.add(0, "camel");
        }
    }
}
