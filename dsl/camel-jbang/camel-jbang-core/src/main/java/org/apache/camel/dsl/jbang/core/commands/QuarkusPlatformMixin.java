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

import java.util.Properties;
import java.util.function.Function;

import org.apache.camel.dsl.jbang.core.common.QuarkusHelper;
import org.apache.camel.dsl.jbang.core.common.QuarkusHelper.QuarkusPlatformBom;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.MavenDownloader;
import org.apache.camel.tooling.maven.MavenGav;
import picocli.CommandLine;

import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.QUARKUS_EXTENSION_REGISTRY_BASE_URI;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.QUARKUS_GROUP_ID;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.QUARKUS_VERSION;

/**
 * Options related to Quarkus Platform.
 */
public class QuarkusPlatformMixin extends QuarkusExtensionRegistryMixin implements QuarkusPlatformMixinSpec {

    @CommandLine.Option(names = {
            "--quarkus-group-id" }, description = "groupId of Quarkus Platform BOM; honored only if --quarkus-version is set",
                        defaultValue = "io.quarkus.platform")
    String quarkusGroupId = "io.quarkus.platform";

    @CommandLine.Option(names = {
            "--quarkus-artifact-id" },
                        description = "Deprecated. This value is not used anymore. It is kept only for backwards compatibility and will be removed in Camel 5.x. Camel commands may use either 'quarkus-bom' or 'quarkus-camel-bom' artifactIds dependening on the context.",
                        defaultValue = "quarkus-bom")
    @Deprecated(forRemoval = true, since = "4.21.0")
    String quarkusArtifactId = "quarkus-bom";

    @CommandLine.Option(names = {
            "--quarkus-version" },
                        description = "version of Quarkus Platform BOM; the default value is looked up in Quarkus Extensio Registry")
    String quarkusVersion;

    /**
     * If {@link #quarkusVersion} is set, returns {@link QuarkusPlatformBom} with that version and
     * {@link #quarkusGroupId}; otherwise queries Quarkus Extension Registry for the Quarkus Platform version best
     * matching the specified {@code camelVersion}.
     *
     * @param  camelVersion    version of Camel either specified via {@code --camel-version} or the version of the
     *                         currently running Camel JBang
     * @param  mavenDownloader the {@link MavenDownloader} to use for resolving Maven Artifacts
     * @return                 a new {@link QuarkusPlatformBom}
     */
    public QuarkusPlatformBom resolve(String camelVersion, Function<MavenGav, MavenArtifact> mavenDownloader) {
        if (quarkusVersion != null) {
            MavenGav quarkusCamelBom
                    = MavenGav.fromCoordinates(quarkusGroupId, "quarkus-camel-bom", quarkusVersion, "pom", null);
            String cv = QuarkusHelper.resolveCamelVersionFromQuarkusCamelBom(quarkusCamelBom, mavenDownloader);
            return new QuarkusPlatformBom(quarkusGroupId, quarkusVersion, cv, quarkusExtensioRegistryBaseUri);
        } else {
            return QuarkusHelper.findQuarkusPlatformBom(camelVersion, mavenDownloader, quarkusExtensioRegistryBaseUri());
        }
    }

    /**
     * Load a QuarkusPlatformMixin from the specified {@link Properties} or the specified
     * {@link QuarkusPlatformMixinSpec} {@code fallback}.
     *
     * @param  props    the {@link Properties} from which the {@link QuarkusPlatformMixin} should be loaded
     * @param  fallback the {@link QuarkusPlatformMixinSpec} from which the values should be taken which are unavailable
     *                  in the given {@code Properties}
     * @return          a new {@link QuarkusPlatformMixin}
     */
    public static QuarkusPlatformMixin of(Properties props, QuarkusPlatformMixinSpec fallback) {
        QuarkusPlatformMixin result = new QuarkusPlatformMixin();
        result.quarkusGroupId = props.getProperty(QUARKUS_GROUP_ID, fallback.quarkusGroupId());
        result.quarkusVersion = props.getProperty(QUARKUS_VERSION, fallback.quarkusVersion());
        result.quarkusExtensioRegistryBaseUri
                = props.getProperty(QUARKUS_EXTENSION_REGISTRY_BASE_URI, fallback.quarkusExtensioRegistryBaseUri());
        return result;
    }

    /**
     * WARNING: you should prefer calling {@link #resolve(String, Function)} to get the Quarkus Platform version and
     * groupId matching the given Camel version.
     *
     * @return the groupId of Quarkus Platform if set or {@code null} otherwise
     */
    @Override
    public String quarkusGroupId() {
        return quarkusGroupId;
    }

    /**
     * WARNING: you should prefer calling {@link #resolve(String, Function)} to get the Quarkus Platform version
     * matching the given Camel version.
     *
     * @return the version of Quarkus Platform if set or {@code null} otherwise
     */
    @Override
    public String quarkusVersion() {
        return quarkusVersion;
    }

}
