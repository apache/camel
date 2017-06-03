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

/**
 * Represents a Maven artifact from the Nexus repository.
 */
public class NexusArtifactDto implements Serializable {

    private String groupId;
    private String artifactId;
    private String version;
    private String artifactLink;

    public NexusArtifactDto() {
    }

    public NexusArtifactDto(String groupId, String artifactId, String version, String artifactLink) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.artifactLink = artifactLink;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getArtifactLink() {
        return artifactLink;
    }

    public void setArtifactLink(String artifactLink) {
        this.artifactLink = artifactLink;
    }

    @Override
    public boolean equals(Object o) {
        // use gav for equals/hashCode
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        NexusArtifactDto that = (NexusArtifactDto) o;

        if (!groupId.equals(that.groupId)) {
            return false;
        }
        if (!artifactId.equals(that.artifactId)) {
            return false;
        }
        return version.equals(that.version);
    }

    @Override
    public int hashCode() {
        int result = groupId.hashCode();
        result = 31 * result + artifactId.hashCode();
        result = 31 * result + version.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "NexusArtifactDto["
            + "groupId='" + groupId + '\''
            + ", artifactId='" + artifactId + '\''
            + ", version='" + version + '\''
            + ", artifactLink='" + artifactLink + '\''
            + ']';
    }
}
