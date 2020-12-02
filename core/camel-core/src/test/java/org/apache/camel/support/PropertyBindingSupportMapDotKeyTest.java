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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for PropertyBindingSupport
 */
public class PropertyBindingSupportMapDotKeyTest extends ContextTestSupport {

    @Test
    public void testPropertiesMapDotKey() throws Exception {
        Foo foo = new Foo();

        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("name", "James");
        prop.put("data[age]", "33");
        prop.put("data[zip.code]", "90210");
        prop.put("data[ISO.CODE]", "USA");
        prop.put("data[with-dash]", "123");
        prop.put("data[with_underscore]", "456");

        PropertyBindingSupport.build().bind(context, foo, prop);

        assertEquals("James", foo.getName());
        assertEquals("33", foo.getData().get("age"));
        assertEquals("90210", foo.getData().get("zip.code"));
        assertEquals("USA", foo.getData().get("ISO.CODE"));
        assertEquals("123", foo.getData().get("with-dash"));
        assertEquals("456", foo.getData().get("with_underscore"));
    }

    public static class Foo {
        private String name;
        private Map<String, String> data = new HashMap<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, String> getData() {
            return data;
        }

        public void setData(Map<String, String> data) {
            this.data = data;
        }
    }

}
