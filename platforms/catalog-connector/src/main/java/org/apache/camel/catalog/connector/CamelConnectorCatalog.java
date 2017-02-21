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
package org.apache.camel.catalog.connector;

import java.util.List;
import javax.management.MXBean;

/**
 * Catalog of connectors.
 */
@MXBean
public interface CamelConnectorCatalog {

    /**
     * To configure which {@link ConnectorDataStore} to use
     */
    void setConnectorDataStore(ConnectorDataStore dataStore);

    /**
     * Adds or updates the connector to the catalog
     *
     * @param groupId               maven group id
     * @param artifactId            maven artifact id
     * @param version               maven version
     * @param name                  name of connector
     * @param description           description of connector
     * @param labels                labels (separated by comma) of connector
     * @param connectorJson         the <tt>camel-connector</tt> json file
     * @param connectorSchemaJson   the <tt>camel-connector-schema</tt> json file
     */
    void addConnector(String groupId, String artifactId, String version, String name, String description, String labels,
                      String connectorJson, String connectorSchemaJson);

    /**
     * Removes the connector from the catalog
     *
     * @param groupId               maven group id
     * @param artifactId            maven artifact id
     * @param version               maven version
     */
    void removeConnector(String groupId, String artifactId, String version);

    /**
     * Finds all the connectors from the catalog
     *
     * @param latestVersionOnly  whether to include only latest version of the connectors
     */
    List<ConnectorDto> findConnector(boolean latestVersionOnly);

    /**
     * Find all the connectors that matches the maven coordinate, name, label or description from the catalog
     *
     * @param filter             filter text
     * @param latestVersionOnly  whether to include only latest version of the connectors
     */
    List<ConnectorDto> findConnector(String filter, boolean latestVersionOnly);

    /**
     * Returns the <tt>camel-connector</tt> json file for the given connector with the Maven coordinate
     *
     * @param groupId     maven group id
     * @param artifactId  maven artifact id
     * @param version     maven version
     */
    String connectorJSon(String groupId, String artifactId, String version);

    /**
     * Returns the <tt>camel-connector-schema</tt> json file for the given connector with the Maven coordinate
     *
     * @param groupId     maven group id
     * @param artifactId  maven artifact id
     * @param version     maven version
     */
    String connectorSchemaJSon(String groupId, String artifactId, String version);

}
