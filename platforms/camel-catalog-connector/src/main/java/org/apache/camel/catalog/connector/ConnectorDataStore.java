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

/**
 * Data store for connector details to be used by the {@link CamelConnectorCatalog}.
 */
public interface ConnectorDataStore {

    /**
     * Adds or updates the connector to the catalog
     *
     * @param dto                   the connector dto
     * @param connectorJson         the <tt>camel-connector</tt> json file
     * @param connectorSchemaJson   the <tt>camel-connector-schema</tt> json file
     * @param componentSchemaJson   the <tt>camel-component-schema</tt> json file
     */
    void addConnector(ConnectorDto dto, String connectorJson, String connectorSchemaJson, String componentSchemaJson);

    /**
     * Is the connector already registered in the catalog
     *
     * @param dto  the connector dto
     */
    boolean hasConnector(ConnectorDto dto);

    /**
     * Removes the connector from the catalog
     *
     * @param dto  the connector dto
     */
    void removeConnector(ConnectorDto dto);

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
     * @param dto  the connector dto
     */
    String connectorJSon(ConnectorDto dto);

    /**
     * Returns the <tt>camel-connector-schema</tt> json file for the given connector with the Maven coordinate
     *
     * @param dto  the connector dto
     */
    String connectorSchemaJSon(ConnectorDto dto);

    /**
     * Returns the <tt>camel-component-schema</tt> json file for the given connector with the Maven coordinate
     *
     * @param dto  the connector dto
     */
    String componentSchemaJSon(ConnectorDto dto);

}
