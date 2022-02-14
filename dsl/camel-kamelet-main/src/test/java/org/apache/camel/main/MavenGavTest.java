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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MavenGavTest {

    @Test
    void parseCoreGav() {
        MavenGav gav = MavenGav.parseGav(null, "camel:core");

        assertEquals("org.apache.camel", gav.getGroupId());
        assertEquals("camel-core", gav.getArtifactId());
    }

    @Test
    void parseCamelCoreGav() {
        MavenGav gav = MavenGav.parseGav(null, "camel:camel-core");

        assertEquals("org.apache.camel", gav.getGroupId());
        assertEquals("camel-core", gav.getArtifactId());
    }

    @Test
    void parseOtherGav() {
        MavenGav gav = MavenGav.parseGav(null, "mvn:org.junit:junit-api:99.99");

        assertEquals("org.junit", gav.getGroupId());
        assertEquals("junit-api", gav.getArtifactId());
        assertEquals("99.99", gav.getVersion());
    }
}
