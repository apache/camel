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
import java.util.List;
import java.util.StringJoiner;

import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.dsl.jbang.core.common.QuarkusHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.model.DependencyRuntimeDTO;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.json.Jsoner;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import picocli.CommandLine;

@CommandLine.Command(name = "runtime", description = "Display Camel runtime and version for given Maven project",
                     sortOptions = false, showDefaultValues = true)
public class DependencyRuntime extends CamelCommand {

    @CommandLine.Parameters(description = "The pom.xml to analyze", arity = "1", paramLabel = "<pom.xml>")
    Path pomXml;

    @CommandLine.Mixin
    MavenResolverMixin mavenResolver;

    @CommandLine.Option(names = { "--json" }, description = "Output in JSON Format")
    boolean jsonOutput;

    public DependencyRuntime(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // read pom.xml
        if (!Files.exists(pomXml)) {
            printer().printErr(String.format("Cannot find %s", pomXml));
            return 1;
        }

        Model model = RunHelper.loadMavenModel(pomXml);
        if (model == null) {
            return 0;
        }

        List<String> deps = RunHelper.scanMavenDependenciesFromModel(pomXml, model, true);
        if (deps.isEmpty()) {
            return 0;
        }

        String camelVersion = null;
        String camelSpringBootVersion = null;
        String springBootVersion = null;
        String quarkusVersion = null;
        String quarkusBomGroupId = null;
        String camelSpringBootBomGroupId = null;
        String camelSpringBootBomArtifactId = null;

        for (String dep : deps) {
            MavenGav gav = MavenGav.parseGav(dep);
            if (camelVersion == null && "org.apache.camel".equals(gav.getGroupId())) {
                camelVersion = gav.getVersion();
            }
            if (camelSpringBootVersion == null && "org.apache.camel.springboot".equals(gav.getGroupId())) {
                camelSpringBootVersion = gav.getVersion();
            }
            if (springBootVersion == null && "org.springframework.boot".equals(gav.getGroupId())) {
                springBootVersion = gav.getVersion();
            }
            if (quarkusVersion == null && ("quarkus-camel-bom".equals(gav.getArtifactId()))) {
                quarkusBomGroupId = gav.getGroupId();
                quarkusVersion = gav.getVersion();
            }
            if (camelSpringBootBomGroupId == null && "camel-spring-boot-bom".equals(gav.getArtifactId())) {
                camelSpringBootBomGroupId = gav.getGroupId();
                camelSpringBootBomArtifactId = gav.getArtifactId();
            }
        }

        if (springBootVersion == null && camelSpringBootVersion != null) {
            springBootVersion = CatalogLoader.resolveSpringBootVersionFromCamelSpringBoot(
                    mavenRepos(model, mavenResolver.repos()),
                    camelSpringBootVersion,
                    mavenResolver.download());
        }
        if (camelSpringBootVersion != null && camelVersion == null) {
            camelVersion = CatalogLoader.resolveCamelVersionFromSpringBoot(mavenRepos(model, mavenResolver.repos()),
                    camelSpringBootVersion, mavenResolver.download());
        }
        if (quarkusVersion != null && camelVersion == null) {
            MavenGav camelQuarkusBom
                    = MavenGav.fromCoordinates(quarkusBomGroupId, "quarkus-camel-bom", quarkusVersion, "pom", null);
            camelVersion = QuarkusHelper.resolveCamelVersionFromQuarkusCamelBom(camelQuarkusBom,
                    mavenResolver.downloader()::resolveArtifact);
        }

        String runtime = RuntimeType.main.runtime();
        if (springBootVersion != null) {
            runtime = RuntimeType.springBoot.runtime();
        } else if (quarkusVersion != null) {
            runtime = RuntimeType.quarkus.runtime();
        }

        if (jsonOutput) {
            DependencyRuntimeDTO dto = new DependencyRuntimeDTO(
                    runtime,
                    camelVersion,
                    camelSpringBootVersion,
                    springBootVersion,
                    quarkusVersion,
                    camelSpringBootBomGroupId,
                    camelSpringBootBomArtifactId,
                    quarkusBomGroupId);
            printer().println(Jsoner.serialize(dto.toMap()));
        } else {
            printer().println("Runtime: " + runtime);
            if (camelVersion != null) {
                printer().println("Camel Version: " + camelVersion);
            }
            if (camelSpringBootVersion != null) {
                printer().println("Camel Spring Boot Version: " + camelSpringBootVersion);
            }
            if (springBootVersion != null) {
                printer().println("Spring Boot Version: " + springBootVersion);
            } else if (quarkusVersion != null) {
                printer().println("Quarkus Version: " + quarkusVersion);
            }
        }

        return 0;
    }

    private static String mavenRepos(Model model, String repositories) {
        StringJoiner sj = new StringJoiner(",");
        for (Repository r : model.getRepositories()) {
            sj.add(r.getUrl());
        }
        String answer = sj.length() > 0 ? sj.toString() : null;
        if (repositories != null) {
            if (answer == null) {
                answer = repositories;
            } else {
                answer += "," + repositories;
            }
        }
        return answer;
    }
}
