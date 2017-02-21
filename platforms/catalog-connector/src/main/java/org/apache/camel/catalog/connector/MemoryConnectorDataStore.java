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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A memory based {@link ConnectorDataStore}
 */
public class MemoryConnectorDataStore implements ConnectorDataStore {

    private Set<ConnectorDetails> store = new CopyOnWriteArraySet<>();

    @Override
    public void addConnector(ConnectorDto dto, String connectorJson, String connectorSchemaJson) {
        ConnectorDetails entry = new ConnectorDetails(dto, connectorJson, connectorSchemaJson);

        // remove in case we are updating the connector
        store.remove(entry);
        store.add(entry);
    }

    @Override
    public void removeConnector(ConnectorDto dto) {
        store.remove(new ConnectorDetails(dto, null, null));
    }

    @Override
    public List<ConnectorDto> findConnector() {
        List<ConnectorDto> dtos = new ArrayList<>();
        store.forEach(e -> dtos.add(e.getDto()));
        return dtos;
    }

    @Override
    public List<ConnectorDto> findConnector(String filter) {
        // TODO: collect
        return null;
    }

    @Override
    public String connectorJSon(ConnectorDto dto) {
        return store.stream().filter(d -> d.getDto().equals(dto)).findFirst().orElse(null).getConnectorJson();
    }

    @Override
    public String connectorSchemaJSon(ConnectorDto dto) {
        return store.stream().filter(d -> d.getDto().equals(dto)).findFirst().orElse(null).getConnectorSchemaJson();
    }

    /**
     * Entry holding the connector details
     */
    private static final class ConnectorDetails {

        private ConnectorDto dto;
        private String connectorJson;
        private String connectorSchemaJson;

        ConnectorDetails(ConnectorDto dto, String connectorJson, String connectorSchemaJson) {
            this.dto = dto;
            this.connectorJson = connectorJson;
            this.connectorSchemaJson = connectorSchemaJson;
        }

        ConnectorDto getDto() {
            return dto;
        }

        String getConnectorJson() {
            return connectorJson;
        }

        String getConnectorSchemaJson() {
            return connectorSchemaJson;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ConnectorDetails entry = (ConnectorDetails) o;

            return dto.equals(entry.dto);
        }

        @Override
        public int hashCode() {
            return dto.hashCode();
        }
    }

}
