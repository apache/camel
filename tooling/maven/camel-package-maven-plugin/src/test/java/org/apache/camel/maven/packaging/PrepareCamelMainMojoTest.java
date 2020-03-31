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
package org.apache.camel.maven.packaging;

import java.util.List;

import org.apache.camel.tooling.model.MainModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PrepareCamelMainMojoTest {

    @Test
    public void testMyParser() throws Exception {
        String fileName = "src/test/java/org/apache/camel/maven/packaging/MyConfiguration.java";

        List<MainModel.MainOptionModel> list = PrepareCamelMainMojo.parseConfigurationSource(fileName);
        assertNotNull(list);
        assertEquals(39, list.size());

        assertEquals("name", list.get(0).getName());
        assertEquals("java.lang.String", list.get(0).getJavaType());
        assertNull(list.get(0).getDefaultValue());
        assertEquals("Sets the name of the CamelContext.", list.get(0).getDescription());

        assertEquals("shutdownTimeout", list.get(4).getName());
        assertEquals("int", list.get(4).getJavaType());
        assertEquals(300, list.get(4).getDefaultValue());
        assertEquals("Timeout in seconds to graceful shutdown Camel.", list.get(4).getDescription());

        assertEquals("tracing", list.get(25).getName());
        assertEquals("boolean", list.get(25).getJavaType());
        assertTrue(list.get(25).isDeprecated());
    }
}
