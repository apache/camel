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

import org.apache.camel.CamelContext;
import org.apache.camel.console.DevConsole;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.OrderedLocationProperties;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainConfigurationDevConsoleTest {

    private CamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        context.setDevConsole(true);
        context.start();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    public void testMainConfigurationConsoleEmpty() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("main-configuration");
        assertNotNull(con);
        assertEquals("camel", con.getGroup());
        assertEquals("main-configuration", con.getId());

        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON);
        assertNotNull(out);
        assertTrue(out.isEmpty());
    }

    @Test
    public void testMainConfigurationConsoleWithEntries() {
        DevConsole resolved = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("main-configuration");
        assertNotNull(resolved);
        MainConfigurationDevConsole con = (MainConfigurationDevConsole) resolved;

        OrderedLocationProperties props = new OrderedLocationProperties();
        props.put("properties", "camel.main.name", "myApp");
        con.addStartupConfiguration(props);

        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON);
        assertNotNull(out);

        JsonArray configurations = out.getCollection("configurations");
        assertNotNull(configurations);
        assertFalse(configurations.isEmpty());

        JsonObject entry = (JsonObject) configurations.get(0);
        assertEquals("camel.main.name", entry.getString("key"));
        assertEquals("myApp", entry.getString("value"));
    }
}
