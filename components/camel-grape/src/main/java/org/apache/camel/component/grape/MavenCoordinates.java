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
package org.apache.camel.component.grape;

public class MavenCoordinates {

    private final String groupId;

    private final String artifactId;

    private final String version;

    private final String classifier;

    MavenCoordinates(String groupId, String artifactId, String version, String classifier) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.classifier = classifier;
    }
    

    public static MavenCoordinates parseMavenCoordinates(String coordinates) {
        String[] coordinatesParts = coordinates.split("/");
        String clazzifier = "";
        if (coordinatesParts.length < 3 || coordinatesParts.length > 4) {
            throw new IllegalArgumentException("Invalid coordinates: " + coordinates);
        }

        if (coordinatesParts.length == 4) {
            clazzifier = coordinatesParts[3];
        }

        return new MavenCoordinates(coordinatesParts[0], coordinatesParts[1], coordinatesParts[2], clazzifier);
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getClassifier() {
        return classifier;
    }

}
