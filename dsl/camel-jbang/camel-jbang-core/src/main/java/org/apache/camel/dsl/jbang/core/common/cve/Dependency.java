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
package org.apache.camel.dsl.jbang.core.common.cve;

import java.util.Arrays;

/**
 * A JAR to audit, by its Maven coordinates.
 */
public record Dependency(String groupId, String artifactId, String version) {

    /** {@code groupId:artifactId:version}. */
    public String gav() {
        return groupId + ":" + artifactId + ":" + version;
    }

    /** {@code groupId:artifactId}. */
    public String ga() {
        return groupId + ":" + artifactId;
    }

    /**
     * The coordinates of a JAR in a Maven repository, such as
     * {@code /home/me/.m2/repository/org/apache/kafka/kafka-clients/3.9.0/kafka-clients-3.9.0.jar}; null for a JAR
     * outside one.
     */
    public static Dependency fromJarPath(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        int repoIdx = normalized.indexOf("/repository/");
        if (repoIdx < 0 || !normalized.endsWith(".jar")) {
            return null;
        }
        String relative = normalized.substring(repoIdx + "/repository/".length());
        String[] parts = relative.split("/");
        // groupId parts / artifactId / version / file
        if (parts.length < 4) {
            return null;
        }
        String version = parts[parts.length - 2];
        String artifactId = parts[parts.length - 3];
        String groupId = String.join(".", Arrays.copyOfRange(parts, 0, parts.length - 3));
        if (!parts[parts.length - 1].startsWith(artifactId + "-")) {
            return null;
        }
        return new Dependency(groupId, artifactId, version);
    }
}
