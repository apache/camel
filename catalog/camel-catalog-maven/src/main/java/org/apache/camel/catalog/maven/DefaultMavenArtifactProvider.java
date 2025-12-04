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

package org.apache.camel.catalog.maven;

import static org.apache.camel.catalog.maven.ComponentArtifactHelper.extractComponentJavaType;
import static org.apache.camel.catalog.maven.ComponentArtifactHelper.loadComponentJSonSchema;
import static org.apache.camel.catalog.maven.ComponentArtifactHelper.loadComponentProperties;
import static org.slf4j.helpers.NOPLogger.NOP_LOGGER;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.MavenDownloader;
import org.apache.camel.tooling.maven.MavenDownloaderImpl;
import org.eclipse.aether.ConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link MavenArtifactProvider} which uses Groovy Grape to download the artifact.
 *
 * @deprecated use {@code org.apache.camel.tooling.maven.support.MavenDownloader} from {@code camel-tooling-maven}
 *             instead.
 */
@Deprecated(since = "4.6.0")
public class DefaultMavenArtifactProvider implements MavenArtifactProvider {

    private String localRepository;
    private Logger logger;

    private final MavenDownloader downloader;

    private final Map<String, String> repositories = new LinkedHashMap<>();

    public DefaultMavenArtifactProvider() {
        downloader = new MavenDownloaderImpl();
        downloader.build();
        setLog(true);
    }

    /**
     * Sets whether to log errors and warnings to System.out. By default nothing is logged.
     */
    public void setLog(boolean log) {
        setLogger(log ? LoggerFactory.getLogger(DefaultMavenArtifactProvider.class) : null);
    }

    public void setLogger(Logger logger) {
        this.logger = logger != null ? logger : NOP_LOGGER;
    }

    @Override
    public void setCacheDirectory(String directory) {
        this.localRepository = directory;
    }

    @Override
    public void addMavenRepository(String name, String url) {
        repositories.put(name, url);
    }

    @Override
    public Set<String> addArtifactToCatalog(
            CamelCatalog camelCatalog, String groupId, String artifactId, String version) {
        final Set<String> names = new LinkedHashSet<>();

        try {
            MavenDownloader mavenDownloader = downloader;
            if (localRepository != null) {
                logger.debug("Using cache directory: {}", localRepository);
                // customize only local repository
                mavenDownloader = mavenDownloader.customize(
                        localRepository,
                        ConfigurationProperties.DEFAULT_CONNECT_TIMEOUT,
                        ConfigurationProperties.DEFAULT_REQUEST_TIMEOUT);
            }

            logger.info("Downloading {}:{}:{}", groupId, artifactId, version);

            try (OpenURLClassLoader classLoader = new OpenURLClassLoader()) {
                if (version == null || version.isBlank()) {
                    version = "LATEST";
                }
                String gav = String.format("%s:%s:%s", groupId, artifactId, version);
                Set<String> extraRepositories = new LinkedHashSet<>(repositories.values());
                List<MavenArtifact> artifacts = mavenDownloader.resolveArtifacts(
                        Collections.singletonList(gav), extraRepositories, false, version.contains("SNAPSHOT"));

                for (MavenArtifact ma : artifacts) {
                    classLoader.addURL(ma.getFile().toURI().toURL());
                }

                // the classloader can load content from the downloaded JAR
                if (camelCatalog != null) {
                    scanCamelComponents(camelCatalog, classLoader, names);
                }
            }

        } catch (Exception e) {
            logger.warn(
                    "Error during add components from artifact {}:{}:{} due {}",
                    groupId,
                    artifactId,
                    version,
                    e.getMessage(),
                    e);
        }

        return names;
    }

    protected void scanCamelComponents(CamelCatalog camelCatalog, ClassLoader classLoader, Set<String> names) {
        // is there any custom Camel components in this library?
        Properties properties = loadComponentProperties(classLoader, logger);
        String components = (String) properties.get("components");
        if (components != null) {
            String[] part = components.split("\\s");
            for (String scheme : part) {
                if (!camelCatalog.findComponentNames().contains(scheme)) {
                    findClassName(camelCatalog, classLoader, names, scheme);
                }
            }
        }
    }

    private void findClassName(CamelCatalog camelCatalog, ClassLoader classLoader, Set<String> names, String scheme) {
        // find the class name
        String javaType = extractComponentJavaType(classLoader, scheme, logger);
        if (javaType != null) {
            String json = loadComponentJSonSchema(classLoader, scheme, logger);
            if (json != null) {
                logger.info("Adding component: {}", scheme);
                camelCatalog.addComponent(scheme, javaType, json);
                names.add(scheme);
            }
        }
    }
}
