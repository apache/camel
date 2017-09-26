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
package org.apache.camel.runtimecatalog;

import java.util.List;
import java.util.Map;

import org.apache.camel.ContextTestSupport;

public class JSonSchemaHelperTest extends ContextTestSupport {

    public void testParseJsonSchemaMustBeOrdered() throws Exception {
        String json = context.getRuntimeCamelCatalog().componentJSonSchema("bean");
        assertNotNull(json);

        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("component", json, false);
        assertEquals(17, rows.size());

        // should preserve order
        assertEquals("kind", rows.get(0).keySet().iterator().next());
        assertEquals("scheme", rows.get(1).keySet().iterator().next());
        assertEquals("syntax", rows.get(2).keySet().iterator().next());
        assertEquals("title", rows.get(3).keySet().iterator().next());
        assertEquals("description", rows.get(4).keySet().iterator().next());
        assertEquals("label", rows.get(5).keySet().iterator().next());
        assertEquals("deprecated", rows.get(6).keySet().iterator().next());
        assertEquals("deprecationNote", rows.get(7).keySet().iterator().next());
        assertEquals("async", rows.get(8).keySet().iterator().next());
        assertEquals("consumerOnly", rows.get(9).keySet().iterator().next());
        assertEquals("producerOnly", rows.get(10).keySet().iterator().next());
        assertEquals("lenientProperties", rows.get(11).keySet().iterator().next());
        assertEquals("javaType", rows.get(12).keySet().iterator().next());
        assertEquals("firstVersion", rows.get(13).keySet().iterator().next());
        assertEquals("groupId", rows.get(14).keySet().iterator().next());
        assertEquals("artifactId", rows.get(15).keySet().iterator().next());
        assertEquals("version", rows.get(16).keySet().iterator().next());

        rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);
        assertEquals(6, rows.size());
        assertEquals("beanName", rows.get(0).values().iterator().next());
        assertEquals("method", rows.get(1).values().iterator().next());
        assertEquals("cache", rows.get(2).values().iterator().next());
        assertEquals("multiParameterArray", rows.get(3).values().iterator().next());
        assertEquals("parameters", rows.get(4).values().iterator().next());
        assertEquals("synchronous", rows.get(5).values().iterator().next());
    }

    public void testParseInvalidJson() throws Exception {
        String json = "{ \"name\": this is invalid }";
        try {
            JSonSchemaHelper.parseJsonSchema("foo", json, false);
            fail("Should fail");
        } catch (Exception e) {
            assertEquals("Cannot parse json", e.getMessage());
        }
    }
}
