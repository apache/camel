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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A memory based {@link ConnectorDataStore}
 */
public class MemoryConnectorDataStore implements ConnectorDataStore {

    private Set<ConnectorDetails> store = new CopyOnWriteArraySet<>();

    @Override
    public void addConnector(ConnectorDto dto, String connectorJson, String connectorSchemaJson, String componentSchemaJson) {
        ConnectorDetails entry = new ConnectorDetails(dto, connectorJson, connectorSchemaJson, componentSchemaJson);

        // remove in case we are updating the connector
        store.remove(entry);
        store.add(entry);
    }

    @Override
    public boolean hasConnector(ConnectorDto dto) {
        return store.contains(new ConnectorDetails(dto, null, null, null));
    }

    @Override
    public void removeConnector(ConnectorDto dto) {
        store.remove(new ConnectorDetails(dto, null, null, null));
    }

    @Override
    public List<ConnectorDto> findConnector(String filter, boolean latestVersionOnly) {
        final List<ConnectorDto> answer = new ArrayList<>();

        if (filter != null && !filter.isEmpty()) {
            // search ignore case
            filter = filter.toLowerCase(Locale.US);

            for (ConnectorDetails detail : store) {
                ConnectorDto dto = detail.getDto();
                if (dto.getName().toLowerCase(Locale.US).contains(filter)) {
                    answer.add(dto);
                } else if (dto.getDescription().toLowerCase(Locale.US).contains(filter)) {
                    answer.add(dto);
                } else if (dto.getGroupId().toLowerCase(Locale.US).contains(filter)) {
                    answer.add(dto);
                } else if (dto.getArtifactId().toLowerCase(Locale.US).contains(filter)) {
                    answer.add(dto);
                } else if (dto.getVersion().toLowerCase(Locale.US).contains(filter)) {
                    answer.add(dto);
                } else {
                    String labels = dto.getLabels();
                    if (labels != null) {
                        String[] arr = labels.split(",");
                        for (String lab : arr) {
                            lab = lab.toLowerCase(Locale.US);
                            if (lab.contains(filter)) {
                                answer.add(dto);
                                break;
                            }
                        }
                    }
                }
            }
        } else {
            store.forEach(d -> answer.add(d.getDto()));
        }

        // filter only latest version
        if (latestVersionOnly && answer.size() > 1) {
            // sort first
            answer.sort(Comparator.comparing(ConnectorDto::getMavenGav));

            // keep only latest in each group
            List<ConnectorDto> unique = new ArrayList<>();
            ConnectorDto prev = null;

            for (ConnectorDto dto : answer) {
                if (prev == null
                    || (prev.getGroupId().equals(dto.getGroupId())
                    && prev.getArtifactId().equals(dto.getArtifactId()))) {
                    prev = dto;
                } else {
                    unique.add(prev);
                    prev = dto;
                }
            }
            if (prev != null) {
                // special for last element
                unique.add(prev);
            }

            return unique;
        }

        return answer;
    }

    @Override
    public String connectorJSon(ConnectorDto dto) {
        return store.stream().filter(d -> d.getDto().equals(dto)).findFirst().orElse(null).getConnectorJson();
    }

    @Override
    public String connectorSchemaJSon(ConnectorDto dto) {
        return store.stream().filter(d -> d.getDto().equals(dto)).findFirst().orElse(null).getConnectorSchemaJson();
    }

    @Override
    public String componentSchemaJSon(ConnectorDto dto) {
        return store.stream().filter(d -> d.getDto().equals(dto)).findFirst().orElse(null).getComponentSchemaJson();
    }

    /**
     * Entry holding the connector details
     */
    private static final class ConnectorDetails {

        private ConnectorDto dto;
        private String connectorJson;
        private String connectorSchemaJson;
        private String componentSchemaJson;

        ConnectorDetails(ConnectorDto dto, String connectorJson, String connectorSchemaJson, String componentSchemaJson) {
            this.dto = dto;
            this.connectorJson = connectorJson;
            this.connectorSchemaJson = connectorSchemaJson;
            this.componentSchemaJson = componentSchemaJson;
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

        String getComponentSchemaJson() {
            return componentSchemaJson;
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
