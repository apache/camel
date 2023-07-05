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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.catalog.VersionManager;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.MavenDownloader;
import org.apache.camel.tooling.maven.MavenDownloaderImpl;
import org.apache.camel.tooling.maven.MavenResolutionException;
import org.eclipse.aether.ConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link VersionManager} that can load the resources using Maven to download needed artifacts from a local or remote
 * Maven repository.
 * <p/>
 * This implementation uses Maven Resolver to download the Maven JARs.
 */
public class MavenVersionManager implements VersionManager, Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenVersionManager.class);

    private ClassLoader classLoader;
    private final OpenURLClassLoader helperClassLoader = new OpenURLClassLoader();

    private String version;
    private String runtimeProviderVersion;
    private String localRepository;
    private boolean log;

    private final MavenDownloader downloader;

    private final Map<String, String> repositories = new LinkedHashMap<>();

    private int connectTimeout = ConfigurationProperties.DEFAULT_CONNECT_TIMEOUT;
    private int requestTimeout = ConfigurationProperties.DEFAULT_REQUEST_TIMEOUT;

    private boolean customized;

    public MavenVersionManager() {
        downloader = new MavenDownloaderImpl();
        ((MavenDownloaderImpl) downloader).build();
    }

    @Override
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public ClassLoader getClassLoader() {
        if (classLoader != null) {
            return classLoader;
        }
        return helperClassLoader;
    }

    /**
     * Configures the directory for the download cache.
     * <p/>
     * The default folder is <tt>USER_HOME/.m2/repository</tt>
     *
     * @param directory the directory.
     */
    public void setCacheDirectory(String directory) {
        this.localRepository = directory;
        this.customized = true;
    }

    /**
     * Sets whether to log errors and warnings to System.out. By default nothing is logged.
     */
    public void setLog(boolean log) {
        this.log = log;
    }

    /**
     * Sets the connection timeout in millis when downloading via http/https protocols.
     * <p/>
     * The default value is 10000
     */
    public void setHttpClientTimeout(int timeout) {
        this.connectTimeout = timeout;
        this.customized = true;
    }

    /**
     * Sets the read timeout in millis when downloading via http/https protocols.
     * <p/>
     * The default value is 1800000
     */
    public void setHttpClientRequestTimeout(int timeout) {
        this.requestTimeout = timeout;
        this.customized = true;
    }

    /**
     * To add a 3rd party Maven repository.
     *
     * @param name the repository name
     * @param url  the repository url
     */
    public void addMavenRepository(String name, String url) {
        repositories.put(name, url);
    }

    @Override
    public String getLoadedVersion() {
        return version;
    }

    @Override
    public boolean loadVersion(String version) {
        try {
            MavenDownloader mavenDownloader = downloader;
            if (customized) {
                mavenDownloader = mavenDownloader.customize(localRepository, connectTimeout, requestTimeout);
            }

            String camelCatalogGAV = String.format("org.apache.camel:camel-catalog:%s", version);
            resolve(mavenDownloader, camelCatalogGAV, version.contains("SNAPSHOT"));

            this.version = version;
            return true;
        } catch (Exception e) {
            if (log) {
                LOGGER.warn("Cannot load version {} due {}", version, e.getMessage(), e);
            }
            return false;
        }
    }

    @Override
    public String getRuntimeProviderLoadedVersion() {
        return runtimeProviderVersion;
    }

    @Override
    public boolean loadRuntimeProviderVersion(String groupId, String artifactId, String version) {
        try {
            MavenDownloader mavenDownloader = downloader;
            if (customized) {
                mavenDownloader = mavenDownloader.customize(localRepository, connectTimeout, requestTimeout);
            }

            String gav = String.format("%s:%s:%s", groupId, artifactId, version);
            resolve(mavenDownloader, gav, version.contains("SNAPSHOT"));

            this.runtimeProviderVersion = version;
            return true;
        } catch (Exception e) {
            if (log) {
                LOGGER.warn("Cannot load runtime provider version {} due {}", version, e.getMessage(), e);
            }
            return false;
        }
    }

    /**
     * Resolves Maven artifact using passed coordinates and use downloaded artifact as one of the URLs in the
     * helperClassLoader, so further Catalog access may load resources from it.
     */
    private void resolve(MavenDownloader mavenDownloader, String gav, boolean useSnapshots)
            throws MavenResolutionException, MalformedURLException {
        Set<String> extraRepositories = new LinkedHashSet<>(repositories.values());

        // non-transitive resolve, because we load static data from the catalog artifacts
        List<MavenArtifact> artifacts = mavenDownloader.resolveArtifacts(Collections.singletonList(gav),
                extraRepositories, false, useSnapshots);

        for (MavenArtifact ma : artifacts) {
            helperClassLoader.addURL(ma.getFile().toURI().toURL());
        }
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        InputStream is = null;

        if (runtimeProviderVersion != null) {
            is = doGetResourceAsStream(name, runtimeProviderVersion);
        }
        if (is == null && version != null) {
            is = doGetResourceAsStream(name, version);
        }
        if (classLoader != null && is == null) {
            is = classLoader.getResourceAsStream(name);
        }
        if (is == null) {
            is = MavenVersionManager.class.getClassLoader().getResourceAsStream(name);
        }
        if (is == null) {
            is = helperClassLoader.getResourceAsStream(name);
        }

        return is;
    }

    private InputStream doGetResourceAsStream(String name, String version) {
        if (version == null) {
            return null;
        }

        try {
            URL found = null;
            Enumeration<URL> urls = helperClassLoader.getResources(name);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                if (url.getPath().contains(version)) {
                    found = url;
                    break;
                }
            }
            if (found != null) {
                return found.openStream();
            }
        } catch (IOException e) {
            if (log) {
                LOGGER.warn("Cannot open resource {} and version {} due {}", name, version, e.getMessage(), e);
            }
        }

        return null;
    }

    @Override
    public void close() {
    }
}
