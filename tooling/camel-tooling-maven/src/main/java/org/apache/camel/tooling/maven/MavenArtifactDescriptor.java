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

import java.util.List;
import java.util.Map;

/**
 * The resolved (effective) POM of a Maven artifact: its direct dependencies, with versions and scopes after parent
 * inheritance and dependency management (including imported BOMs) are applied, and its effective model properties.
 *
 * @param gav          the artifact
 * @param dependencies the direct dependencies, each with its scope set (see {@link MavenGav#getScope()})
 * @param properties   the effective model properties, with interpolation applied. Values that could not be interpolated
 *                     (still containing <code>${</code>) are left out.
 */
public record MavenArtifactDescriptor(MavenGav gav, List<MavenGav> dependencies, Map<String, String> properties) {

    /**
     * Finds a direct dependency by groupId and artifactId (any scope).
     */
    public MavenGav findDependency(String groupId, String artifactId) {
        for (MavenGav dep : dependencies) {
            if (groupId.equals(dep.getGroupId()) && artifactId.equals(dep.getArtifactId())) {
                return dep;
            }
        }
        return null;
    }
}
