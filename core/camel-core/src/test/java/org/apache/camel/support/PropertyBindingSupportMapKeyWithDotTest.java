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
package org.apache.camel.support;

import org.apache.camel.ContextTestSupport;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for PropertyBindingSupport
 */
public class PropertyBindingSupportMapKeyWithDotTest extends ContextTestSupport {

    @Test
    public void testPropertiesMap() throws Exception {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("id", "123");
        prop.put("database.name", "MySQL");
        prop.put("database.timezone", "CET");

        Map<String, String> map = new HashMap<>();
        PropertyBindingSupport.build().bind(context, map, prop);

        assertEquals("123", map.get("id"));
        assertEquals("MySQL", map.get("database.name"));
        assertEquals("CET", map.get("database.timezone"));
    }

}
