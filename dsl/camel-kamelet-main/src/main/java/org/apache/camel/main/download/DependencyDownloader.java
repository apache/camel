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

import org.apache.camel.CamelContextAware;
import org.apache.camel.StaticService;

/**
 * To download dependencies at runtime.
 */
public interface DependencyDownloader extends CamelContextAware, StaticService {

    DownloadListener getDownloadListener();

    /**
     * Sets a listener to capture download activity
     */
    void setDownloadListener(DownloadListener downloadListener);

    String getRepos();

    /**
     * Additional maven repositories for download on-demand (Use commas to separate multiple repositories).
     */
    void setRepos(String repos);

    boolean isFresh();

    /**
     * Make sure we use fresh (i.e. non-cached) resources.
     */
    void setFresh(boolean fresh);

    /**
     * Downloads the dependency incl transitive dependencies
     *
     * @param groupId    maven group id
     * @param artifactId maven artifact id
     * @param version    maven version
     */
    void downloadDependency(String groupId, String artifactId, String version);

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
     * Downloads a single maven artifact (no transitive dependencies)
     *
     * @param  groupId    maven group id
     * @param  artifactId maven artifact id
     * @param  version    maven version
     * @return            the artifact, or null if none found
     */
    MavenArtifact downloadArtifact(String groupId, String artifactId, String version);

    /**
     * Checks whether the dependency is already on the classpath
     *
     * @param  groupId    maven group id
     * @param  artifactId maven artifact id
     * @param  version    maven version
     * @return            true if already on classpath, false if not.
     */
    boolean alreadyOnClasspath(String groupId, String artifactId, String version);

}
