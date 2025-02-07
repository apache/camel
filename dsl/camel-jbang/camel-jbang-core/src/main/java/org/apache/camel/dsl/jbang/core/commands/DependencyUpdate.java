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
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.xml.XmlLineNumberParser;
import org.apache.logging.log4j.util.Strings;
import picocli.CommandLine;

@CommandLine.Command(name = "update",
                     description = "Updates dependencies in Maven pom.xml or Java source file (JBang style)",
                     sortOptions = false,
                     showDefaultValues = true)
public class DependencyUpdate extends DependencyList {

    @CommandLine.Parameters(description = "Maven pom.xml or Java source files (JBang Style with //DEPS) to have dependencies updated", arity = "1")
    public File file;

    @CommandLine.Option(names = { "--clean" },
                        description = "Regenerate list of dependencies (do not keep existing dependencies). Not supported for pom.xml")
    protected boolean clean;

    private final List<String> deps = new ArrayList<>();
    private final List<MavenGav> gavs = new ArrayList<>();

    public DependencyUpdate(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // source file must exist
        if (!file.exists()) {
            printer().printErr("Source file does not exist: " + file);
            return -1;
        }

        boolean maven = "pom.xml".equals(file.getName());

        if (clean && !maven) {
            // remove DEPS in source file first
            updateJBangSource();
        }

        return super.doCall();
    }

    @Override
    protected void outputGav(MavenGav gav, int index, int total) {
        try {
            boolean maven = "pom.xml".equals(file.getName());
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
            updateMavenSource();
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
            updateJBangSource();
        }
    }

    private void updateJBangSource() {
        try {
            List<String> lines = Files.readAllLines(file.toPath());
            List<String> answer = new ArrayList<>();

            // find position of where the old DEPS was
            int pos = -1;
            for (int i = 0; i < lines.size(); i++) {
                String l = lines.get(i);
                if (l.trim().startsWith("//DEPS ")) {
                    if (pos == -1) {
                        pos = i;
                    }
                } else {
                    answer.add(l);
                }
            }
            // add after shebang in top
            if (pos == -1) {
                if (answer.get(0).trim().startsWith("///usr/bin/env jbang")) {
                    pos = 1;
                }
            }
            if (pos == -1) {
                pos = 0;
            }

            // reverse collection as we insert pos based
            Collections.reverse(deps);
            for (String dep : deps) {
                answer.add(pos, dep);
            }

            String text = String.join(System.lineSeparator(), answer);
            IOHelper.writeText(text, file);
        } catch (Exception e) {
            printer().printErr("Error updating source file: " + file + " due to: " + e.getMessage());
        }
    }

    private void updateMavenSource() throws Exception {
        List<MavenGav> existingGavs = new ArrayList<>();

        Node camelClone = null;
        int targetLineNumber = -1;

        File pom = new File(file.getName());
        if (pom.exists()) {
            // use line number parser as we want to find where to add new Camel JARs after the existing Camel JARs
            Document dom = XmlLineNumberParser.parseXml(new FileInputStream(pom));
            String camelVersion = null;
            NodeList nl = dom.getElementsByTagName("dependency");
            for (int i = 0; i < nl.getLength(); i++) {
                Element node = (Element) nl.item(i);

                // must be child at <project/dependencyManagement> or <project/dependencies>
                String p = node.getParentNode().getNodeName();
                String p2 = node.getParentNode().getParentNode().getNodeName();
                boolean accept = "project".equals(p2) && (p.equals("dependencyManagement") || p.equals("dependencies"));
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
                    if (camelClone == null && !"camel-bom".equals(a)) {
                        camelClone = node.cloneNode(true);
                    }
                    String num = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER_END);
                    if (num != null) {
                        targetLineNumber = Integer.parseInt(num);
                    }
                    num = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER_END);
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
                if (camelVersion != null) {
                    target = MavenGav.parseGav(gav.getGroupId() + ":" + gav.getArtifactId() + ":" + camelVersion);
                } else {
                    target = MavenGav.parseGav(gav.getGroupId() + ":" + gav.getArtifactId());
                }
                updates.add(target);
            }
            // sort the new JARs being added
            updates.sort(mavenGavComparator());
            List<MavenGav> toBeUpdated = new ArrayList<>();
            int changes = 0;
            for (MavenGav update : updates) {
                if (!existingGavs.contains(update)) {
                    toBeUpdated.add(update);
                    changes++;
                }
            }

            if (changes > 0) {
                // respect indent from existing GAVs
                String line = IOHelper.loadTextLine(new FileInputStream(file), targetLineNumber);
                line = StringHelper.before(line, "<");
                int indent = StringHelper.countChar(line, ' ');
                String pad = Strings.repeat(" ", indent);
                line = IOHelper.loadTextLine(new FileInputStream(file), targetLineNumber - 1);
                line = StringHelper.before(line, "<");
                int indent2 = StringHelper.countChar(line, ' ');
                String pad2 = Strings.repeat(" ", indent2);

                // build GAVs to be added to pom.xml
                StringJoiner sj = new StringJoiner("");
                for (MavenGav gav : toBeUpdated) {
                    sj.add(pad).add("<dependency>\n");
                    sj.add(pad2).add("<groupId>" + gav.getGroupId() + "</groupId>\n");
                    sj.add(pad2).add("<artifactId>" + gav.getArtifactId() + "</artifactId>\n");
                    if (gav.getVersion() != null) {
                        sj.add(pad2).add("<version>" + gav.getVersion() + "</version>\n");
                    }
                    sj.add(pad).add("</dependency>");
                }

                StringJoiner out = new StringJoiner("\n");
                String[] lines = IOHelper.loadText(new FileInputStream(file)).split("\n");
                for (int i = 0; i < lines.length; i++) {
                    String txt = lines[i];
                    out.add(txt);
                    if (i == targetLineNumber - 1) {
                        out.add(sj.toString());
                    }
                }
                if (changes > 1) {
                    outPrinter().println("Updating pom.xml with " + changes + " dependencies added");
                } else {
                    outPrinter().println("Updating pom.xml with 1 dependency added");
                }
                IOHelper.writeText(out.toString(), file);
            } else {
                outPrinter().println("No updates to pom.xml");
            }
        }
    }

}
