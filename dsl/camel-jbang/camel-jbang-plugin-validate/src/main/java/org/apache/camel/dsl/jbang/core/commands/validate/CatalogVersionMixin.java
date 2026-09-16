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
package org.apache.camel.dsl.jbang.core.commands.validate;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.MavenResolverMixin;
import org.apache.camel.dsl.jbang.core.commands.QuarkusPlatformMixin;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.dsl.jbang.core.common.QuarkusHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.RuntimeTypeConverter;
import org.apache.camel.dsl.yaml.validator.YamlValidator;
import picocli.CommandLine;

/**
 * The Camel version and runtime a validation answers for (CAMEL-24711): the catalog of that version and runtime, as
 * {@code camel catalog} and {@code camel doc} load it, and the YAML DSL schema of that Camel version read from its
 * {@code camel-yaml-dsl} jar. Without options the CLI's own version, with nothing to download.
 */
public class CatalogVersionMixin {

    @CommandLine.Option(names = { "--camel-version" },
                        description = "Validate against another Camel version than the CLI's own: its catalog and YAML DSL schema")
    String camelVersion;

    @CommandLine.Option(names = { "--runtime" },
                        completionCandidates = RuntimeCompletionCandidates.class,
                        converter = RuntimeTypeConverter.class,
                        description = "Runtime (${COMPLETION-CANDIDATES}); spring-boot and quarkus use the catalog of that"
                                      + " runtime, so a component without a starter or extension is an error")
    RuntimeType runtime;

    @CommandLine.Mixin
    MavenResolverMixin mavenResolver;

    @CommandLine.Mixin
    QuarkusPlatformMixin quarkusPlatform;

    /** The catalog and the Camel version it answers for. */
    public record Loaded(CamelCatalog catalog, String camelVersion) {
    }

    /**
     * Loads the catalog of the version and runtime asked for; the built-in catalog when none is.
     */
    public Loaded load() throws Exception {
        if (RuntimeType.springBoot == runtime) {
            String version = camelVersion != null ? camelVersion : new DefaultCamelCatalog().getCatalogVersion();
            return new Loaded(
                    CatalogLoader.loadSpringBootCatalog(mavenResolver.repos(), version, mavenResolver.download()), version);
        }
        if (RuntimeType.quarkus == runtime) {
            QuarkusHelper.QuarkusPlatformBom bom = quarkusPlatform.resolve(camelVersion,
                    mavenResolver.downloader()::resolveArtifact, mavenResolver.download(), mavenResolver.fresh());
            CamelCatalog catalog
                    = CatalogLoader.loadQuarkusCatalog(bom.quarkusCamelBom(), mavenResolver.downloader()::resolveArtifact);
            // the Camel version the platform pins is the one the schema is read for
            String version = bom.camelVersion() != null ? bom.camelVersion() : camelVersion;
            return new Loaded(catalog, version);
        }
        if (camelVersion == null || camelVersion.isBlank()) {
            return new Loaded(new DefaultCamelCatalog(), null);
        }
        return new Loaded(
                CatalogLoader.loadCatalog(mavenResolver.repos(), camelVersion, mavenResolver.download()),
                camelVersion);
    }

    /**
     * The YAML DSL schema validator of the loaded Camel version: the CLI's own schema for the CLI's version, else the
     * schema read from the {@code camel-yaml-dsl} jar of that version.
     *
     * @throws Exception when the jar cannot be downloaded, or the version has no canonical schema (before 4.22)
     */
    public YamlValidator yamlValidator(Loaded loaded, boolean canonical) throws Exception {
        String schema = CatalogLoader.loadYamlDslSchema(mavenResolver.repos(), loaded.camelVersion(), canonical,
                mavenResolver.download());
        YamlValidator validator = new YamlValidator(canonical, schema, loaded.catalog());
        validator.init();
        return validator;
    }
}
