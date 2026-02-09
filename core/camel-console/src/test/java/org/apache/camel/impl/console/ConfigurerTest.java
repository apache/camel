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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConfigurerTest extends ContextTestSupport {

    @Test
    public void testSendDevConsoleConfigurer() {
        SendDevConsoleConfigurer configurer = new SendDevConsoleConfigurer();
        SendDevConsole console = new SendDevConsole();

        // Test configure
        Assertions.assertTrue(configurer.configure(context, console, "bodyMaxChars", "1000", false));
        Assertions.assertTrue(configurer.configure(context, console, "pollTimeout", "5000", false));
        Assertions.assertTrue(configurer.configure(context, console, "camelContext", context, false));
        Assertions.assertFalse(configurer.configure(context, console, "nonExistent", "value", false));

        // Test with ignore case
        Assertions.assertTrue(configurer.configure(context, console, "bodymaxchars", "2000", true));
        Assertions.assertTrue(configurer.configure(context, console, "polltimeout", "6000", true));

        // Test getAllOptions
        Map<String, Object> options = configurer.getAllOptions(console);
        Assertions.assertNotNull(options);
        Assertions.assertTrue(options.containsKey("BodyMaxChars"));
        Assertions.assertTrue(options.containsKey("PollTimeout"));

        // Test getOptionType
        Assertions.assertEquals(int.class, configurer.getOptionType("bodyMaxChars", false));
        Assertions.assertEquals(int.class, configurer.getOptionType("pollTimeout", false));
        Assertions.assertNull(configurer.getOptionType("nonExistent", false));

        // Test getOptionValue
        Assertions.assertNotNull(configurer.getOptionValue(console, "bodyMaxChars", false));
        Assertions.assertNotNull(configurer.getOptionValue(console, "pollTimeout", false));
        Assertions.assertNull(configurer.getOptionValue(console, "nonExistent", false));
    }

    @Test
    public void testReceiveDevConsoleConfigurer() {
        ReceiveDevConsoleConfigurer configurer = new ReceiveDevConsoleConfigurer();
        ReceiveDevConsole console = new ReceiveDevConsole();

        // Test configure
        Assertions.assertTrue(configurer.configure(context, console, "capacity", "200", false));
        Assertions.assertTrue(configurer.configure(context, console, "bodyMaxChars", "1000", false));
        Assertions.assertTrue(configurer.configure(context, console, "removeOnDump", "false", false));
        Assertions.assertFalse(configurer.configure(context, console, "nonExistent", "value", false));

        // Test with ignore case
        Assertions.assertTrue(configurer.configure(context, console, "CAPACITY", "300", true));

        // Test getAllOptions
        Map<String, Object> options = configurer.getAllOptions(console);
        Assertions.assertNotNull(options);
        Assertions.assertTrue(options.containsKey("Capacity"));

        // Test getOptionType
        Assertions.assertEquals(int.class, configurer.getOptionType("capacity", false));
        Assertions.assertEquals(int.class, configurer.getOptionType("bodyMaxChars", false));
        Assertions.assertEquals(boolean.class, configurer.getOptionType("removeOnDump", false));
        Assertions.assertNull(configurer.getOptionType("nonExistent", false));

        // Test getOptionValue
        Assertions.assertNotNull(configurer.getOptionValue(console, "capacity", false));
        Assertions.assertNotNull(configurer.getOptionValue(console, "bodyMaxChars", false));
        Assertions.assertNotNull(configurer.getOptionValue(console, "removeOnDump", false));
        Assertions.assertNull(configurer.getOptionValue(console, "nonExistent", false));
    }

    @Test
    public void testTraceDevConsoleConfigurer() {
        TraceDevConsoleConfigurer configurer = new TraceDevConsoleConfigurer();
        TraceDevConsole console = new TraceDevConsole();

        // Test configure
        Assertions.assertTrue(configurer.configure(context, console, "capacity", "200", false));
        Assertions.assertTrue(configurer.configure(context, console, "camelContext", context, false));
        Assertions.assertFalse(configurer.configure(context, console, "nonExistent", "value", false));

        // Test getAllOptions
        Map<String, Object> options = configurer.getAllOptions(console);
        Assertions.assertNotNull(options);

        // Test getOptionType
        Assertions.assertEquals(int.class, configurer.getOptionType("capacity", false));
        Assertions.assertNull(configurer.getOptionType("nonExistent", false));

        // Test getOptionValue
        Assertions.assertNotNull(configurer.getOptionValue(console, "capacity", false));
        Assertions.assertNull(configurer.getOptionValue(console, "nonExistent", false));
    }

    @Test
    public void testEventConsoleConfigurer() {
        EventConsoleConfigurer configurer = new EventConsoleConfigurer();
        EventConsole console = new EventConsole();

        // Test configure
        Assertions.assertTrue(configurer.configure(context, console, "capacity", "200", false));
        Assertions.assertTrue(configurer.configure(context, console, "camelContext", context, false));
        Assertions.assertFalse(configurer.configure(context, console, "nonExistent", "value", false));

        // Test getAllOptions
        Map<String, Object> options = configurer.getAllOptions(console);
        Assertions.assertNotNull(options);

        // Test getOptionType
        Assertions.assertEquals(int.class, configurer.getOptionType("capacity", false));
        Assertions.assertNull(configurer.getOptionType("nonExistent", false));

        // Test getOptionValue
        Assertions.assertNotNull(configurer.getOptionValue(console, "capacity", false));
        Assertions.assertNull(configurer.getOptionValue(console, "nonExistent", false));
    }

    @Test
    public void testJvmDevConsoleConfigurer() {
        JvmDevConsoleConfigurer configurer = new JvmDevConsoleConfigurer();
        JvmDevConsole console = new JvmDevConsole();

        // Test configure
        Assertions.assertTrue(configurer.configure(context, console, "camelContext", context, false));
        Assertions.assertFalse(configurer.configure(context, console, "nonExistent", "value", false));

        // Test getAllOptions
        Map<String, Object> options = configurer.getAllOptions(console);
        Assertions.assertNotNull(options);

        // Test getOptionType
        Assertions.assertNull(configurer.getOptionType("nonExistent", false));

        // Test getOptionValue
        Assertions.assertNull(configurer.getOptionValue(console, "nonExistent", false));
    }

    @Test
    public void testMemoryDevConsoleConfigurer() {
        MemoryDevConsoleConfigurer configurer = new MemoryDevConsoleConfigurer();
        MemoryDevConsole console = new MemoryDevConsole();

        // Test configure
        Assertions.assertTrue(configurer.configure(context, console, "camelContext", context, false));
        Assertions.assertFalse(configurer.configure(context, console, "nonExistent", "value", false));

        // Test getAllOptions
        Map<String, Object> options = configurer.getAllOptions(console);
        Assertions.assertNotNull(options);

        // Test getOptionType
        Assertions.assertNull(configurer.getOptionType("nonExistent", false));

        // Test getOptionValue
        Assertions.assertNull(configurer.getOptionValue(console, "nonExistent", false));
    }

    @Test
    public void testThreadDevConsoleConfigurer() {
        ThreadDevConsoleConfigurer configurer = new ThreadDevConsoleConfigurer();
        ThreadDevConsole console = new ThreadDevConsole();

        // Test configure
        Assertions.assertTrue(configurer.configure(context, console, "camelContext", context, false));
        Assertions.assertFalse(configurer.configure(context, console, "nonExistent", "value", false));

        // Test getAllOptions
        Map<String, Object> options = configurer.getAllOptions(console);
        Assertions.assertNotNull(options);

        // Test getOptionType
        Assertions.assertNull(configurer.getOptionType("nonExistent", false));

        // Test getOptionValue
        Assertions.assertNull(configurer.getOptionValue(console, "nonExistent", false));
    }

    @Test
    public void testGarbageCollectorDevConsoleConfigurer() {
        GarbageCollectorDevConsoleConfigurer configurer = new GarbageCollectorDevConsoleConfigurer();
        GarbageCollectorDevConsole console = new GarbageCollectorDevConsole();

        // Test configure
        Assertions.assertTrue(configurer.configure(context, console, "camelContext", context, false));
        Assertions.assertFalse(configurer.configure(context, console, "nonExistent", "value", false));

        // Test getAllOptions
        Map<String, Object> options = configurer.getAllOptions(console);
        Assertions.assertNotNull(options);

        // Test getOptionType
        Assertions.assertNull(configurer.getOptionType("nonExistent", false));

        // Test getOptionValue
        Assertions.assertNull(configurer.getOptionValue(console, "nonExistent", false));
    }

    @Test
    public void testSystemPropertiesDevConsoleConfigurer() {
        SystemPropertiesDevConsoleConfigurer configurer = new SystemPropertiesDevConsoleConfigurer();
        SystemPropertiesDevConsole console = new SystemPropertiesDevConsole();

        // Test configure
        Assertions.assertTrue(configurer.configure(context, console, "camelContext", context, false));
        Assertions.assertFalse(configurer.configure(context, console, "nonExistent", "value", false));

        // Test getAllOptions
        Map<String, Object> options = configurer.getAllOptions(console);
        Assertions.assertNotNull(options);

        // Test getOptionType
        Assertions.assertNull(configurer.getOptionType("nonExistent", false));

        // Test getOptionValue
        Assertions.assertNull(configurer.getOptionValue(console, "nonExistent", false));
    }

    @Test
    public void testJavaSecurityDevConsoleConfigurer() {
        JavaSecurityDevConsoleConfigurer configurer = new JavaSecurityDevConsoleConfigurer();
        JavaSecurityDevConsole console = new JavaSecurityDevConsole();

        // Test configure
        Assertions.assertTrue(configurer.configure(context, console, "camelContext", context, false));
        Assertions.assertFalse(configurer.configure(context, console, "nonExistent", "value", false));

        // Test getAllOptions
        Map<String, Object> options = configurer.getAllOptions(console);
        Assertions.assertNotNull(options);

        // Test getOptionType
        Assertions.assertNull(configurer.getOptionType("nonExistent", false));

        // Test getOptionValue
        Assertions.assertNull(configurer.getOptionValue(console, "nonExistent", false));
    }

    @Test
    public void testMessageHistoryDevConsoleConfigurer() {
        MessageHistoryDevConsoleConfigurer configurer = new MessageHistoryDevConsoleConfigurer();
        MessageHistoryDevConsole console = new MessageHistoryDevConsole();

        // Test configure
        Assertions.assertTrue(configurer.configure(context, console, "camelContext", context, false));
        Assertions.assertFalse(configurer.configure(context, console, "nonExistent", "value", false));

        // Test getAllOptions
        Map<String, Object> options = configurer.getAllOptions(console);
        Assertions.assertNotNull(options);

        // Test getOptionType
        Assertions.assertNull(configurer.getOptionType("nonExistent", false));

        // Test getOptionValue
        Assertions.assertNull(configurer.getOptionValue(console, "nonExistent", false));
    }
}
