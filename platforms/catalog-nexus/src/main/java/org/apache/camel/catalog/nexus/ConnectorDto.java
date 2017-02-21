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

import java.io.Serializable;

public class ConnectorDto implements Serializable {

    private NexusArtifactDto nexusArtifactDto;
    private String name;
    private String description;
    private String labels;
    private String connectorJson;
    private String connectorSchemaJson;

    public ConnectorDto(NexusArtifactDto nexusArtifactDto, String name, String description, String labels,
                        String connectorJson, String connectorSchemaJson) {
        this.nexusArtifactDto = nexusArtifactDto;
        this.name = name;
        this.description = description;
        this.labels = labels;
        this.connectorJson = connectorJson;
        this.connectorSchemaJson = connectorSchemaJson;
    }

    public NexusArtifactDto getNexusArtifactDto() {
        return nexusArtifactDto;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getLabels() {
        return labels;
    }

    public String getConnectorJson() {
        return connectorJson;
    }

    public String getConnectorSchemaJson() {
        return connectorSchemaJson;
    }

    public String getMavenGav() {
        return nexusArtifactDto.getGroupId() + ":" + nexusArtifactDto.getArtifactId() + ":" + nexusArtifactDto.getVersion();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ConnectorDto that = (ConnectorDto) o;

        if (!nexusArtifactDto.equals(that.nexusArtifactDto)) {
            return false;
        }
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        int result = nexusArtifactDto.hashCode();
        result = 31 * result + name.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ConnectorDto["
            + "groupId='" + nexusArtifactDto.getGroupId() + '\''
            + ", artifactId='" + nexusArtifactDto.getArtifactId() + '\''
            + ", version='" + nexusArtifactDto.getVersion() + '\''
            + ", name='" + name + '\''
            + ']';
    }

}
