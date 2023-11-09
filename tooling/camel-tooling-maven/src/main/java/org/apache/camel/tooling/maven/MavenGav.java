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
package org.apache.camel.tooling.maven;

/**
 * Maven GAV model with parsing support and special rules for some names:
 * <ul>
 * <li>{@code camel:core -> org.apache.camel:camel-core}</li>
 * <li>{@code camel-xxx -> org.apache.camel:camel-xxx}</li>
 * <li>{@code camel-quarkus-xxx -> camel-xxx}</li>
 * </ul>
 */
public final class MavenGav {

    private String groupId;
    private String artifactId;
    private String version;
    private String packaging = "jar";
    private String classifier = "";

    public MavenGav() {
    }

    public static MavenGav parseGav(String gav) {
        return parseGav(gav, null);
    }

    public static MavenGav fromCoordinates(
            String groupId, String artifactId, String version, String packaging,
            String classifier) {
        MavenGav answer = new MavenGav();
        answer.groupId = groupId;
        answer.artifactId = artifactId;
        answer.version = version;
        if (classifier != null && !classifier.isEmpty()) {
            answer.classifier = classifier;
        }
        if (packaging != null && !packaging.isEmpty()) {
            answer.packaging = packaging;
        }
        return answer;
    }

    public static MavenGav parseGav(String gav, String defaultVersion) {
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
        } else if (gav.startsWith("agent:")) {
            // special for java agent JARs
            answer = parseGav(gav.substring(6));
            answer.setPackaging("agent");
        } else {
            // for those used to OSGi's pax-url-aether syntax
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

    @Override
    public String toString() {
        if (version != null) {
            return groupId + ":" + artifactId + ":" + version;
        } else {
            return groupId + ":" + artifactId;
        }
    }
}
