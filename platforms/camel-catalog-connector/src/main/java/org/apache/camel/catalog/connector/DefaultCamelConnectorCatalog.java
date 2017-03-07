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

public class DefaultCamelConnectorCatalog implements CamelConnectorCatalog {

    private ConnectorDataStore dataStore = new MemoryConnectorDataStore();

    @Override
    public void setConnectorDataStore(ConnectorDataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public boolean hasConnector(String groupId, String artifactId, String version) {
        ConnectorDto dto = new ConnectorDto();
        dto.setGroupId(groupId);
        dto.setArtifactId(artifactId);
        dto.setVersion(version);
        return dataStore.hasConnector(dto);
    }

    @Override
    public void addConnector(String groupId, String artifactId, String version, String name, String description, String labels,
                             String connectorJson, String connectorSchemaJson) {
        ConnectorDto dto = new ConnectorDto();
        dto.setGroupId(groupId);
        dto.setArtifactId(artifactId);
        dto.setVersion(version);
        dto.setName(name);
        dto.setDescription(description);
        dto.setLabels(labels);
        dataStore.addConnector(dto, connectorJson, connectorSchemaJson);
    }

    @Override
    public void removeConnector(String groupId, String artifactId, String version) {
        ConnectorDto dto = new ConnectorDto();
        dto.setGroupId(groupId);
        dto.setArtifactId(artifactId);
        dto.setVersion(version);
        dataStore.removeConnector(dto);
    }

    @Override
    public List<ConnectorDto> findConnector(boolean latestVersionOnly) {
        return findConnector(null, latestVersionOnly);
    }

    @Override
    public List<ConnectorDto> findConnector(String filter, boolean latestVersionOnly) {
        return dataStore.findConnector(filter, latestVersionOnly);
    }

    @Override
    public String connectorJSon(String groupId, String artifactId, String version) {
        return null;
    }

    @Override
    public String connectorSchemaJSon(String groupId, String artifactId, String version) {
        return null;
    }
}
