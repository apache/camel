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
package org.apache.camel.dsl.jbang.core.commands.update;

import java.util.List;

import org.apache.camel.dsl.jbang.core.common.RuntimeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.RuntimeTypeConverter;
import picocli.CommandLine;

public class CamelUpdateMixin {

    @CommandLine.Parameters(description = "The version to which the Camel project should be updated.", arity = "1")
    String version;

    @CommandLine.Option(names = { "--openRewriteVersion" },
                        description = "The version of OpenRewrite to use during the update process.",
                        defaultValue = "6.0.4")
    String openRewriteVersion;

    @CommandLine.Option(names = { "--camelArtifact" },
                        description = "The Maven artifact coordinates for the Camel upgrade recipes.",
                        defaultValue = "camel-upgrade-recipes")
    String camelArtifactCoordinates;

    @CommandLine.Option(names = { "--camelSpringBootArtifact" },
                        description = "The Maven artifact coordinates for the Camel Spring Boot upgrade recipes.",
                        defaultValue = "camel-spring-boot-upgrade-recipes")
    String camelSpringBootArtifactCoordinates;

    @CommandLine.Option(names = { "--debug" },
                        defaultValue = "false",
                        description = "Enables debug logging if set to true.")
    boolean debug;

    @CommandLine.Option(names = { "--quarkusMavenPluginVersion" },
                        description = "The version of the Quarkus Maven plugin to use.",
                        defaultValue = RuntimeType.QUARKUS_VERSION)
    String quarkusMavenPluginVersion;

    @CommandLine.Option(names = { "--quarkusMavenPluginGroupId" },
                        description = "The group ID of the Quarkus Maven plugin.",
                        defaultValue = "io.quarkus")
    String quarkusMavenPluginGroupId;

    @CommandLine.Option(names = { "--dryRun" },
                        description = "If set to true, performs a dry run of the update process without making any changes.",
                        defaultValue = "false")
    boolean dryRun;

    @CommandLine.Option(names = { "--runtime" },
                        completionCandidates = RuntimeCompletionCandidates.class,
                        defaultValue = "camel-main",
                        converter = RuntimeTypeConverter.class,
                        description = "Runtime (${COMPLETION-CANDIDATES})")
    RuntimeType runtime = RuntimeType.main;

    @CommandLine.Option(names = { "--repos" },
                        description = "Additional maven repositories for download on-demand (Use commas to separate multiple repositories)")
    String repos;

    @CommandLine.Option(names = { "--extraActiveRecipes" },
                        description = "Comma separated list of recipes to be executed after the Camel one, " +
                                      "make sure the artifact containing the recipes is added via extraRecipeArtifactCoordinates")
    List<String> extraActiveRecipes;

    @CommandLine.Option(names = { "--extraRecipeArtifactCoordinates" },
                        description = "Comma separated list of artifact coordinates containing extraActiveRecipes, " +
                                      "ex.my.org:recipes:1.0.0")
    List<String> extraRecipeArtifactCoordinates;

    @CommandLine.Option(names = { "--upgradeTimeout" },
                        description = "Time to wait, in seconds, before shutting down the upgrade process",
                        defaultValue = "240")
    int upgradeTimeout;
}
