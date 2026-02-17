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

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellComponentTest {

    private CamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() throws Exception {
        context.stop();
    }

    @Test
    void testComponentIsRegistered() {
        ShellComponent component = context.getComponent("shell", ShellComponent.class);
        assertNotNull(component);
    }

    @Test
    void testComponentDefaultBannerEnabled() {
        ShellComponent component = context.getComponent("shell", ShellComponent.class);
        assertTrue(component.isShowBanner());
    }

    @Test
    void testComponentDefaultBannerResource() {
        ShellComponent component = context.getComponent("shell", ShellComponent.class);
        assertEquals("camel-shell-banner.txt", component.getBannerResource());
    }

    @Test
    void testEndpointCreatedWithCorrectScheme() throws Exception {
        ShellComponent component = context.getComponent("shell", ShellComponent.class);
        ShellEndpoint endpoint = (ShellEndpoint) component.createEndpoint("shell:myapp", "myapp", new java.util.HashMap<>());
        assertNotNull(endpoint);
        assertEquals("myapp", endpoint.getPrompt());
    }
}
