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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.dsl.jbang.core.common.QuarkusHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.tooling.maven.MavenDownloader;
import org.apache.camel.tooling.maven.MavenDownloaderImpl;
import org.apache.camel.tooling.maven.MavenGav;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Shared service for loading and caching Camel catalogs.
 * <p>
 * All MCP tool classes that need a {@link CamelCatalog} should inject this service instead of creating their own
 * {@link DefaultCamelCatalog} instances. This ensures consistent version handling and avoids redundant Maven artifact
 * downloads when multiple tools query the same version.
 * <p>
 * Catalogs are cached by {@code (runtime, camelVersion, platformBom)} tuple. The default catalog (no version
 * parameters) is created once at startup.
 */
@ApplicationScoped
public class CatalogService {

    @ConfigProperty(name = "camel.catalog.repos")
    Optional<String> catalogRepos;

    private final CamelCatalog defaultCatalog;
    private final ConcurrentMap<CatalogKey, CamelCatalog> cache = new ConcurrentHashMap<>();
    private volatile MavenDownloader downloader;
    private final Object downloaderLock = new Object();

    public CatalogService() {
        this.defaultCatalog = new DefaultCamelCatalog(true);
    }

    /**
     * Get the default catalog (built-in version, no specific runtime or version requested).
     */
    public CamelCatalog getDefaultCatalog() {
        return defaultCatalog;
    }

    /**
     * Load a catalog for the given runtime, version, and platform BOM. Results are cached by the
     * {@code (runtime, camelVersion, platformBom)} tuple so that repeated calls with the same parameters do not trigger
     * redundant Maven downloads.
     *
     * @param  runtime      runtime type: "main", "spring-boot", or "quarkus" (default: main)
     * @param  camelVersion the Camel version to query, or null for the default
     * @param  platformBom  platform BOM in GAV format (groupId:artifactId:version), or null
     * @return              the loaded (and possibly cached) CamelCatalog
     * @throws Exception    if catalog loading fails
     */
    public CamelCatalog loadCatalog(String runtime, String camelVersion, String platformBom) throws Exception {
        RuntimeType runtimeType = resolveRuntime(runtime);

        boolean hasVersion = camelVersion != null && !camelVersion.isBlank();
        boolean hasBom = platformBom != null && !platformBom.isBlank();

        // No version-specific parameters and main runtime -> default catalog
        if (!hasVersion && !hasBom && runtimeType == RuntimeType.main) {
            return defaultCatalog;
        }

        // Normalize the cache key
        String normalizedVersion = hasVersion ? camelVersion : null;
        String normalizedBom = hasBom ? platformBom : null;

        // For non-main runtimes without explicit version, use the runtime's default version
        MavenGav platformBomGav = null;
        if (!hasVersion && !hasBom) {
            if (runtimeType == RuntimeType.quarkus) {
                platformBomGav
                        = QuarkusHelper.findQuarkusPlatformBom(
                                camelVersion,
                                downloader()::resolveArtifact,
                                RuntimeType.QUARKUS_EXTENSION_REGISTRY_BASE_URL).quarkusCamelBom();
                normalizedVersion = platformBomGav.getVersion();
            }
        }
        if (platformBomGav == null && platformBom != null) {
            String[] parts = platformBom.split(":");
            if (parts.length != 3) {
                throw new ToolCallException(
                        "platformBom must be in GAV format (groupId:artifactId:version), got: " + platformBom, null);
            }
            platformBomGav = MavenGav.fromCoordinates(parts[0], parts[1], parts[2], "pom", null);
        }

        CatalogKey key = new CatalogKey(runtimeType.name(), normalizedVersion, normalizedBom);

        CamelCatalog cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        CamelCatalog loaded = doLoadCatalog(runtimeType, camelVersion, platformBomGav);
        cache.putIfAbsent(key, loaded);
        return cache.get(key);
    }

    /**
     * Resolve a runtime string to a {@link RuntimeType} enum value.
     *
     * @param  runtime           the runtime string ("main", "spring-boot", "quarkus"), or null for main
     * @return                   the resolved RuntimeType
     * @throws ToolCallException if the runtime value is not recognized
     */
    public RuntimeType resolveRuntime(String runtime) {
        if (runtime == null || runtime.isBlank() || "main".equalsIgnoreCase(runtime)) {
            return RuntimeType.main;
        }
        try {
            return RuntimeType.fromValue(runtime);
        } catch (IllegalArgumentException e) {
            throw new ToolCallException(
                    "Unsupported runtime: " + runtime + ". Supported values are: main, spring-boot, quarkus", null);
        }
    }

    private CamelCatalog doLoadCatalog(
            RuntimeType runtimeType, String camelVersion, MavenGav platformBom)
            throws Exception {

        String repos = catalogRepos.orElse(null);

        // If platformBom is provided (GAV format), parse and use it
        if (platformBom != null) {

            if (runtimeType == RuntimeType.quarkus) {
                return CatalogLoader.loadQuarkusCatalog(platformBom, downloader()::resolveArtifact);
            } else if (runtimeType == RuntimeType.springBoot) {
                return CatalogLoader.loadSpringBootCatalog(repos, platformBom.getVersion(), platformBom.getGroupId(), true);
            } else {
                return CatalogLoader.loadCatalog(repos, platformBom.getVersion(), platformBom.getGroupId(), true);
            }
        }

        // If a specific version is requested, load that version's catalog
        if (camelVersion != null && !camelVersion.isBlank()) {
            if (runtimeType == RuntimeType.springBoot) {
                return CatalogLoader.loadSpringBootCatalog(repos, camelVersion, true);
            } else if (runtimeType == RuntimeType.quarkus) {
                return CatalogLoader.loadQuarkusCatalog(platformBom, downloader()::resolveArtifact);
            } else {
                return CatalogLoader.loadCatalog(repos, camelVersion, true);
            }
        }

        // No specific version, use runtime-specific default catalog
        if (runtimeType == RuntimeType.springBoot) {
            return CatalogLoader.loadSpringBootCatalog(repos, null, true);
        } else if (runtimeType == RuntimeType.quarkus) {
            return CatalogLoader.loadQuarkusCatalog(platformBom, downloader()::resolveArtifact);
        }

        return defaultCatalog;
    }

    MavenDownloader downloader() {
        MavenDownloader d;
        if ((d = downloader) == null) {
            synchronized (downloaderLock) {
                if ((d = downloader) == null) {
                    d = new MavenDownloaderImpl();
                    if (catalogRepos.isPresent()) {
                        d.setRepos(catalogRepos.get());
                    }
                    d.build();
                    downloader = d;
                }
            }
        }
        return d;
    }

    private record CatalogKey(String runtime, String camelVersion, String platformBom) {
    }
}
