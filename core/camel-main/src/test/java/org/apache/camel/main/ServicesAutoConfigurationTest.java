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

import org.apache.camel.component.seda.SedaComponent;
import org.junit.Assert;
import org.junit.Test;

public class ServicesAutoConfigurationTest extends Assert {
    @Test
    public void testComponentAutoConfiguredWhenGlobalAutoConfigurationIsDisabled() {
        Properties properties = new Properties();
        properties.put("camel.component.enabled", "false");
        properties.put("camel.component.seda.queue-size", "1234");

        Main main = new Main();
        try {
            main.setDefaultPropertyPlaceholderLocation("false");
            main.setInitialProperties(properties);
            main.start();

            SedaComponent component = main.getCamelContext().getComponent("seda", SedaComponent.class);
            assertNotEquals(1234, component.getQueueSize());
        } finally {
            main.stop();
        }
    }

    @Test
    public void testComponentAutoConfiguredWhenGlobalAutoConfigurationIsEnabled() {
        Properties properties = new Properties();
        properties.put("camel.component.enabled", "true");
        properties.put("camel.component.seda.queue-size", "1234");

        Main main = new Main();
        try {
            main.setDefaultPropertyPlaceholderLocation("false");
            main.setInitialProperties(properties);
            main.start();

            SedaComponent component = main.getCamelContext().getComponent("seda", SedaComponent.class);
            assertEquals(1234, component.getQueueSize());
        } finally {
            main.stop();
        }
    }

    @Test
    public void testComponentAutoConfiguredWhenGlobalAutoConfigurationIsDisabledButComponentAutoConfigurationIsEnabled() {
        Properties properties = new Properties();
        properties.put("camel.component.enabled", "false");
        properties.put("camel.component.seda.enabled", "true");
        properties.put("camel.component.seda.queue-size", "1234");

        Main main = new Main();
        try {
            main.setDefaultPropertyPlaceholderLocation("false");
            main.setInitialProperties(properties);
            main.start();

            SedaComponent component = main.getCamelContext().getComponent("seda", SedaComponent.class);
            assertEquals(1234, component.getQueueSize());
        } finally {
            main.stop();
        }
    }

    @Test
    public void testComponentAutoConfiguredWhenGlobalAutoConfigurationIsEnabledButComponentAutoConfigurationIsDisabled() {
        Properties properties = new Properties();
        properties.put("camel.component.enabled", "true");
        properties.put("camel.component.seda.enabled", "false");
        properties.put("camel.component.seda.queue-size", "1234");

        Main main = new Main();
        try {
            main.setDefaultPropertyPlaceholderLocation("false");
            main.setInitialProperties(properties);
            main.start();

            SedaComponent component = main.getCamelContext().getComponent("seda", SedaComponent.class);
            assertNotEquals(1234, component.getQueueSize());
        } finally {
            main.stop();
        }
    }

    @Test
    public void testComponentAutoConfiguredDisabled() {
        Properties properties = new Properties();
        properties.put("camel.component.enabled", "false");
        properties.put("camel.component.seda.enabled", "false");
        properties.put("camel.component.seda.queue-size", "1234");

        Main main = new Main();
        try {
            main.setDefaultPropertyPlaceholderLocation("false");
            main.setInitialProperties(properties);
            main.start();

            SedaComponent component = main.getCamelContext().getComponent("seda", SedaComponent.class);
            assertNotEquals(1234, component.getQueueSize());
        } finally {
            main.stop();
        }
    }

    @Test
    public void testComponentAutoConfiguredEnabled() {
        Properties properties = new Properties();
        properties.put("camel.component.enabled", "true");
        properties.put("camel.component.seda.enabled", "true");
        properties.put("camel.component.seda.queue-size", "1234");

        Main main = new Main();
        try {
            main.setDefaultPropertyPlaceholderLocation("false");
            main.setInitialProperties(properties);
            main.start();

            SedaComponent component = main.getCamelContext().getComponent("seda", SedaComponent.class);
            assertEquals(1234, component.getQueueSize());
        } finally {
            main.stop();
        }
    }
}
