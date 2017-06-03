/**
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

import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.connector.CamelConnectorCatalog;

/**
 * Provider which allows downloading artifact using Maven and add content to the {@link CamelCatalog}.
 */
public interface MavenArtifactProvider {

    /**
     * Configures the directory for the download cache.
     * <p/>
     * The default folder is <tt>USER_HOME/.groovy/grape</tt>
     *
     * @param directory the directory.
     */
    void setCacheDirectory(String directory);

    /**
     * To add a 3rd party Maven repository.
     *
     * @param name the repository name
     * @param url  the repository url
     */
    void addMavenRepository(String name, String url);

    /**
     * Downloads the artifact using the Maven coordinates and scans the JAR for Camel components
     * which will be added to the CamelCatalog or CamelConnectorCatalog.
     *
     * @param camelCatalog          The Camel Catalog
     * @param camelConnectorCatalog The Camel Connector Catalog
     * @param groupId               Maven group id
     * @param artifactId            Maven artifact id
     * @param version               Maven version
     * @return the names of the components that was added, or an empty set if none found or they already exists in the catalog
     */
    Set<String> addArtifactToCatalog(CamelCatalog camelCatalog, CamelConnectorCatalog camelConnectorCatalog,
                                     String groupId, String artifactId, String version);

}