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
package org.apache.camel.dsl.jbang.core.common;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JavaVersionMetadataTest {

    @Test
    public void testJavaVersionEnumMatchesCompletionCandidates() throws Exception {
        List<String> candidates = new ArrayList<>();
        new JavaVersionCompletionCandidates().forEach(candidates::add);

        JsonObject property = null;
        try (InputStream is = getClass().getResourceAsStream("/META-INF/camel-jbang-configuration-metadata.json")) {
            assertNotNull(is, "camel-jbang-configuration-metadata.json should be on the classpath");
            JsonObject root = (JsonObject) Jsoner.deserialize(IOHelper.loadText(is));
            for (Object o : (JsonArray) root.get("properties")) {
                JsonObject jo = (JsonObject) o;
                if (CamelJBangConstants.JAVA_VERSION.equals(jo.getString("name"))) {
                    property = jo;
                }
            }
        }
        assertNotNull(property, "camel.jbang.javaVersion should be in the metadata");
        // the java versions of camel.jbang.javaVersion are the same as offered by --java-version
        assertEquals(candidates, property.getCollection("enum"));
        assertEquals("21", property.getString("defaultValue"));
    }
}
