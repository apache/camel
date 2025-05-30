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
package org.apache.camel.main.download;

import java.util.Collection;
import java.util.List;

import org.apache.camel.CamelContextAware;
import org.apache.camel.StaticService;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.RepositoryResolver;

/**
 * To download dependencies at runtime.
 */
public interface DependencyDownloader extends CamelContextAware, StaticService {

    /**
     * Classloader able to load from downloaded dependencies.
     */
    ClassLoader getClassLoader();

    /**
     * Sets the classloader to use that will be able to load from downloaded dependencies
     */
    void setClassLoader(ClassLoader classLoader);

    /**
     * Adds a listener to capture download activity
     */
    void addDownloadListener(DownloadListener downloadListener);

    /**
     * Adds a listener to capture download activity
     */
    void addArtifactDownloadListener(ArtifactDownloadListener downloadListener);

    String getRepositories();

    /**
     * Additional maven repositories for download on-demand (Use commas to separate multiple repositories).
     */
    void setRepositories(String repositories);

    /**
     * Whether downloading from remote Maven repositories is enabled
     */
    void setDownload(boolean download);

    /**
     * Whether downloading from remote Maven repositories is enabled
     */
    boolean isDownload();

    boolean isFresh();

    /**
     * Make sure we use fresh (i.e. non-cached) resources.
     */
    void setFresh(boolean fresh);

    String getMavenSettings();

    /**
     * Configure location of Maven settings.xml file
     */
    void setMavenSettings(String mavenSettings);

    String getMavenSettingsSecurity();

    /**
     * Configure location of Maven settings-security.xml file
     */
    void setMavenSettingsSecurity(String mavenSettingsSecurity);

    /**
     * Whether downloading JARs from Maven Central repository is enabled
     */
    boolean isMavenCentralEnabled();

    /**
     * Whether downloading JARs from Maven Central repository is enabled
     */
    void setMavenCentralEnabled(boolean mavenCentralEnabled);

    /**
     * Whether downloading JARs from ASF Maven Snapshot repository is enabled
     */
    boolean isMavenApacheSnapshotEnabled();

    /**
     * Whether downloading JARs from ASF Maven Snapshot repository is enabled
     */
    void setMavenApacheSnapshotEnabled(boolean mavenApacheSnapshotEnabled);

    /**
     * Downloads the dependency incl transitive dependencies
     *
     * @param parentGav  maven parent GAV
     * @param groupId    maven group id
     * @param artifactId maven artifact id
     * @param version    maven version
     */
    void downloadDependencyWithParent(String parentGav, String groupId, String artifactId, String version);

    /**
     * Downloads the dependency incl transitive dependencies
     *
     * @param groupId    maven group id
     * @param artifactId maven artifact id
     * @param version    maven version
     */
    void downloadDependency(String groupId, String artifactId, String version);

    /**
     * Downloads the dependency incl transitive dependencies
     *
     * @param groupId    maven group id
     * @param artifactId maven artifact id
     * @param version    maven version
     * @param extraRepos additional remote maven repositories to use when downloading
     */
    void downloadDependency(String groupId, String artifactId, String version, String extraRepos);

    /**
     * Downloads the dependency
     *
     * @param groupId      maven group id
     * @param artifactId   maven artifact id
     * @param version      maven version
     * @param transitively whether to include transitive dependencies
     */
    void downloadDependency(String groupId, String artifactId, String version, boolean transitively);

    /**
     * Downloads as hidden dependency that are not captured as a requirement
     *
     * @param groupId    maven group id
     * @param artifactId maven artifact id
     * @param version    maven version
     */
    void downloadHiddenDependency(String groupId, String artifactId, String version);

    /**
     * Downloads a single maven artifact (no transitive dependencies)
     *
     * @param  groupId    maven group id
     * @param  artifactId maven artifact id
     * @param  version    maven version
     * @return            the artifact, or null if none found
     */
    MavenArtifact downloadArtifact(String groupId, String artifactId, String version);

    /**
     * Downloads maven artifact (can also include transitive dependencies).
     *
     * @param  groupId      maven group id
     * @param  artifactId   maven artifact id
     * @param  version      maven version
     * @param  transitively whether to include transitive dependencies
     * @return              the artifacts, or null if none found
     */
    List<MavenArtifact> downloadArtifacts(String groupId, String artifactId, String version, boolean transitively);

    /**
     * Resolves the available versions for the given maven artifact
     *
     * @param  groupId        maven group id
     * @param  artifactId     maven artifact id
     * @param  minimumVersion optional minimum version to avoid resolving too old releases
     * @param  repo           to use specific maven repository instead of maven central (used if repo is {@code null})
     * @return                list of versions of the given artifact (0=camel-core version, 1=runtime version, such as
     *                        spring-boot or quarkus)
     */
    List<String[]> resolveAvailableVersions(String groupId, String artifactId, String minimumVersion, String repo);

    /**
     * Checks whether the dependency is already on the classpath
     *
     * @param  groupId    maven group id
     * @param  artifactId maven artifact id
     * @param  version    maven version
     * @return            true if already on classpath, false if not.
     */
    boolean alreadyOnClasspath(String groupId, String artifactId, String version);

    /**
     * When a kamelet is being loaded
     *
     * @param name the kamelet name
     */
    void onLoadingKamelet(String name);

    /**
     * Gets download record for a given artifact
     *
     * @return download record (if any) or <tt>null</tt> if artifact was not downloaded, but could have been resolved
     *         from local disk
     */
    DownloadRecord getDownloadState(String groupId, String artifactId, String version);

    /**
     * Gets the records for the downloaded artifacts
     */
    Collection<DownloadRecord> downloadRecords();

    /**
     * Gets the {@link RepositoryResolver}
     */
    RepositoryResolver getRepositoryResolver();

}
