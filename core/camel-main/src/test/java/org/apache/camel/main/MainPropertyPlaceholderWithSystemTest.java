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

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Isolated
@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class MainPropertyPlaceholderWithSystemTest {
    @Test
    public void testCustomPropertyPlaceholderLocation() {
        Main main = new Main();
        try {
            System.setProperty(MainConstants.PROPERTY_PLACEHOLDER_LOCATION, "classpath:default.properties");
            main.start();
            assertEquals("default", main.getCamelContext().resolvePropertyPlaceholders("{{hello}}"));
        } finally {
            System.clearProperty(MainConstants.PROPERTY_PLACEHOLDER_LOCATION);
            main.stop();
        }
    }

    @Test
    public void testInitialProperties() {
        Main main = new Main();
        try {
            System.setProperty(MainConstants.INITIAL_PROPERTIES_LOCATION, "classpath:initial.properties");
            main.start();
            assertEquals("initial", main.getCamelContext().resolvePropertyPlaceholders("{{type}}"));
        } finally {
            System.clearProperty(MainConstants.INITIAL_PROPERTIES_LOCATION);
            main.stop();
        }
    }

    @Test
    public void testInstanceInitialPropertiesOrdering() {
        Main main = new Main();
        try {
            Properties properties = new Properties();
            properties.setProperty("type", "custom");

            System.setProperty(MainConstants.INITIAL_PROPERTIES_LOCATION, "classpath:initial.properties");
            main.setInitialProperties(properties);
            main.start();
            assertEquals("custom", main.getCamelContext().resolvePropertyPlaceholders("{{type}}"));
        } finally {
            System.clearProperty(MainConstants.INITIAL_PROPERTIES_LOCATION);
            main.stop();
        }
    }

    @Test
    public void testOverrideProperties() {
        Main main = new Main();
        try {
            System.setProperty(MainConstants.OVERRIDE_PROPERTIES_LOCATION, "classpath:override.properties");
            main.start();
            assertEquals("override", main.getCamelContext().resolvePropertyPlaceholders("{{type}}"));
        } finally {
            System.clearProperty(MainConstants.OVERRIDE_PROPERTIES_LOCATION);
            main.stop();
        }
    }

    @Test
    public void testInstanceOverridePropertiesOrdering() {
        Main main = new Main();
        try {
            Properties properties = new Properties();
            properties.setProperty("type", "custom");

            System.setProperty(MainConstants.OVERRIDE_PROPERTIES_LOCATION, "classpath:override.properties");
            main.setOverrideProperties(properties);
            main.start();
            assertEquals("custom", main.getCamelContext().resolvePropertyPlaceholders("{{type}}"));
        } finally {
            System.clearProperty(MainConstants.OVERRIDE_PROPERTIES_LOCATION);
            main.stop();
        }
    }

    @Test
    public void testAll() {
        Main main = new Main();
        try {
            System.setProperty(MainConstants.INITIAL_PROPERTIES_LOCATION, "classpath:initial.properties");
            System.setProperty(MainConstants.OVERRIDE_PROPERTIES_LOCATION, "classpath:override.properties");
            System.setProperty(MainConstants.PROPERTY_PLACEHOLDER_LOCATION,
                    "classpath:default.properties,classpath:user.properties");

            main.start();

            assertEquals("default", main.getCamelContext().resolvePropertyPlaceholders("{{hello}}"));
            assertEquals("override", main.getCamelContext().resolvePropertyPlaceholders("{{type}}"));
            assertEquals("user-value", main.getCamelContext().resolvePropertyPlaceholders("{{user-key}}"));
            assertEquals("initial-value", main.getCamelContext().resolvePropertyPlaceholders("{{initial-key}}"));
        } finally {
            System.clearProperty(MainConstants.INITIAL_PROPERTIES_LOCATION);
            System.clearProperty(MainConstants.OVERRIDE_PROPERTIES_LOCATION);
            System.clearProperty(MainConstants.PROPERTY_PLACEHOLDER_LOCATION);
            main.stop();
        }
    }
}
