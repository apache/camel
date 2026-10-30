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
package org.apache.camel.dsl.jbang.export.common;

import java.util.Properties;
import java.util.function.Function;

import org.apache.camel.dsl.jbang.export.common.QuarkusHelper.QuarkusPlatformBom;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.MavenGav;

import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.QUARKUS_EXTENSION_REGISTRY_BASE_URI;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.QUARKUS_GROUP_ID;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.QUARKUS_VERSION;

/**
 * The Quarkus Platform to export to: the groupId and version of its BOM, and the Quarkus Extension Registry where the
 * version matching a Camel version is looked up when no version is given.
 */
public class QuarkusPlatform implements QuarkusPlatformMixinSpec {

    public static final String DEFAULT_GROUP_ID = "io.quarkus.platform";

    private final String quarkusGroupId;
    private final String quarkusVersion;
    private final String quarkusExtensionRegistryBaseUri;

    public QuarkusPlatform() {
        this(DEFAULT_GROUP_ID, null, null);
    }

    /**
     * @param quarkusGroupId                  groupId of the Quarkus Platform BOM; honored only if a version is set
     * @param quarkusVersion                  version of the Quarkus Platform BOM, or null to look it up in the Quarkus
     *                                        Extension Registry
     * @param quarkusExtensionRegistryBaseUri the base URI of the Quarkus Extension Registry, or null for the default
     */
    public QuarkusPlatform(String quarkusGroupId, String quarkusVersion, String quarkusExtensionRegistryBaseUri) {
        this.quarkusGroupId = quarkusGroupId;
        this.quarkusVersion = quarkusVersion;
        this.quarkusExtensionRegistryBaseUri = quarkusExtensionRegistryBaseUri;
    }

    /**
     * If {@link #quarkusVersion} is set, returns {@link QuarkusPlatformBom} with that version and
     * {@link #quarkusGroupId}; otherwise queries Quarkus Extension Registry for the Quarkus Platform version best
     * matching the specified {@code camelVersion}.
     *
     * @param  camelVersion    version of Camel either specified via {@code --camel-version} or the version of the
     *                         currently running Camel CLI
     * @param  mavenDownloader the function to use for resolving Maven Artifacts
     * @return                 a new {@link QuarkusPlatformBom}
     */
    public QuarkusPlatformBom resolve(
            String camelVersion,
            Function<MavenGav, MavenArtifact> mavenDownloader,
            boolean download,
            boolean fresh) {
        if (quarkusVersion != null) {
            MavenGav quarkusCamelBom
                    = MavenGav.fromCoordinates(quarkusGroupId, "quarkus-camel-bom", quarkusVersion, "pom", null);
            String cv = QuarkusHelper.resolveCamelVersionFromQuarkusCamelBom(quarkusCamelBom, mavenDownloader);
            return new QuarkusPlatformBom(quarkusGroupId, quarkusVersion, cv, quarkusExtensionRegistryBaseUri);
        } else {
            return QuarkusHelper.findQuarkusPlatformBom(
                    camelVersion,
                    mavenDownloader,
                    download,
                    quarkusExtensionRegistryBaseUri(),
                    fresh);
        }
    }

    /**
     * Load a QuarkusPlatform from the specified {@link Properties} or the specified {@link QuarkusPlatformMixinSpec}
     * {@code fallback}.
     *
     * @param  props    the {@link Properties} from which the {@link QuarkusPlatform} should be loaded
     * @param  fallback the {@link QuarkusPlatformMixinSpec} from which the values should be taken which are unavailable
     *                  in the given {@code Properties}
     * @return          a new {@link QuarkusPlatform}
     */
    public static QuarkusPlatform of(Properties props, QuarkusPlatformMixinSpec fallback) {
        String groupId = props.getProperty(QUARKUS_GROUP_ID, fallback.quarkusGroupId());
        String version = props.getProperty(QUARKUS_VERSION, fallback.quarkusVersion());
        // Prefer the canonical camel.jbang.quarkusExtensionRegistryBaseUri property; also honour the legacy
        // camel.jbang.quarkus.platform.url property (stripping its "/client/platforms" suffix if present)
        // so that users who set it in application.properties are not silently ignored.
        String registryBaseUri = props.getProperty(QUARKUS_EXTENSION_REGISTRY_BASE_URI);
        if (registryBaseUri == null) {
            String platformUrl = props.getProperty(QuarkusHelper.QUARKUS_PLATFORM_URL_PROPERTY);
            if (platformUrl != null) {
                final String suffix = "/client/platforms";
                if (platformUrl.endsWith(suffix)) {
                    platformUrl = platformUrl.substring(0, platformUrl.length() - suffix.length());
                }
                registryBaseUri = platformUrl;
            }
        }
        // strip trailing slash, consistent with quarkusExtensionRegistryBaseUri()
        if (registryBaseUri != null && registryBaseUri.endsWith("/")) {
            registryBaseUri = registryBaseUri.substring(0, registryBaseUri.length() - 1);
        }
        return new QuarkusPlatform(
                groupId, version, registryBaseUri != null ? registryBaseUri : fallback.quarkusExtensionRegistryBaseUri());
    }

    /**
     * The base URI of the Quarkus Extension Registry: the given one, else
     * {@link RuntimeType#QUARKUS_EXTENSION_REGISTRY_BASE_URL}; without a trailing slash.
     */
    public static String extensionRegistryBaseUri(String quarkusExtensionRegistryBaseUri) {
        String result = quarkusExtensionRegistryBaseUri;
        if (result == null) {
            result = RuntimeType.QUARKUS_EXTENSION_REGISTRY_BASE_URL;
        }
        if (result.endsWith("/")) {
            return result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * WARNING: you should prefer calling {@link #resolve(String, Function, boolean, boolean)} to get the Quarkus
     * Platform version and groupId matching the given Camel version.
     *
     * @return the groupId of Quarkus Platform if set or {@code null} otherwise
     */
    @Override
    public String quarkusGroupId() {
        return quarkusGroupId;
    }

    /**
     * WARNING: you should prefer calling {@link #resolve(String, Function, boolean, boolean)} to get the Quarkus
     * Platform version matching the given Camel version.
     *
     * @return the version of Quarkus Platform if set or {@code null} otherwise
     */
    @Override
    public String quarkusVersion() {
        return quarkusVersion;
    }

    @Override
    public String quarkusExtensionRegistryBaseUri() {
        return extensionRegistryBaseUri(quarkusExtensionRegistryBaseUri);
    }
}
