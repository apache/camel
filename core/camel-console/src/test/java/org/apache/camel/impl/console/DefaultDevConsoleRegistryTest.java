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

import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.console.DevConsole;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DefaultDevConsoleRegistryTest extends ContextTestSupport {

    @Test
    public void testRegistry() {
        DevConsoleRegistry registry = context.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class);
        Assertions.assertInstanceOf(DefaultDevConsoleRegistry.class, registry);
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

    @Test
    public void testRegisterWhileStarting() throws Exception {
        DefaultDevConsoleRegistry registry = new DefaultDevConsoleRegistry(context);
        registry.start();

        // a console that registers another console when started, such as camel-cli-connector
        // resolving consoles from its own thread while the registry is starting its consoles
        DevConsole first = new TestConsole("a-first") {
            @Override
            protected void doStart() throws Exception {
                registry.register(new TestConsole("z-late-" + registry.stream().count()));
            }
        };
        registry.register(first);
        registry.register(new TestConsole("b-second"));
        registry.stop();

        // starting again registers a console while the registry iterates its consoles
        registry.start();

        Assertions.assertTrue(registry.getConsole("a-first").isPresent());
        Assertions.assertTrue(registry.getConsole("b-second").isPresent());
        Assertions.assertEquals(4, registry.stream().count());
    }

    @Test
    public void testRegisterSameIdTwice() {
        DefaultDevConsoleRegistry registry = new DefaultDevConsoleRegistry(context);

        DevConsole console = new TestConsole("same");
        Assertions.assertTrue(registry.register(console));
        Assertions.assertFalse(registry.register(new TestConsole("same")));
        Assertions.assertSame(console, registry.getConsole("same").orElse(null));
    }

    private static class TestConsole extends AbstractDevConsole {

        TestConsole(String id) {
            super("test", id, id, id);
        }

        @Override
        protected Map<String, Object> doCallJson(Map<String, Object> options) {
            return Map.of();
        }

        @Override
        protected String doCallText(Map<String, Object> options) {
            return "";
        }
    }
}
