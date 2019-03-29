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
package org.apache.camel.catalog;

import java.io.InputStream;

/**
 * Strategy to manage and load Camel versions the catalog uses.
 */
public interface VersionManager {

    /**
     * Gets the current loaded Camel version used by the catalog.
     */
    String getLoadedVersion();

    /**
     * Attempt to load the Camel version to be used by the catalog.
     * <p/>
     * Loading the camel-catalog JAR of the given version of choice may require internet access
     * to download the JAR from Maven central. You can pre download the JAR and install in a local
     * Maven repository to avoid internet access for offline environments.
     *
     * @param version  the Camel version such as <tt>2.17.1</tt>
     * @return <tt>true</tt> if the version was loaded, <tt>false</tt> if not.
     */
    boolean loadVersion(String version);

    /**
     * Gets the current loaded runtime provider version used by the catalog.
     */
    String getRuntimeProviderLoadedVersion();

    /**
     * Attempt to load the runtime provider version to be used by the catalog.
     * <p/>
     * Loading the runtime provider JAR of the given version of choice may require internet access
     * to download the JAR from Maven central. You can pre download the JAR and install in a local
     * Maven repository to avoid internet access for offline environments.
     *
     * @param groupId  the runtime provider Maven groupId
     * @param artifactId  the runtime provider Maven artifactId
     * @param version  the runtime provider Maven version
     * @return <tt>true</tt> if the version was loaded, <tt>false</tt> if not.
     */
    boolean loadRuntimeProviderVersion(String groupId, String artifactId, String version);

    /**
     * Returns an input stream for reading the specified resource from the loaded Catalog version.
     *
     * @param name the resource name
     * @return the stream if found, or <tt>null</tt> if not found.
     */
    InputStream getResourceAsStream(String name);

}
