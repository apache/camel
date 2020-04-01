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
package org.apache.camel.maven.packaging;

import java.io.IOError;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.tooling.util.PackageHelper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

@Mojo(name = "xref-check", threadSafe = true)
public class XRefCheckMojo extends AbstractMojo {

    public static final java.lang.String PLAYBOOK = "antora-playbook-local-xref-check.yml";
    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            List<String> unresolved = checkXRef(project.getBasedir().toPath());
            if (!unresolved.isEmpty()) {
                getLog().error("Unresolved xrefs:");
                for (String ref : unresolved) {
                    getLog().error("  " + ref);
                }
                throw new MojoFailureException("Unresolved xrefs");
            }
        } catch (IOException e) {
            throw new MojoFailureException("Error checking xref", e);
        }
    }

    public List<String> checkXRef(Path path) throws IOException {
        List<String> unresolved = new ArrayList<>();
        Load yaml = new Load(LoadSettings.builder().build());
        Map site;
        try (Reader r = Files.newBufferedReader(path.resolve(PLAYBOOK))) {
            site = (Map) yaml.loadFromReader(r);
        }
        Map<String, String> attributes = (Map) ((Map) site.get("asciidoc")).get("attributes");
        if (attributes != null) {
            attributes = attributes.entrySet().stream()
                    .collect(Collectors.toMap(e -> "{" + e.getKey() + "}", e -> e.getValue()));
        }
        Map<String, Path> pages = new TreeMap<>();
        for (Map component : (List<Map>) ((Map) site.get("content")).get("sources")) {
            String url = (String) component.get("url");
            String startPath = (String) component.get("start_path");
            Path root = path.resolve(url).resolve(startPath).normalize();
            Map antora;
            try (Reader r = Files.newBufferedReader(root.resolve("antora.yml"))) {
                antora = (Map) yaml.loadFromReader(r);
            }
            String name = (String) antora.get("name");
            List<Path> navs = Optional.ofNullable((List<String>) antora.get("nav"))
                    .map(List::stream)
                    .orElse(Stream.empty())
                    .map(root::resolve)
                    .collect(Collectors.toList());
            for (Path nav : navs) {
                pages.put(name + ":ROOT:" + nav.getFileName().toString(), nav);
            }
            Files.list(root.resolve("modules"))
                    .filter(Files::isDirectory)
                    .filter(p -> Files.isDirectory(p.resolve("pages")))
                    .forEach(module -> {
                        String m = module.getFileName().toString();
                        Path pagesDir = module.resolve("pages");
                        walk(pagesDir)
                                .filter(Files::isRegularFile)
                                .forEach(page -> {
                                    Path rel = pagesDir.relativize(page);
                                    pages.put(name + ":" + m + ":" + rel.toString(), page);
                                });
                    });
        }

        Pattern xref = Pattern.compile("\\b(?<all>xref:(?<link>[^\\[]+.adoc)[^\\]]*\\])");
        for (Map.Entry<String, Path> page : pages.entrySet()) {
            int firstDot = page.getKey().indexOf(":");
            int secondDot = firstDot >= 0 ? page.getKey().indexOf(":", firstDot + 1) : -1;
            String component = secondDot >= 0 ? page.getKey().substring(0, firstDot) : "";
            String module = secondDot >= 0 ? page.getKey().substring(firstDot + 1, secondDot) : "";
            String str = PackageHelper.loadText(page.getValue());
            for (Map.Entry<String, String> repl : attributes.entrySet()) {
                str = str.replace(repl.getKey(), repl.getValue());
            }
            Matcher m = xref.matcher(str);
            while (m.find()) {
                String link = m.group("link");
                String all = m.group("all");
                String[] groups = link.split(":");
                String cl;
                String ml;
                String rem;
                if (groups.length == 3) {
                    cl = groups[0];
                    ml = groups[1].isEmpty() ? "ROOT" : groups[1];
                    rem = groups[2];
                } else if (groups.length == 2) {
                    cl = component;
                    ml = groups[0];
                    rem = groups[1];
                } else {
                    cl = component;
                    ml = module;
                    rem = groups[0];
                }
                if (cl.startsWith("latest@")) {
                    cl = cl.substring("latest@".length());
                }
                link = cl + ":" + ml + ":" + rem;
                if (!pages.containsKey(link)) {
                    long line = str.chars().limit(m.start()).filter(c -> c == '\n').count() + 1;
                    String prnt = Stream.of(all.split("\n")).map(String::trim).collect(Collectors.joining(" "));
                    unresolved.add(page.getKey() + " (" + page.getValue() + ") at line " + line + ": " + prnt);
                }
            }
        }

        return unresolved;
    }

    private Stream<Path> walk(Path p) {
        try {
            return Files.walk(p);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

}
