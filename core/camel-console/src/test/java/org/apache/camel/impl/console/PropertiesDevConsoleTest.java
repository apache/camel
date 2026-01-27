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
package org.apache.camel.impl.console;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.console.DevConsole;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for PropertiesDevConsole with properties configured.
 */
public class PropertiesDevConsoleTest extends AbstractDevConsoleTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        Properties props = new Properties();
        props.put("myProp", "myValue");
        props.put("anotherProp", "anotherValue");
        context.getPropertiesComponent().setInitialProperties(props);
        return context;
    }

    @Test
    public void testPropertiesConsole() {
        DevConsole console = assertConsoleExists("properties", "camel");

        String textOut = callText(console);
        assertTrue(textOut.contains("Properties loaded from locations:"));

        JsonObject jsonOut = callJson(console);
        assertNotNull(jsonOut.get("locations"));
    }
}
