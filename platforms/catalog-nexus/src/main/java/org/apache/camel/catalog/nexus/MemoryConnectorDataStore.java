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
package org.apache.camel.catalog.nexus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A in-memory based {@link ConnectorDataStore}.
 */
public class MemoryConnectorDataStore implements ConnectorDataStore {

    private final Set<ConnectorDto> connectors = new LinkedHashSet<>();

    @Override
    public int size() {
        return connectors.size();
    }

    @Override
    public void addConnector(ConnectorDto connector) {
        connectors.add(connector);
    }

    @Override
    public List<ConnectorDto> searchConnectors(String filter, boolean latestVersionOnly) {
        List<ConnectorDto> answer = new ArrayList<>();

        if (filter == null || filter.isEmpty()) {
            // return all of them
            answer.addAll(connectors);
        } else {
            // search ignore case
            filter = filter.toLowerCase(Locale.US);
            for (ConnectorDto dto : connectors) {
                if (dto.getName().toLowerCase(Locale.US).contains(filter)) {
                    answer.add(dto);
                } else if (dto.getDescription().toLowerCase(Locale.US).contains(filter)) {
                    answer.add(dto);
                } else if (dto.getNexusArtifactDto().getGroupId().toLowerCase(Locale.US).contains(filter)) {
                    answer.add(dto);
                } else if (dto.getNexusArtifactDto().getArtifactId().toLowerCase(Locale.US).contains(filter)) {
                    answer.add(dto);
                } else if (dto.getNexusArtifactDto().getVersion().toLowerCase(Locale.US).contains(filter)) {
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
                    || (prev.getNexusArtifactDto().getGroupId().equals(dto.getNexusArtifactDto().getGroupId())
                        && prev.getNexusArtifactDto().getArtifactId().equals(dto.getNexusArtifactDto().getArtifactId())) ) {
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

            answer = unique;
        }

        return answer;

    }
}
