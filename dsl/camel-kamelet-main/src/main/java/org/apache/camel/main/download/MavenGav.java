/*
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
package org.apache.camel.main.download;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 * Maven GAV model
 */
public final class MavenGav {

    private Artifact artifact;

    private String groupId;
    private String artifactId;
    private String version;
    private String packaging = "jar";
    private String classifier = "";

    public MavenGav() {
    }

    public static MavenGav parseGav(String gav) {
        return parseGav(gav, null, null);
    }

    public static MavenGav parseGav(String gav, String defaultVersion) {
        return parseGav(gav, defaultVersion, null);
    }

    public static MavenGav parseGav(String gav, ArtifactTypeRegistry artifactTypeRegistry) {
        return parseGav(gav, null, artifactTypeRegistry);
    }

    public static MavenGav parseGav(String gav, String defaultVersion, ArtifactTypeRegistry artifactTypeRegistry) {
        MavenGav answer = new MavenGav();
        // camel-k style GAV
        if (gav.startsWith("camel:")) {
            answer.setGroupId("org.apache.camel");
            String a = gav.substring(6);
            // users may mistakenly use quarkus extension, but they should just refer to the vanilla component name
            if (a.startsWith("camel-quarkus-")) {
                a = "camel-" + a.substring(14);
            }
            if (!a.startsWith("camel-")) {
                a = "camel-" + a;
            }
            answer.setArtifactId(a);
            if (defaultVersion != null) {
                answer.setVersion(defaultVersion);
            }
        } else if (gav.startsWith("camel-") && !(gav.contains(":") || gav.contains("/"))) {
            // not really camel-k style but users may mistakenly use camel-file instead of camel:file
            answer.setGroupId("org.apache.camel");
            String a = gav;
            // users may mistakenly use quarkus extension, but they should just refer to the vanilla component name
            if (a.startsWith("camel-quarkus-")) {
                a = "camel-" + a.substring(14);
            }
            answer.setArtifactId(a);
            if (defaultVersion != null) {
                answer.setVersion(defaultVersion);
            }
        } else if (gav.startsWith("org.apache.camel:")) {
            String[] parts = gav.split(":");
            if (parts.length > 0) {
                answer.setGroupId(parts[0]);
            }
            if (parts.length > 1) {
                answer.setArtifactId(parts[1]);
            }
            if (parts.length > 2) {
                answer.setVersion(parts[2]);
            } else if (defaultVersion != null) {
                answer.setVersion(defaultVersion);
            }
        } else {
            String[] parts = gav.startsWith("mvn:") ? gav.substring(4).split(":") : gav.split(":");
            if (parts.length > 0) {
                answer.setGroupId(parts[0]);
            }
            if (parts.length > 1) {
                answer.setArtifactId(parts[1]);
            }
            if (parts.length > 2) {
                // it depends...
                if (parts.length == 3) {
                    answer.setVersion(parts[2]);
                } else if (parts.length == 4) {
                    // packaging type + version
                    answer.setPackaging(parts[2]);
                    answer.setVersion(parts[3]);
                } else if (parts.length == 5) {
                    // packaging type + classifier + version
                    answer.setPackaging(parts[2]);
                    answer.setClassifier(parts[3]);
                    answer.setVersion(parts[4]);
                }
            }
        }
        if (artifactTypeRegistry == null) {
            answer.setArtifact(new DefaultArtifact(
                    answer.groupId, answer.artifactId, answer.classifier,
                    answer.packaging, answer.version));
        } else {
            answer.setArtifact(new DefaultArtifact(
                    answer.groupId, answer.artifactId, answer.classifier,
                    answer.packaging, answer.version, artifactTypeRegistry.get(answer.packaging)));
        }

        return answer;
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

    public String getPackaging() {
        return packaging;
    }

    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    public String getClassifier() {
        return classifier;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    public Artifact getArtifact() {
        return artifact;
    }

    public void setArtifact(Artifact artifact) {
        this.artifact = artifact;
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + version;
    }
}
