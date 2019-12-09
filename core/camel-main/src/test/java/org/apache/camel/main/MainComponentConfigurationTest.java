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

import org.apache.camel.main.support.MyDummyComponent;
import org.junit.Assert;
import org.junit.Test;

public class MainComponentConfigurationTest extends Assert {
    @Test
    public void testComponentConfiguration() {
        Properties properties = new Properties();
        properties.setProperty("camel.component.dummy.configuration.log", "true");
        properties.setProperty("camel.component.dummy.component-value", "component-value");
        properties.setProperty("camel.component.dummy.configuration.nested.value", "nested-value");
        properties.setProperty("camel.component.dummy.configuration", "#class:org.apache.camel.main.support.MyDummyConfiguration");

        Main main = new Main();
        try {
            MyDummyComponent dummy = new MyDummyComponent(false);

            main.bind("dummy", dummy);
            main.setOverrideProperties(properties);
            main.setDefaultPropertyPlaceholderLocation("false");
            main.start();

            assertEquals("component-value", dummy.getComponentValue());

            assertNotNull(dummy.getConfiguration());
            assertTrue(dummy.getConfiguration().isLog());
            assertNotNull(dummy.getConfiguration().getNested());
            assertEquals("nested-value", dummy.getConfiguration().getNested().getValue());
        } finally {
            main.stop();
        }
    }
}

