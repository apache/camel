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

/**
 * Listener for downloading a dependency (can be downloaded from a local cache)
 */
public interface DownloadListener {

    /**
     * Downloads a new dependency
     */
    void onDownloadDependency(String groupId, String artifactId, String version);

    /**
     * After the dependency has been downloaded
     */
    default void onDownloadedDependency(String groupId, String artifactId, String version) {
        // noop
    }

    /**
     * Some dependencies require third-party maven repositories to be downloaded.
     */
    default void onExtraRepository(String repo) {
        // noop
    }

    /**
     * Uses an existing already downloaded dependency
     */
    void onAlreadyDownloadedDependency(String groupId, String artifactId, String version);

    /**
     * When a kamelet is being downloaded (typically loaded directly from camel-kamelets JAR)
     */
    default void onLoadingKamelet(String name) {
        // noop
    }

    /**
     * When a modeline is detected
     */
    default void onLoadingModeline(String key, String value) {
        // noop
    }

}
