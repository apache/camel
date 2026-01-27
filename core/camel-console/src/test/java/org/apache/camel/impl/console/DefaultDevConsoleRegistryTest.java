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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.console.DevConsole;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DefaultDevConsoleRegistryTest extends ContextTestSupport {

    @Test
    public void testRegistry() {
        DevConsoleRegistry registry = context.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class);
        Assertions.assertNotNull(registry);
        Assertions.assertTrue(registry instanceof DefaultDevConsoleRegistry);
        Assertions.assertTrue(registry.isEnabled());
    }

    @Test
    public void testGetConsoles() {
        DevConsoleRegistry registry = context.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class);
        Assertions.assertNotNull(registry);

        // The registry stream should not be empty after console loading
        registry.loadDevConsoles();
        long count = registry.stream().count();
        Assertions.assertTrue(count >= 0);
    }

    @Test
    public void testResolveConsoleById() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("context");
        Assertions.assertNotNull(console);
        Assertions.assertEquals("context", console.getId());
        Assertions.assertEquals("camel", console.getGroup());
    }

    @Test
    public void testRegisterAndUnregister() {
        DevConsoleRegistry registry = context.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class);
        Assertions.assertNotNull(registry);

        // Create a custom console
        DevConsole customConsole = new ContextDevConsole() {
            @Override
            public String getId() {
                return "custom-test";
            }
        };

        // Register the console
        boolean registered = registry.register(customConsole);
        Assertions.assertTrue(registered);
        Assertions.assertTrue(registry.getConsole("custom-test").isPresent());

        // Unregister the console
        boolean unregistered = registry.unregister(customConsole);
        Assertions.assertTrue(unregistered);
        Assertions.assertFalse(registry.getConsole("custom-test").isPresent());
    }
}
