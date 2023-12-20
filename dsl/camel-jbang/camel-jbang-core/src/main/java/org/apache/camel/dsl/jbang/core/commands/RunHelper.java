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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

public final class RunHelper {

    private RunHelper() {
    }

    public static String mavenArtifactId() {
        File f = new File("pom.xml");
        if (f.exists() && f.isFile()) {
            // find additional dependencies form pom.xml
            MavenXpp3Reader mavenReader = new MavenXpp3Reader();
            try {
                Model model = mavenReader.read(new FileReader(f));
                model.setPomFile(f);

                return model.getArtifactId();
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    public static List<String> scanMavenDependenciesFromPom() {
        List<String> answer = new ArrayList<>();

        File f = new File("pom.xml");
        if (f.exists() && f.isFile()) {
            // find additional dependencies form pom.xml
            MavenXpp3Reader mavenReader = new MavenXpp3Reader();
            try {
                Model model = mavenReader.read(new FileReader(f));
                model.setPomFile(f);

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
                            v = findMavenProperty(f, v);
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

                        if (!isInCamelCatalog(a)) {
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
        s.filter(p -> p.toFile().isFile())
                .map(p -> p.toFile().getPath())
                .forEach(answer::add);
        return answer;
    }

    public static String findMavenProperty(File f, String placeholder) {
        if (f.exists() && f.isFile()) {
            // find additional dependencies form pom.xml
            MavenXpp3Reader mavenReader = new MavenXpp3Reader();
            try {
                Model model = mavenReader.read(new FileReader(f));
                model.setPomFile(f);
                String p = model.getProperties().getProperty(placeholder);
                if (p != null) {
                    return p;
                } else if (model.getParent() != null) {
                    p = model.getParent().getRelativePath();
                    if (p != null) {
                        String dir = FileUtil.onlyPath(f.getAbsolutePath());
                        String parent = FileUtil.compactPath(dir + File.separatorChar + p);
                        return findMavenProperty(new File(parent), placeholder);
                    }
                }
            } catch (Exception ex) {
                // ignore
            }
        }
        return null;
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
        CamelCatalog catalog = new DefaultCamelCatalog();
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
}
