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
package org.apache.camel.component.grape

import groovy.transform.Immutable

@Immutable
class MavenCoordinates {

    private final String groupId

    private final String artifactId

    private final String version

    static MavenCoordinates parseMavenCoordinates(String coordinates) {
        def coordinatesParts = coordinates.split('/')
        if(coordinatesParts.length != 3) {
            throw new IllegalArgumentException("Invalid coordinates: ${coordinates}")
        }
        new MavenCoordinates(groupId: coordinatesParts[0], artifactId: coordinatesParts[1], version: coordinatesParts[2])
    }

    String getGroupId() {
        return groupId
    }

    String getArtifactId() {
        return artifactId
    }

    String getVersion() {
        return version
    }

}
