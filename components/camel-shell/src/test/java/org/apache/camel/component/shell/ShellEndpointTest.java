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
package org.apache.camel.component.shell;

import java.util.HashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShellEndpointTest {

    private CamelContext context;
    private ShellComponent component;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        component = context.getComponent("shell", ShellComponent.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        context.stop();
    }

    @Test
    void testPromptSetFromUri() throws Exception {
        ShellEndpoint endpoint = (ShellEndpoint) component.createEndpoint("shell:myapp", "myapp", new HashMap<>());
        assertEquals("myapp", endpoint.getPrompt());
    }

    @Test
    void testDefaultColor() throws Exception {
        ShellEndpoint endpoint = (ShellEndpoint) component.createEndpoint("shell:camel", "camel", new HashMap<>());
        assertEquals("cyan", endpoint.getColor());
    }

    @Test
    void testColorCanBeOverridden() throws Exception {
        HashMap<String, Object> params = new HashMap<>();
        params.put("color", "green");
        ShellEndpoint endpoint = (ShellEndpoint) component.createEndpoint("shell:camel?color=green", "camel", params);
        assertEquals("green", endpoint.getColor());
    }

    @Test
    void testCreateProducerThrowsUnsupportedOperation() throws Exception {
        ShellEndpoint endpoint = (ShellEndpoint) component.createEndpoint("shell:camel", "camel", new HashMap<>());
        assertThrows(UnsupportedOperationException.class, endpoint::createProducer);
    }

    @Test
    void testEndpointIsNotNull() throws Exception {
        ShellEndpoint endpoint = (ShellEndpoint) component.createEndpoint("shell:camel", "camel", new HashMap<>());
        assertNotNull(endpoint);
    }
}
