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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Isolated
@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class MainPropertyPlaceholderWithEnvTest {

    public static final String ENV_PROPERTY_PLACEHOLDER_LOCATION
            = MainHelper.toEnvVar(MainConstants.PROPERTY_PLACEHOLDER_LOCATION);
    public static final String ENV_INITIAL_PROPERTIES_LOCATION = MainHelper.toEnvVar(MainConstants.INITIAL_PROPERTIES_LOCATION);
    public static final String ENV_OVERRIDE_PROPERTIES_LOCATION
            = MainHelper.toEnvVar(MainConstants.OVERRIDE_PROPERTIES_LOCATION);

    private static final Map<String, String> THE_CASE_INSENSITIVE_ENVIRONMENT = new HashMap<>();

    protected final Map<String, String> env = new HashMap<>();
    protected final Map<String, String> sys = new HashMap<>();

    @Test
    public void testPropertyPlaceholderLocation() {
        envVariable(ENV_PROPERTY_PLACEHOLDER_LOCATION, "classpath:default.properties");

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
        envVariable(ENV_PROPERTY_PLACEHOLDER_LOCATION, "classpath:default.properties");
        sysVariable(MainConstants.PROPERTY_PLACEHOLDER_LOCATION, "classpath:user.properties");

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
        envVariable(ENV_INITIAL_PROPERTIES_LOCATION, "classpath:initial.properties");

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
        envVariable(ENV_INITIAL_PROPERTIES_LOCATION, "classpath:default.properties");
        sysVariable(MainConstants.INITIAL_PROPERTIES_LOCATION, "classpath:user.properties");

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
        envVariable(ENV_INITIAL_PROPERTIES_LOCATION, "classpath:initial.properties");

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
        envVariable(ENV_OVERRIDE_PROPERTIES_LOCATION, "classpath:override.properties");

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
        envVariable(ENV_OVERRIDE_PROPERTIES_LOCATION, "classpath:override.properties");

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
        envVariable(ENV_OVERRIDE_PROPERTIES_LOCATION, "classpath:default.properties");
        sysVariable(MainConstants.OVERRIDE_PROPERTIES_LOCATION, "classpath:user.properties");

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
        envVariable(ENV_INITIAL_PROPERTIES_LOCATION, "classpath:initial.properties");
        envVariable(ENV_OVERRIDE_PROPERTIES_LOCATION, "classpath:override.properties");
        envVariable(ENV_PROPERTY_PLACEHOLDER_LOCATION, "classpath:default.properties,classpath:user.properties");

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

    @AfterEach
    protected void tearDown() {
        for (Map.Entry<String, String> e : env.entrySet()) {
            doEnvVariable(e.getKey(), e.getValue());
        }
        env.clear();
        for (Map.Entry<String, String> e : sys.entrySet()) {
            doSysVariable(e.getKey(), e.getValue());
        }
        sys.clear();
    }

    protected void envVariable(String name, String value) {
        env.put(name, System.getenv(name));
        doEnvVariable(name, value);
    }

    protected void sysVariable(String name, String value) {
        sys.put(name, System.getProperty(name));
        doSysVariable(name, value);
    }

    private void doEnvVariable(String name, String value) {
        if (value != null) {
            getEditableMapOfVariables().put(name, value);
            getTheCaseInsensitiveEnvironment().put(name, value);
        } else {
            getEditableMapOfVariables().remove(name);
            getTheCaseInsensitiveEnvironment().remove(name);
        }
    }

    private void doSysVariable(String name, String value) {
        if (value != null) {
            System.setProperty(name, value);
        } else {
            System.getProperties().remove(name);
        }
    }

    private static Map<String, String> getEditableMapOfVariables() {
        Class<?> classOfMap = System.getenv().getClass();
        try {
            return getFieldValue(classOfMap, System.getenv(), "m");
        } catch (IllegalAccessException e) {
            throw new RuntimeException(
                    "System Rules cannot access the field"
                                       + " 'm' of the map System.getenv().",
                    e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(
                    "System Rules expects System.getenv() to"
                                       + " have a field 'm' but it has not.",
                    e);
        }
    }

    /*
     * The names of environment variables are case-insensitive in Windows.
     * Therefore it stores the variables in a TreeMap named
     * theCaseInsensitiveEnvironment.
     */
    private static Map<String, String> getTheCaseInsensitiveEnvironment() {
        try {
            Class<?> processEnvironment = Class.forName("java.lang.ProcessEnvironment");
            return getFieldValue(
                    processEnvironment, null, "theCaseInsensitiveEnvironment");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                    "System Rules expects the existence of"
                                       + " the class java.lang.ProcessEnvironment but it does not"
                                       + " exist.",
                    e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(
                    "System Rules cannot access the static"
                                       + " field 'theCaseInsensitiveEnvironment' of the class"
                                       + " java.lang.ProcessEnvironment.",
                    e);
        } catch (NoSuchFieldException e) {
            //this field is only available for Windows so return a unused map
            return THE_CASE_INSENSITIVE_ENVIRONMENT;
        }
    }

    private static Map<String, String> getFieldValue(
            Class<?> klass,
            Object object,
            String name)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = klass.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<String, String>) field.get(object);
    }

}
