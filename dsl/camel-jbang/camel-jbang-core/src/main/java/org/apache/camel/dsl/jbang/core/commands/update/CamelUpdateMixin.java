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

import org.aesh.command.option.Argument;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;
import org.apache.camel.dsl.jbang.core.commands.MavenResolverMixin;
import org.apache.camel.dsl.jbang.core.commands.QuarkusExtensionRegistryMixin;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.RuntimeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.RuntimeTypeConverter;
import org.apache.camel.dsl.jbang.run.common.QuarkusHelper;
import org.apache.camel.dsl.jbang.run.common.QuarkusHelper.QuarkusPlatformBom;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.camel.tooling.maven.MavenGav;

public class CamelUpdateMixin {

    @Argument(required = true, description = "The version to which the Camel project should be updated.")
    String version;

    @Option(name = "openRewriteVersion", description = "The version of OpenRewrite to use during the update process.",
            defaultValue = "6.0.4")
    String openRewriteVersion;

    @Option(name = "camelArtifact", description = "The Maven artifact coordinates for the Camel upgrade recipes.",
            defaultValue = "camel-upgrade-recipes")
    String camelArtifactCoordinates;

    @Option(name = "camelSpringBootArtifact",
            description = "The Maven artifact coordinates for the Camel Spring Boot upgrade recipes.",
            defaultValue = "camel-spring-boot-upgrade-recipes")
    String camelSpringBootArtifactCoordinates;

    @Option(name = "debug", hasValue = false, description = "Enables debug logging if set to true.", defaultValue = "false")
    boolean debug;

    @Option(name = "quarkusMavenPluginVersion",
            description = "The version of the Quarkus Maven plugin to use; the default value is looked up in Quarkus Extension Registry")
    String quarkusMavenPluginVersion;

    @Option(name = "quarkusMavenPluginGroupId", description = "The group ID of the Quarkus Maven plugin.",
            defaultValue = "io.quarkus.platform")
    String quarkusMavenPluginGroupId;

    @Option(name = "dryRun", hasValue = false,
            description = "If set to true, performs a dry run of the update process without making any changes.",
            defaultValue = "false")
    boolean dryRun;

    @Option(name = "runtime", description = "Runtime (${COMPLETION-CANDIDATES})", defaultValue = "camel-main",
            completer = RuntimeCompletionCandidates.class, converter = RuntimeTypeConverter.class)
    RuntimeType runtime = RuntimeType.main;

    @Mixin
    MavenResolverMixin mavenResolver;

    @OptionList(name = "extraActiveRecipes", valueSeparator = OptionList.NO_SEPARATOR,
                description = "Comma separated list of recipes to be executed after the Camel one, " +
                              "make sure the artifact containing the recipes is added via extraRecipeArtifactCoordinates")
    List<String> extraActiveRecipes;

    @OptionList(name = "extraRecipeArtifactCoordinates", valueSeparator = OptionList.NO_SEPARATOR,
                description = "Comma separated list of artifact coordinates containing extraActiveRecipes, " +
                              "ex.my.org:recipes:1.0.0")
    List<String> extraRecipeArtifactCoordinates;

    @Option(name = "upgradeTimeout", description = "Time to wait, in seconds, before shutting down the upgrade process",
            defaultValue = "240")
    int upgradeTimeout;

    @Mixin
    QuarkusExtensionRegistryMixin quarkusExtensionRegistry;

    public MavenGav resolveQuarkusMavenPlugin() {
        if (quarkusMavenPluginVersion != null) {
            return MavenGav.fromCoordinates(quarkusMavenPluginGroupId, "quarkus-maven-plugin", quarkusMavenPluginVersion, "jar",
                    null);
        } else if (!mavenResolver.download()) {
            throw CliException.usage("--quarkusMavenPluginVersion is required with --download=false, as the version of "
                                     + "the Quarkus Maven plugin is looked up in the Quarkus Extension Registry");
        } else {
            QuarkusPlatformBom result
                    = QuarkusHelper.findQuarkusPlatformBom(
                            version,
                            mavenResolver.downloader()::resolveArtifact,
                            mavenResolver.download(),
                            quarkusExtensionRegistry.quarkusExtensionRegistryBaseUri(),
                            mavenResolver.fresh());
            return result.quarkusMavenPlugin();
        }
    }
}
