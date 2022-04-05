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
package org.apache.camel.main;

import org.apache.camel.CamelContext;

/**
 * Maven GAV model
 */
public final class MavenGav {

    private String groupId;
    private String artifactId;
    private String version;

    public MavenGav() {
    }

    public static MavenGav parseGav(CamelContext context, String gav) {
        MavenGav answer = new MavenGav();
        // camel-k style GAV
        if (gav.startsWith("camel:")) {
            answer.setGroupId("org.apache.camel");
            String a = gav.substring(6);
            // users may mistakenly use quarkus extension but they should just refer to the vanilla component name
            if (a.startsWith("camel-quarkus-")) {
                a = "camel-" + a.substring(14);
            }
            if (!a.startsWith("camel-")) {
                a = "camel-" + a;
            }
            answer.setArtifactId(a);
            if (context != null) {
                answer.setVersion(context.getVersion());
            }
        } else if (gav.startsWith("camel-") && !(gav.contains(":") || gav.contains("/"))) {
            // not really camel-k style but users may mistakenly use camel-file instead of camel:file
            answer.setGroupId("org.apache.camel");
            String a = gav;
            // users may mistakenly use quarkus extension but they should just refer to the vanilla component name
            if (a.startsWith("camel-quarkus-")) {
                a = "camel-" + a.substring(14);
            }
            answer.setArtifactId(a);
            if (context != null) {
                answer.setVersion(context.getVersion());
            }
        } else if (gav.startsWith("github:")) {
            // TODO: currently not supported
            return answer;
        } else {
            String[] parts = gav.startsWith("mvn:") ? gav.substring(4).split(":") : gav.split(":");
            answer.setGroupId(parts[0]);
            answer.setArtifactId(parts[1]);
            if (parts.length == 3) {
                answer.setVersion(parts[2]);
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

    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + version;
    }
}
