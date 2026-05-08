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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.xml.XmlLineNumberParser;
import picocli.CommandLine;

@CommandLine.Command(name = "update",
                     description = "Updates dependencies in Maven pom.xml or Java source files (JBang style)",
                     sortOptions = false,
                     showDefaultValues = true)
public class DependencyUpdate extends DependencyList {

    @CommandLine.Parameters(description = "Maven pom.xml or Java source files (JBang Style with //DEPS) to have dependencies updated."
                                          + " Route definition files (YAML, XML) can also be included and will be used as source files"
                                          + " for dependency resolution.",
                            arity = "1..*")
    public List<Path> targetFiles;

    @CommandLine.Option(names = { "--clean" },
                        description = "Regenerate list of dependencies (do not keep existing dependencies). Not supported for pom.xml")
    protected boolean clean;

    @CommandLine.Option(names = { "--scan-routes" },
                        description = "Sync dependencies from route definitions. Only manages org.apache.camel dependencies,"
                                      + " preserving non-Camel dependencies. Removes unused Camel dependencies.")
    protected boolean scanRoutes;

    private final List<String> deps = new ArrayList<>();
    private final List<MavenGav> gavs = new ArrayList<>();
    // actual target files to update (pom.xml or Java files with //DEPS)
    private final List<Path> updateTargets = new ArrayList<>();

    public DependencyUpdate(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // clear state from any previous invocation
        deps.clear();
        gavs.clear();
        updateTargets.clear();

        if (clean && scanRoutes) {
            printer().printErr("Options --clean and --scan-routes are mutually exclusive");
            return -1;
        }

        for (Path file : targetFiles) {
            if (!Files.exists(file)) {
                printer().printErr("Source file does not exist: " + file);
                return -1;
            }
            String name = file.getFileName().toString();
            String ext = FileUtil.onlyExt(name, true);
            if ("pom.xml".equals(name) || "java".equals(ext)) {
                updateTargets.add(file);
            }
        }

        if (scanRoutes) {
            // in scan-routes mode, strip existing //DEPS from Java target files before the export pipeline
            // runs, so only actual route-based dependencies are resolved (not stale //DEPS)
            for (Path file : updateTargets) {
                String ext = FileUtil.onlyExt(file.getFileName().toString(), true);
                if ("java".equals(ext)) {
                    stripJBangDeps(file);
                }
            }
        }

        if (updateTargets.isEmpty()) {
            printer().printErr("No target files (pom.xml or Java source files) specified");
            return -1;
        }

        // validate that all update targets are of the same type (all Maven or all JBang)
        boolean hasMaven = updateTargets.stream().anyMatch(f -> "pom.xml".equals(f.getFileName().toString()));
        boolean hasJava = updateTargets.stream().anyMatch(f -> !"pom.xml".equals(f.getFileName().toString()));
        if (hasMaven && hasJava) {
            printer().printErr("Cannot mix pom.xml and Java source files as update targets");
            return -1;
        }

        Path firstTarget = updateTargets.get(0);
        boolean maven = hasMaven;

        if (clean && !maven) {
            // in clean mode: remove all DEPS first
            for (Path file : updateTargets) {
                updateJBangSource(file);
            }
        }

        if (maven && this.runtime == null) {
            // Basic heuristic to determine if the project is a Quarkus or Spring Boot one.
            String pomContent = new String(Files.readAllBytes(firstTarget));
            if (pomContent.contains("quarkus")) {
                runtime = RuntimeType.quarkus;
            } else if (pomContent.contains("spring-boot")) {
                runtime = RuntimeType.springBoot;
            } else if (pomContent.contains("camel-main")) {
                runtime = RuntimeType.main;
            } else {
                // In case no specific word found, we keep the runtime type unset even if the fallback is currently on Main Runtime type
            }
        }

        return super.doCall();
    }

    @Override
    protected void outputGav(MavenGav gav, int index, int total) {
        try {
            Path firstTarget = updateTargets.get(0);
            boolean maven = "pom.xml".equals(firstTarget.getFileName().toString());
            if (maven) {
                outputGavMaven(gav, index, total);
            } else {
                outputGavJBang(gav, index, total);
            }
        } catch (Exception e) {
            printer().printErr("Cannot update dependencies due to " + e.getMessage(), e);
        }
    }

    protected void outputGavMaven(MavenGav gav, int index, int total) throws Exception {
        gavs.add(gav);

        boolean last = total - index <= 1;
        if (last) {
            for (Path file : updateTargets) {
                updateMavenSource(file);
            }
        }
    }

    protected void outputGavJBang(MavenGav gav, int index, int total) {
        if (index == 0) {
            deps.add("//DEPS org.apache.camel:camel-bom:" + gav.getVersion() + "@pom");
        }
        if (gav.getGroupId().equals("org.apache.camel")) {
            // jbang has version in @pom so we should remove this
            gav.setVersion(null);
        }
        String line = "//DEPS " + gav;
        if (!deps.contains(line)) {
            deps.add(line);
        }
        boolean last = total - index <= 1;
        if (last) {
            for (Path file : updateTargets) {
                if (scanRoutes) {
                    syncJBangSource(file);
                } else {
                    updateJBangSource(file);
                }
            }
        }
    }

    private void updateJBangSource(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            List<String> answer = new ArrayList<>();

            // find position of where the old DEPS was
            int pos = -1;
            for (int i = 0; i < lines.size(); i++) {
                String o = lines.get(i);
                // remove leading comments
                String l = o.trim();
                while (l.startsWith("#")) {
                    l = l.substring(1);
                }
                if (l.startsWith("//DEPS ")) {
                    if (pos == -1) {
                        pos = i;
                    }
                } else {
                    answer.add(o);
                }
            }
            // add after shebang in top
            if (pos == -1) {
                if (!answer.isEmpty() && answer.get(0).trim().startsWith("///usr/bin/env jbang")) {
                    pos = 1;
                }
            }
            if (pos == -1) {
                pos = 0;
            }

            // reverse collection as we insert pos based
            List<String> depsToInsert = new ArrayList<>(deps);
            Collections.reverse(depsToInsert);
            for (String dep : depsToInsert) {
                answer.add(pos, dep);
            }

            String text = String.join(System.lineSeparator(), answer);
            Files.writeString(file, text);
        } catch (Exception e) {
            printer().printErr("Error updating source file: " + file + " due to: " + e.getMessage());
        }
    }

    /**
     * Syncs JBang source file dependencies: preserves non-Camel //DEPS, replaces Camel //DEPS with the resolved set.
     */
    private void syncJBangSource(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            List<String> answer = new ArrayList<>();

            // collect non-Camel DEPS and find insertion position
            List<String> nonCamelDeps = new ArrayList<>();
            int pos = -1;
            for (int i = 0; i < lines.size(); i++) {
                String o = lines.get(i);
                // remove leading comment chars to inspect
                String l = o.trim();
                while (l.startsWith("#")) {
                    l = l.substring(1);
                }
                if (l.startsWith("//DEPS ")) {
                    if (pos == -1) {
                        pos = i;
                    }
                    // check if this is a Camel dependency
                    String depPart = l.substring("//DEPS ".length()).trim();
                    if (!isCamelDependency(depPart)) {
                        nonCamelDeps.add(o);
                    }
                } else {
                    answer.add(o);
                }
            }
            // add after shebang in top
            if (pos == -1) {
                if (!answer.isEmpty() && answer.get(0).trim().startsWith("///usr/bin/env jbang")) {
                    pos = 1;
                }
            }
            if (pos == -1) {
                pos = 0;
            }

            // build combined deps: Camel deps (from resolved) + non-Camel deps (preserved)
            Set<String> seen = new LinkedHashSet<>();
            List<String> allDeps = new ArrayList<>();

            // add resolved Camel deps first
            for (String dep : deps) {
                if (seen.add(dep)) {
                    allDeps.add(dep);
                }
            }
            // add preserved non-Camel deps
            for (String dep : nonCamelDeps) {
                if (seen.add(dep)) {
                    allDeps.add(dep);
                }
            }

            // reverse collection as we insert pos based
            Collections.reverse(allDeps);
            for (String dep : allDeps) {
                answer.add(pos, dep);
            }

            String text = String.join(System.lineSeparator(), answer);
            Files.writeString(file, text);
        } catch (Exception e) {
            printer().printErr("Error updating source file: " + file + " due to: " + e.getMessage());
        }
    }

    /**
     * Strips Camel //DEPS lines from a JBang-style source file (in-place), preserving non-Camel //DEPS. This is used in
     * scan-routes mode to prevent stale Camel //DEPS from being picked up by the export pipeline.
     */
    private void stripJBangDeps(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            List<String> answer = new ArrayList<>();
            for (String line : lines) {
                String l = line.trim();
                while (l.startsWith("#")) {
                    l = l.substring(1);
                }
                if (l.startsWith("//DEPS ")) {
                    String depPart = l.substring("//DEPS ".length()).trim();
                    if (isCamelDependency(depPart)) {
                        // skip Camel deps — they will be re-added from route resolution
                        continue;
                    }
                }
                answer.add(line);
            }
            String text = String.join(System.lineSeparator(), answer);
            Files.writeString(file, text);
        } catch (Exception e) {
            printer().printErr("Error stripping //DEPS from: " + file + " due to: " + e.getMessage());
        }
    }

    /**
     * Check if a JBang dependency GAV string is a Camel dependency. Uses exact prefix "org.apache.camel:" (with colon)
     * because JBang files only use the org.apache.camel group (not springboot/quarkus variants). For Maven pom.xml, see
     * syncMavenSource which uses startsWith("org.apache.camel") without colon to also match org.apache.camel.springboot
     * and org.apache.camel.quarkus groups.
     */
    private static boolean isCamelDependency(String dep) {
        // handle @pom suffix
        String d = dep;
        if (d.endsWith("@pom")) {
            d = d.substring(0, d.length() - 4);
        }
        return d.startsWith("org.apache.camel:");
    }

    private void updateMavenSource(Path file) throws Exception {
        List<MavenGav> existingGavs = new ArrayList<>();

        int targetLineNumber = -1;

        if (Files.exists(file)) {
            // use line number parser as we want to find where to add new Camel JARs after the existing Camel JARs
            Document dom = XmlLineNumberParser.parseXml(Files.newInputStream(file));
            String camelVersion = null;
            NodeList nl = dom.getElementsByTagName("dependency");
            for (int i = 0; i < nl.getLength(); i++) {
                Element node = (Element) nl.item(i);

                // must be child at <project/dependencyManagement> or <project/dependencies>
                String p = node.getParentNode().getNodeName();
                String p2 = node.getParentNode().getParentNode().getNodeName();
                boolean accept = ("dependencyManagement".equals(p2) || "project".equals(p2)) && (p.equals("dependencies"));
                if (!accept) {
                    continue;
                }

                String g = node.getElementsByTagName("groupId").item(0).getTextContent();
                String a = node.getElementsByTagName("artifactId").item(0).getTextContent();
                String v = null;
                NodeList vl = node.getElementsByTagName("version");
                if (vl.getLength() > 0) {
                    v = vl.item(0).getTextContent();
                }
                String scope = null;
                vl = node.getElementsByTagName("scope");
                if (vl.getLength() > 0) {
                    scope = vl.item(0).getTextContent();
                }
                if (scope != null && !"compile".equals(scope)) {
                    continue;
                }

                if ("org.apache.camel".equals(g)) {
                    camelVersion = v;
                    String num = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER_END);
                    if (num != null) {
                        targetLineNumber = Integer.parseInt(num);
                    }
                }
                if ("org.apache.camel.springboot".equals(g)) {
                    camelVersion = v;
                    String num = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER_END);
                    if (num != null) {
                        targetLineNumber = Integer.parseInt(num);
                    }
                }
                if ("org.apache.camel.quarkus".equals(g)) {
                    String num = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER_END);
                    if (num != null) {
                        targetLineNumber = Integer.parseInt(num);
                    }
                }
                if (v != null) {
                    existingGavs.add(MavenGav.parseGav(g + ":" + a + ":" + v));
                } else {
                    existingGavs.add(MavenGav.parseGav(g + ":" + a));
                }
            }

            // find out which JARs are new
            List<MavenGav> updates = new ArrayList<>();
            for (MavenGav gav : gavs) {
                MavenGav target;
                if ("org.apache.camel.kamelets".equals(gav.getGroupId())) {
                    // special for kamelets (should be as-is)
                    target = gav;
                } else if (camelVersion != null) {
                    target = MavenGav.parseGav(gav.getGroupId() + ":" + gav.getArtifactId() + ":" + camelVersion);
                } else {
                    target = MavenGav.parseGav(gav.getGroupId() + ":" + gav.getArtifactId());
                }
                updates.add(target);
            }
            // sort the new JARs being added
            updates.sort(mavenGavComparator());

            if (scanRoutes) {
                // in scan-routes mode, sync Camel deps: add missing, remove unused
                syncMavenSource(file, dom, existingGavs, updates);
            } else {
                // default mode: only add new deps
                addMavenDeps(file, existingGavs, updates, targetLineNumber);
            }
        } else {
            outPrinter().println("pom.xml not found " + file.toAbsolutePath());
        }
    }

    private void addMavenDeps(Path file, List<MavenGav> existingGavs, List<MavenGav> updates, int targetLineNumber)
            throws Exception {
        List<MavenGav> toBeUpdated = new ArrayList<>();
        for (MavenGav update : updates) {
            if (!existingGavs.contains(update)) {
                toBeUpdated.add(update);
            }
        }

        if (!toBeUpdated.isEmpty() && targetLineNumber > 0) {
            String content = IOHelper.loadText(Files.newInputStream(file));
            String[] lines = content.split("\n");
            content = insertMavenDeps(lines, toBeUpdated, targetLineNumber);
            Files.writeString(file, content);
            int changes = toBeUpdated.size();
            if (changes > 1) {
                outPrinter().println("Updating pom.xml with " + changes + " dependencies added");
            } else {
                outPrinter().println("Updating pom.xml with 1 dependency added");
            }
        } else {
            outPrinter().println("No updates to pom.xml");
        }
    }

    private void syncMavenSource(
            Path file, Document dom, List<MavenGav> existingGavs, List<MavenGav> resolvedGavs)
            throws Exception {

        // determine which existing Camel deps are no longer needed
        Set<String> resolvedGAs = new LinkedHashSet<>();
        for (MavenGav gav : resolvedGavs) {
            resolvedGAs.add(gav.getGroupId() + ":" + gav.getArtifactId());
        }
        Set<String> existingGAs = new LinkedHashSet<>();
        for (MavenGav gav : existingGavs) {
            existingGAs.add(gav.getGroupId() + ":" + gav.getArtifactId());
        }

        // find Camel deps to remove (exist in pom but not in resolved set)
        List<String> toRemove = new ArrayList<>();
        for (MavenGav gav : existingGavs) {
            String ga = gav.getGroupId() + ":" + gav.getArtifactId();
            boolean isCamel = gav.getGroupId().startsWith("org.apache.camel");
            if (isCamel && !resolvedGAs.contains(ga)) {
                // skip BOM entries
                if (!"camel-bom".equals(gav.getArtifactId())
                        && !"camel-spring-boot-bom".equals(gav.getArtifactId())) {
                    toRemove.add(ga);
                }
            }
        }

        // find Camel deps to add (in resolved but not in pom)
        List<MavenGav> toAdd = new ArrayList<>();
        for (MavenGav gav : resolvedGavs) {
            String ga = gav.getGroupId() + ":" + gav.getArtifactId();
            if (!existingGAs.contains(ga)) {
                toAdd.add(gav);
            }
        }

        int added = toAdd.size();
        int removed = toRemove.size();

        if (added == 0 && removed == 0) {
            outPrinter().println("No updates to pom.xml");
            return;
        }

        // process file: remove unused Camel deps and add new ones
        String content = IOHelper.loadText(Files.newInputStream(file));
        String[] lines = content.split("\n");

        if (removed > 0) {
            content = removeMavenDeps(lines, dom, toRemove);
        }

        if (added > 0) {
            // re-parse to get updated line numbers after removals
            if (removed > 0) {
                lines = content.split("\n");
                Document updatedDom = XmlLineNumberParser.parseXml(
                        new java.io.ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                int insertLine = findLastCamelDependencyLine(updatedDom);
                if (insertLine > 0) {
                    content = insertMavenDeps(lines, toAdd, insertLine);
                }
            } else {
                int insertLine = findLastCamelDependencyLine(dom);
                if (insertLine > 0) {
                    content = insertMavenDeps(lines, toAdd, insertLine);
                }
            }
        }

        Files.writeString(file, content);

        StringBuilder msg = new StringBuilder("Updating pom.xml with ");
        if (added > 0) {
            msg.append(added).append(added == 1 ? " dependency added" : " dependencies added");
        }
        if (removed > 0) {
            if (added > 0) {
                msg.append(" and ");
            }
            msg.append(removed).append(removed == 1 ? " dependency removed" : " dependencies removed");
        }
        outPrinter().println(msg.toString());
    }

    private int findLastCamelDependencyLine(Document dom) {
        int targetLineNumber = -1;
        NodeList nl = dom.getElementsByTagName("dependency");
        for (int i = 0; i < nl.getLength(); i++) {
            Element node = (Element) nl.item(i);
            String p = node.getParentNode().getNodeName();
            String p2 = node.getParentNode().getParentNode().getNodeName();
            boolean accept = ("dependencyManagement".equals(p2) || "project".equals(p2)) && (p.equals("dependencies"));
            if (!accept) {
                continue;
            }
            String g = node.getElementsByTagName("groupId").item(0).getTextContent();
            if (g.startsWith("org.apache.camel")) {
                String num = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER_END);
                if (num != null) {
                    targetLineNumber = Integer.parseInt(num);
                }
            }
        }
        return targetLineNumber;
    }

    private String removeMavenDeps(String[] lines, Document dom, List<String> toRemove) {
        // find line ranges of dependencies to remove
        List<int[]> rangesToRemove = new ArrayList<>();
        NodeList nl = dom.getElementsByTagName("dependency");
        for (int i = 0; i < nl.getLength(); i++) {
            Element node = (Element) nl.item(i);
            String p = node.getParentNode().getNodeName();
            String p2 = node.getParentNode().getParentNode().getNodeName();
            boolean accept = ("dependencyManagement".equals(p2) || "project".equals(p2)) && (p.equals("dependencies"));
            if (!accept) {
                continue;
            }
            String g = node.getElementsByTagName("groupId").item(0).getTextContent();
            String a = node.getElementsByTagName("artifactId").item(0).getTextContent();
            String ga = g + ":" + a;
            if (toRemove.contains(ga)) {
                String startNum = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER);
                String endNum = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER_END);
                if (startNum != null && endNum != null) {
                    rangesToRemove.add(new int[] { Integer.parseInt(startNum), Integer.parseInt(endNum) });
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        boolean previousSkipped = false;
        for (int i = 0; i < lines.length; i++) {
            int lineNum = i + 1; // 1-based
            boolean skip = false;
            for (int[] range : rangesToRemove) {
                if (lineNum >= range[0] && lineNum <= range[1]) {
                    skip = true;
                    break;
                }
            }
            if (!skip) {
                // skip blank line immediately following a removed block to avoid double-blank-lines
                if (previousSkipped && lines[i].trim().isEmpty()) {
                    previousSkipped = false;
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(lines[i]);
            }
            previousSkipped = skip;
        }
        return sb.toString();
    }

    private String insertMavenDeps(String[] lines, List<MavenGav> toAdd, int targetLineNumber) {
        // respect indent from existing lines
        String line = lines[targetLineNumber - 1];
        String beforeTag = StringHelper.before(line, "<");
        int indent = beforeTag != null ? StringHelper.countChar(beforeTag, ' ') : 8;
        String pad = " ".repeat(indent);
        String line2 = targetLineNumber >= 2 ? lines[targetLineNumber - 2] : line;
        String beforeTag2 = StringHelper.before(line2, "<");
        int indent2 = beforeTag2 != null ? StringHelper.countChar(beforeTag2, ' ') : indent + 4;
        String pad2 = " ".repeat(indent2);

        StringJoiner sj = new StringJoiner("");
        for (MavenGav gav : toAdd) {
            sj.add("\n").add(pad).add("<dependency>\n");
            sj.add(pad2).add("<groupId>" + gav.getGroupId() + "</groupId>\n");
            sj.add(pad2).add("<artifactId>" + gav.getArtifactId() + "</artifactId>\n");
            if (gav.getVersion() != null) {
                sj.add(pad2).add("<version>" + gav.getVersion() + "</version>\n");
            }
            if (gav.getScope() != null) {
                sj.add(pad2).add("<scope>" + gav.getScope() + "</scope>\n");
            }
            sj.add(pad).add("</dependency>");
        }

        StringJoiner out = new StringJoiner("\n");
        for (int i = 0; i < lines.length; i++) {
            out.add(lines[i]);
            if (i == targetLineNumber - 1) {
                out.add(sj.toString());
            }
        }
        return out.toString();
    }

}
