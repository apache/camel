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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.management.*", "javax.script.*"})
@PrepareForTest(MainHelper.class)
public class MainPropertyPlaceholderWithEnvTest {
    public static final String ENV_PROPERTY_PLACEHOLDER_LOCATION = MainHelper.toEnvVar(Main.PROPERTY_PLACEHOLDER_LOCATION);
    public static final String ENV_INITIAL_PROPERTIES_LOCATION = MainHelper.toEnvVar(Main.INITIAL_PROPERTIES_LOCATION);
    public static final String ENV_OVERRIDE_PROPERTIES_LOCATION = MainHelper.toEnvVar(Main.OVERRIDE_PROPERTIES_LOCATION);

    @Before
    public void setUp() {
        PowerMockito.mockStatic(System.class);
    }

    @Test
    public void testPropertyPlaceholderLocation() {
        when(System.getenv(ENV_PROPERTY_PLACEHOLDER_LOCATION))
            .thenReturn("classpath:default.properties");

        Main main = new Main();
        try {
            main.start();
            assertEquals("default", main.getCamelContext().resolvePropertyPlaceholders("{{hello}}"));
        } finally {
            main.stop();
        }
    }

    @Test
    public void testPropertyPlaceholderOrdering() {
        when(System.getenv(MainHelper.toEnvVar(Main.PROPERTY_PLACEHOLDER_LOCATION)))
            .thenReturn("classpath:default.properties");
        when(System.getProperty(Main.PROPERTY_PLACEHOLDER_LOCATION))
            .thenReturn("classpath:user.properties");

        Main main = new Main();
        try {
            main.start();
            assertEquals("user", main.getCamelContext().resolvePropertyPlaceholders("{{type}}"));
        } finally {
            main.stop();
        }
    }

    @Test
    public void testInitialProperties() {
        when(System.getenv(ENV_INITIAL_PROPERTIES_LOCATION))
            .thenReturn("classpath:initial.properties");

        Main main = new Main();
        try {
            main.start();
            assertEquals("initial", main.getCamelContext().resolvePropertyPlaceholders("{{type}}"));
        } finally {
            main.stop();
        }
    }

    @Test
    public void testInitialPropertiesOrdering() {
        when(System.getenv(ENV_INITIAL_PROPERTIES_LOCATION))
            .thenReturn("classpath:default.properties");
        when(System.getProperty(Main.INITIAL_PROPERTIES_LOCATION))
            .thenReturn("classpath:user.properties");

        Main main = new Main();
        try {
            main.start();
            assertEquals("user", main.getCamelContext().resolvePropertyPlaceholders("{{type}}"));
        } finally {
            main.stop();
        }
    }

    @Test
    public void testInstanceInitialPropertiesOrdering() {
        when(System.getenv(ENV_INITIAL_PROPERTIES_LOCATION))
            .thenReturn("classpath:initial.properties");

        Main main = new Main();
        try {
            Properties properties = new Properties();
            properties.setProperty("type", "custom");

            main.setInitialProperties(properties);
            main.start();
            assertEquals("custom", main.getCamelContext().resolvePropertyPlaceholders("{{type}}"));
        } finally {
            main.stop();
        }
    }

    @Test
    public void testOverrideProperties() {
        when(System.getenv(ENV_OVERRIDE_PROPERTIES_LOCATION))
            .thenReturn("classpath:override.properties");

        Main main = new Main();
        try {
            main.start();
            assertEquals("override", main.getCamelContext().resolvePropertyPlaceholders("{{type}}"));
        } finally {
            main.stop();
        }
    }

    @Test
    public void testInstanceOverridePropertiesOrdering() {
        when(System.getenv(ENV_OVERRIDE_PROPERTIES_LOCATION))
            .thenReturn("classpath:override.properties");

        Main main = new Main();
        try {
            Properties properties = new Properties();
            properties.setProperty("type", "custom");

            main.setOverrideProperties(properties);
            main.start();
            assertEquals("custom", main.getCamelContext().resolvePropertyPlaceholders("{{type}}"));
        } finally {
            main.stop();
        }
    }

    @Test
    public void testOverridePropertiesOrdering() {
        when(System.getenv(ENV_OVERRIDE_PROPERTIES_LOCATION))
            .thenReturn("classpath:default.properties");
        when(System.getProperty(Main.OVERRIDE_PROPERTIES_LOCATION))
            .thenReturn("classpath:user.properties");

        Main main = new Main();
        try {
            main.start();
            assertEquals("user", main.getCamelContext().resolvePropertyPlaceholders("{{type}}"));
        } finally {
            main.stop();
        }
    }


    @Test
    public void testAll() {
        when(System.getenv(ENV_INITIAL_PROPERTIES_LOCATION))
            .thenReturn("classpath:initial.properties");
        when(System.getenv(ENV_OVERRIDE_PROPERTIES_LOCATION))
            .thenReturn("classpath:override.properties");
        when(System.getenv(ENV_PROPERTY_PLACEHOLDER_LOCATION))
            .thenReturn("classpath:default.properties,classpath:user.properties");

        Main main = new Main();
        try {
            main.start();

            assertEquals("default", main.getCamelContext().resolvePropertyPlaceholders("{{hello}}"));
            assertEquals("override", main.getCamelContext().resolvePropertyPlaceholders("{{type}}"));
            assertEquals("user-value", main.getCamelContext().resolvePropertyPlaceholders("{{user-key}}"));
            assertEquals("initial-value", main.getCamelContext().resolvePropertyPlaceholders("{{initial-key}}"));
        } finally {
            main.stop();
        }
    }
}
