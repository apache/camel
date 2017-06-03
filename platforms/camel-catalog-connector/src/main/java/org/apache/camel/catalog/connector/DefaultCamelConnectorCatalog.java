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

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;

public class DefaultCamelConnectorCatalog implements CamelConnectorCatalog {

    private final CamelCatalog camelCatalog = new DefaultCamelCatalog(true);
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
    public void addConnector(String groupId, String artifactId, String version, String name, String scheme,
                             String javaType, String description, String labels,
                             String connectorJson, String connectorSchemaJson, String componentSchemaJson) {
        ConnectorDto dto = new ConnectorDto();
        dto.setGroupId(groupId);
        dto.setArtifactId(artifactId);
        dto.setVersion(version);
        dto.setName(name);
        dto.setScheme(scheme);
        dto.setJavaType(javaType);
        dto.setDescription(description);
        dto.setLabels(labels);
        dataStore.addConnector(dto, connectorJson, connectorSchemaJson, componentSchemaJson);
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
        ConnectorDto dto = new ConnectorDto();
        dto.setGroupId(groupId);
        dto.setArtifactId(artifactId);
        dto.setVersion(version);
        return dataStore.connectorJSon(dto);
    }

    @Override
    public String connectorSchemaJSon(String groupId, String artifactId, String version) {
        ConnectorDto dto = new ConnectorDto();
        dto.setGroupId(groupId);
        dto.setArtifactId(artifactId);
        dto.setVersion(version);
        return dataStore.connectorSchemaJSon(dto);
    }

    @Override
    public String componentSchemaJSon(String groupId, String artifactId, String version) {
        ConnectorDto dto = new ConnectorDto();
        dto.setGroupId(groupId);
        dto.setArtifactId(artifactId);
        dto.setVersion(version);
        return dataStore.componentSchemaJSon(dto);
    }

    @Override
    public String asEndpointUri(String scheme, Map<String, String> properties, boolean encode) throws URISyntaxException {
        // delegate to use CamelCatalog
        Optional<ConnectorDto> found = dataStore.findConnector(null, true).stream().filter(d -> d.getScheme().equals(scheme)).findAny();
        if (found.isPresent()) {
            ConnectorDto dto = found.get();

            // need to add custom connector as component to the catalog before we can build the uri
            String javaType = dto.getJavaType();
            String componentJson = componentSchemaJSon(dto.getGroupId(), dto.getArtifactId(), dto.getVersion());

            camelCatalog.addComponent(scheme, javaType, componentJson);
            return camelCatalog.asEndpointUri(scheme, properties, encode);
        }
        // no connector with that scheme
        return null;
    }

    @Override
    public String asEndpointUriXml(String scheme, Map<String, String> properties, boolean encode) throws URISyntaxException {
        // delegate to use CamelCatalog
        Optional<ConnectorDto> found = dataStore.findConnector(null, true).stream().filter(d -> d.getScheme().equals(scheme)).findAny();
        if (found.isPresent()) {
            ConnectorDto dto = found.get();

            // need to add custom connector as component to the catalog before we can build the uri
            String javaType = dto.getJavaType();
            String componentJson = componentSchemaJSon(dto.getGroupId(), dto.getArtifactId(), dto.getVersion());

            camelCatalog.addComponent(scheme, javaType, componentJson);
            return camelCatalog.asEndpointUriXml(scheme, properties, encode);
        }
        // no connector with that scheme
        return null;
    }

}
