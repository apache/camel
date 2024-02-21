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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.core.common.XmlHelper;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.FileUtil;
import picocli.CommandLine;

@CommandLine.Command(name = "list",
                     description = "Displays all Camel dependencies required to run")
public class DependencyList extends Export {

    protected static final String EXPORT_DIR = CommandLineHelper.CAMEL_JBANG_WORK_DIR + "/export";

    @CommandLine.Option(names = { "--output" }, description = "Output format (gav, maven, jbang)", defaultValue = "gav")
    protected String output;

    public DependencyList(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        this.quiet = true; // be quiet and generate from fresh data to ensure the output is up-to-date
        return super.doCall();
    }

    @Override
    protected Integer export() throws Exception {
        if (!"gav".equals(output) && !"maven".equals(output) && !"jbang".equals(output)) {
            System.err.println("--output must be either gav or maven, was: " + output);
            return 1;
        }

        Integer answer = doExport();
        if (answer == 0) {
            // read pom.xml
            File pom = new File(EXPORT_DIR, "pom.xml");
            if (pom.exists()) {
                DocumentBuilderFactory dbf = XmlHelper.createDocumentBuilderFactory();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document dom = db.parse(pom);
                NodeList nl = dom.getElementsByTagName("dependency");
                List<MavenGav> gavs = new ArrayList<>();
                String camelVersion = null;
                String springBootVersion = null;
                String quarkusVersion = null;
                for (int i = 0; i < nl.getLength(); i++) {
                    Element node = (Element) nl.item(i);
                    String g = node.getElementsByTagName("groupId").item(0).getTextContent();
                    String a = node.getElementsByTagName("artifactId").item(0).getTextContent();
                    String v = null;
                    NodeList vl = node.getElementsByTagName("version");
                    if (vl.getLength() > 0) {
                        v = vl.item(0).getTextContent();
                    }

                    // BOMs
                    if ("org.apache.camel".equals(g) && "camel-bom".equals(a)) {
                        camelVersion = v;
                        continue;
                    }
                    if ("org.apache.camel.springboot".equals(g) && "camel-spring-boot-bom".equals(a)) {
                        camelVersion = v;
                        continue;
                    }
                    if ("org.springframework.boot".equals(g) && "spring-boot-dependencies".equals(a)) {
                        springBootVersion = v;
                        continue;
                    }
                    if (("${quarkus.platform.group-id}".equals(g) || "io.quarkus.platform".equals(g)) &&
                            ("${quarkus.platform.artifact-id}".equals(a) || "quarkus-bom".equals(a))) {
                        if ("${quarkus.platform.version}".equals(v)) {
                            quarkusVersion = dom.getElementsByTagName("quarkus.platform.version").item(0).getTextContent();
                        } else {
                            quarkusVersion = v;
                        }
                        continue;
                    }

                    // scope
                    String scope = null;
                    NodeList sl = node.getElementsByTagName("scope");
                    if (sl.getLength() > 0) {
                        scope = sl.item(0).getTextContent();
                    }
                    if ("test".equals(scope) || "import".equals(scope)) {
                        // skip test/BOM import scopes
                        continue;
                    }

                    // version
                    if (v == null && g.equals("org.apache.camel")) {
                        v = camelVersion;
                    }
                    if (v == null && g.equals("org.apache.camel.springboot")) {
                        v = camelVersion;
                    }
                    if (v == null && g.equals("org.springframework.boot")) {
                        v = springBootVersion;
                    }
                    if (v == null && (g.equals("io.quarkus") || g.equals("org.apache.camel.quarkus"))) {
                        v = quarkusVersion;
                    }

                    if (skipArtifact(g, a, v)) {
                        continue;
                    }
                    if (v != null) {
                        gavs.add(MavenGav.parseGav(g + ":" + a + ":" + v));
                    } else {
                        gavs.add(MavenGav.parseGav(g + ":" + a));
                    }
                }
                // sort GAVs
                gavs.sort(mavenGavComparator());
                int i = 0;
                int total = gavs.size();
                for (MavenGav gav : gavs) {
                    outputGav(gav, i, total);
                    i++;
                }
            }
            // cleanup dir after complete
            File buildDir = new File(EXPORT_DIR);
            FileUtil.removeDir(buildDir);
        }
        return answer;
    }

    protected void outputGav(MavenGav gav, int index, int total) {
        if ("gav".equals(output)) {
            printer().println(String.valueOf(gav));
        } else if ("maven".equals(output)) {
            printer().println("<dependency>");
            printer().printf("    <groupId>%s</groupId>%n", gav.getGroupId());
            printer().printf("    <artifactId>%s</artifactId>%n", gav.getArtifactId());
            printer().printf("    <version>%s</version>%n", gav.getVersion());
            printer().println("</dependency>");
        } else if ("jbang".equals(output)) {
            if (index == 0) {
                printer().println("//DEPS org.apache.camel:camel-bom:" + gav.getVersion() + "@pom");
            }
            if (gav.getGroupId().equals("org.apache.camel")) {
                // jbang has version in @pom so we should remove this
                gav.setVersion(null);
            }
            printer().println("//DEPS " + gav);
        }
    }

    protected Integer doExport() throws Exception {
        // read runtime and gav from profile if not configured
        File profile = new File(getProfile() + ".properties");
        if (profile.exists()) {
            Properties prop = new CamelCaseOrderedProperties();
            RuntimeUtil.loadProperties(prop, profile);
            if (this.runtime == null) {
                this.runtime = prop.getProperty("camel.jbang.runtime");
            }
            if (this.gav == null) {
                this.gav = prop.getProperty("camel.jbang.gav");
            }
            // allow configuring versions from profile
            this.javaVersion = prop.getProperty("camel.jbang.javaVersion", this.javaVersion);
            this.camelVersion = prop.getProperty("camel.jbang.camelVersion", this.camelVersion);
            this.kameletsVersion = prop.getProperty("camel.jbang.kameletsVersion", this.kameletsVersion);
            this.localKameletDir = prop.getProperty("camel.jbang.localKameletDir", this.localKameletDir);
            this.quarkusGroupId = prop.getProperty("camel.jbang.quarkusGroupId", this.quarkusGroupId);
            this.quarkusArtifactId = prop.getProperty("camel.jbang.quarkusArtifactId", this.quarkusArtifactId);
            this.quarkusVersion = prop.getProperty("camel.jbang.quarkusVersion", this.quarkusVersion);
            this.springBootVersion = prop.getProperty("camel.jbang.springBootVersion", this.springBootVersion);
        }

        // use temporary export dir
        exportDir = EXPORT_DIR;
        if (gav == null) {
            gav = "org.apache.camel:camel-jbang-dummy:1.0";
        }
        if (runtime == null) {
            runtime = "camel-main";
        }

        // turn off noise
        if ("spring-boot".equals(runtime) || "camel-spring-boot".equals(runtime)) {
            return export(new ExportSpringBoot(getMain()));
        } else if ("quarkus".equals(runtime) || "camel-quarkus".equals(runtime)) {
            return export(new ExportQuarkus(getMain()));
        } else if ("main".equals(runtime) || "camel-main".equals(runtime)) {
            return export(new ExportCamelMain(getMain()));
        } else {
            System.err.println("Unknown runtime: " + runtime);
            return 1;
        }
    }

    protected boolean skipArtifact(String groupId, String artifactId, String version) {
        // skip jansi which is used for color logging
        if ("org.fusesource.jansi".equals(groupId)) {
            return true;
        }
        // skip logging framework
        if ("org.apache.logging.log4j".equals(groupId)) {
            return true;
        }

        return false;
    }

}
