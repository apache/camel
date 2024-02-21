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
package org.apache.camel.tooling.maven;

import java.util.List;
import java.util.Set;

/**
 * Pragmatic Maven download/resolution API that should replace usage of Ivy/Grape and Shrinkwrap across Camel.
 */
public interface MavenDownloader {

    /**
     * Main resolution method. Using Maven Resolver, a list of maven coordinates (in the form of
     * {@code groupId:artifactId[:packaging[:classifier]]:version}) is used to download artifacts from configured Maven
     * repositories.
     *
     * @param dependencyGAVs     a list of Maven coordinates
     * @param extraRepositories  nullable list of additional repositories to use (except the discovered ones)
     * @param transitively       whether to download/resolve dependencies transitively
     * @param useApacheSnapshots whether to include Apache Snapshots repository in the list of used repositories
     */
    List<MavenArtifact> resolveArtifacts(
            List<String> dependencyGAVs, Set<String> extraRepositories,
            boolean transitively, boolean useApacheSnapshots)
            throws MavenResolutionException;

    /**
     * Resolves available versions for groupId + artifactId from single remote repository.
     *
     * @param groupId    groupId
     * @param artifactId artifactId
     * @param repository external repository to use (defaults to Maven Central if {@code null})
     */
    List<MavenGav> resolveAvailableVersions(String groupId, String artifactId, String repository)
            throws MavenResolutionException;

    /**
     * Existing, configured {@link MavenDownloader} can be used as a template to create customized version which shares
     * most of the configuration except underlying {@code org.eclipse.aether.RepositorySystemSession}, which can't be
     * shared.
     */
    MavenDownloader customize(String localRepository, int connectTimeout, int requestTimeout);

    /**
     * To use a listener when downloading from remote repositories.
     */
    void setRemoteArtifactDownloadListener(RemoteArtifactDownloadListener listener);

    /**
     * Set a flag determining Maven update behavior. See the description of {@code -U,--update-snapshots} Maven option.
     * When set to {@code true}, Maven metadata (to determine newest SNAPSHOT or RELEASE or LATEST version) is always
     * fetched.
     */
    void setFresh(boolean fresh);

    /**
     * Sets maven downloader in offline mode
     */
    void setOffline(boolean offline);

    /**
     * Configure comma-separated list of repositories to use (in addition to the ones discovered from Maven settings).
     */
    void setRepos(String repos);

    /**
     * Configure a location of {@code settings-security.xml} (when not set, defaults to
     * {@code ~/.m2/settings-security.xml} unless {@link #setMavenSettingsLocation(String)} is set explicitly set to
     * {@code "false"}.
     */
    void setMavenSettingsSecurityLocation(String mavenSettingsSecurity);

    /**
     * Configure a location of {@code settings.xml} (when not set, defaults to {@code ~/.m2/settings.xml} unless it's
     * explicitly set to {@code "false"}.
     */
    void setMavenSettingsLocation(String mavenSettings);

    /**
     * Gets the repository resolver.
     */
    RepositoryResolver getRepositoryResolver();

    /**
     * Sets a custom repository resolver.
     */
    void setRepositoryResolver(RepositoryResolver repositoryResolver);

}
