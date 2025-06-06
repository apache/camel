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
import java.util.StringJoiner;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.dsl.jbang.core.common.RuntimeInformation;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.XmlHelper;
import picocli.CommandLine;

@CommandLine.Command(name = "runtime", description = "Display Camel runtime and version for given Maven project",
                     sortOptions = false, showDefaultValues = true)
public class DependencyRuntime extends CamelCommand {

    @CommandLine.Parameters(description = "The pom.xml to analyze.", arity = "1", paramLabel = "<pom.xml>")
    public Path pomXml;
    @CommandLine.Option(names = { "--json" }, description = "Output in JSON Format")
    boolean jsonOutput;

    public DependencyRuntime(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // read pom.xml
        if (!Files.exists(pomXml)) {
            printer().println(String.format("Cannot find %s", pomXml));
            return 1;
        }

        DocumentBuilderFactory dbf = XmlHelper.createDocumentBuilderFactory();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document dom = db.parse(Files.newInputStream(pomXml));
        NodeList nl = dom.getElementsByTagName("dependency");

        RuntimeInformation runtimeInformation = new RuntimeInformation();

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
                runtimeInformation.setCamelVersion(v);
                continue;
            }
            if ("org.apache.camel.springboot".equals(g) && "camel-spring-boot-bom".equals(a)) {
                runtimeInformation.setCamelVersion(v);
                continue;
            }
            if ("org.springframework.boot".equals(g) && "spring-boot-dependencies".equals(a)) {
                runtimeInformation.setSpringBootVersion(v);
                continue;
            }
            if (("${quarkus.platform.group-id}".equals(g) || "io.quarkus.platform".equals(g)) &&
                    ("${quarkus.platform.artifact-id}".equals(a) || "quarkus-bom".equals(a))) {
                if ("${quarkus.platform.version}".equals(v)) {
                    runtimeInformation.setQuarkusVersion(
                            dom.getElementsByTagName("quarkus.platform.version").item(0).getTextContent());
                } else {
                    runtimeInformation.setQuarkusVersion(v);
                }
                continue;
            }
            if (("${quarkus.platform.group-id}".equals(g))) {
                runtimeInformation.setQuarkusGroupId(
                        dom.getElementsByTagName("quarkus.platform.group-id").item(0).getTextContent());
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

        // it's a bit harder to know the camel version from Quarkus because of the universal BOM
        if (runtimeInformation.getQuarkusVersion() != null && runtimeInformation.getCamelVersion() == null) {
            CamelCatalog catalog = CatalogLoader.loadQuarkusCatalog(repos, runtimeInformation.getQuarkusVersion(),
                    runtimeInformation.getQuarkusGroupId());
            if (catalog != null) {
                // find out the camel quarkus version via the constant language that are built-in camel-core
                runtimeInformation.setCamelQuarkusVersion(catalog.languageModel("constant").getVersion());
                // okay so the camel version is also hard to resolve from quarkus
                runtimeInformation.setCamelVersion(CatalogLoader.resolveCamelVersionFromQuarkus(repos,
                        runtimeInformation.getCamelQuarkusVersion()));
            }
        }

        runtimeInformation.setType(RuntimeType.main);
        if (runtimeInformation.getSpringBootVersion() != null) {
            runtimeInformation.setType(RuntimeType.springBoot);
        } else if (runtimeInformation.getQuarkusVersion() != null) {
            runtimeInformation.setType(RuntimeType.quarkus);
        }

        if (jsonOutput) {
            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            String json = mapper.writeValueAsString(runtimeInformation);
            printer().println(json);
        } else {
            printer().println("Runtime: " + runtimeInformation.getType());
            if (runtimeInformation.getCamelVersion() != null) {
                printer().println("Camel Version: " + runtimeInformation.getCamelVersion());
            }
            if (runtimeInformation.getCamelQuarkusVersion() != null) {
                printer().println("Camel Quarkus Version: " + runtimeInformation.getCamelQuarkusVersion());
            }
            if (runtimeInformation.getSpringBootVersion() != null) {
                printer().println("Spring Boot Version: " + runtimeInformation.getSpringBootVersion());
            } else if (runtimeInformation.getQuarkusVersion() != null) {
                printer().println("Quarkus Version: " + runtimeInformation.getQuarkusVersion());
            }
        }

        return 0;
    }
}
