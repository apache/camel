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
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.XmlHelper;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;

@CommandLine.Command(name = "runtime",
                     description = "Display Camel runtime and version for given Maven project", sortOptions = false,
                     showDefaultValues = true)
public class DependencyRuntime extends CamelCommand {

    @CommandLine.Option(names = { "--json" },
                        description = "Output in JSON Format")
    boolean jsonOutput;

    public DependencyRuntime(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // read pom.xml
        Path pom = Paths.get(".").resolve("pom.xml");
        if (Files.exists(pom)) {
            DocumentBuilderFactory dbf = XmlHelper.createDocumentBuilderFactory();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom = db.parse(Files.newInputStream(pom));
            NodeList nl = dom.getElementsByTagName("dependency");
            String camelVersion = null;
            String camelQuarkusVersion = null;
            String springBootVersion = null;
            String quarkusVersion = null;
            String quarkusGroupId = "io.quarkus.platform";
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
                if (("${quarkus.platform.group-id}".equals(g))) {
                    quarkusGroupId = dom.getElementsByTagName("quarkus.platform.group-id").item(0).getTextContent();
                }
            }

            String repos = null;
            StringJoiner sj = new StringJoiner(",");
            nl = dom.getElementsByTagName("repository");
            for (int i = 0; i < nl.getLength(); i++) {
                Element node = (Element) nl.item(i);

                // must be child at <repositories/repository>
                String p = node.getParentNode().getNodeName();
                boolean accept = "repositories".equals(p);
                if (!accept) {
                    continue;
                }
                String url = node.getElementsByTagName("url").item(0).getTextContent();
                sj.add(url);
            }
            if (sj.length() > 0) {
                repos = sj.toString();
            }

            // its a bit harder to know the camel version from Quarkus because of the universal BOM
            if (quarkusVersion != null && camelVersion == null) {
                CamelCatalog catalog = CatalogLoader.loadQuarkusCatalog(repos, quarkusVersion, quarkusGroupId);
                if (catalog != null) {
                    // find out the camel quarkus version via the constant language that are built-in camel-core
                    camelQuarkusVersion = catalog.languageModel("constant").getVersion();
                    // okay so the camel version is also hard to resolve from quarkus
                    camelVersion = CatalogLoader.resolveCamelVersionFromQuarkus(repos, camelQuarkusVersion);
                }
            }

            String runtime = "camel-main";
            if (springBootVersion != null) {
                runtime = "camel-spring-boot";
            } else if (quarkusVersion != null) {
                runtime = "camel-quarkus";
            }

            if (jsonOutput) {
                Map<String, String> map = new LinkedHashMap<>();
                map.put("runtime", runtime);
                if (camelVersion != null) {
                    map.put("camelVersion", camelVersion);
                }
                if (camelQuarkusVersion != null) {
                    map.put("camelQuarkusVersion", camelQuarkusVersion);
                }
                if (springBootVersion != null) {
                    map.put("springBootVersion", springBootVersion);
                }
                if (quarkusVersion != null) {
                    map.put("quarkusVersion", quarkusVersion);
                }
                printer().println(
                        Jsoner.serialize(map));
            } else {
                printer().println("Runtime: " + runtime);
                if (camelVersion != null) {
                    printer().println("Camel Version: " + camelVersion);
                }
                if (camelQuarkusVersion != null) {
                    printer().println("Camel Quarkus Version: " + camelQuarkusVersion);
                }
                if (springBootVersion != null) {
                    printer().println("Spring Boot Version: " + springBootVersion);
                } else if (quarkusVersion != null) {
                    printer().println("Quarkus Version: " + quarkusVersion);
                }
            }
        }

        return 0;
    }

}
