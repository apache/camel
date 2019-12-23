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
package org.apache.camel.main.parser;

import java.util.List;

import junit.framework.TestCase;

import org.junit.Test;

public class MyConfigurationParserTest extends TestCase {

    @Test
    public void testMyParser() throws Exception {
        String fileName = "src/test/java/org/apache/camel/main/parser/MyConfiguration.java";

        MainConfigurationParser parser = new MainConfigurationParser();
        List<ConfigurationModel> list = parser.parseConfigurationSource(fileName);
        assertNotNull(list);
        assertEquals(40, list.size());

        assertEquals("name", list.get(0).getName());
        assertEquals("java.lang.String", list.get(0).getJavaType());
        assertNull(list.get(0).getDefaultValue());
        assertEquals("Sets the name of the CamelContext.", list.get(0).getDescription());

        assertEquals("shutdownTimeout", list.get(4).getName());
        assertEquals("int", list.get(4).getJavaType());
        assertEquals("300", list.get(4).getDefaultValue());
        assertEquals("Timeout in seconds to graceful shutdown Camel.", list.get(4).getDescription());

        assertEquals("tracing", list.get(25).getName());
        assertEquals("boolean", list.get(25).getJavaType());
        assertTrue(list.get(25).isDeprecated());
    }
}
