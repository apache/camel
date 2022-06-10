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

import java.util.Map;
import java.util.Properties;

import org.apache.camel.spi.RestConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainRestConfigurationTest {

    @Test
    public void testRestConfiguration() {
        Properties properties = new Properties();
        properties.setProperty("camel.rest.component", "platform-http");
        properties.setProperty("camel.rest.enableCORS", "true");
        properties.setProperty("camel.rest.apiContextPath", "/openapi");
        properties.setProperty("camel.rest.apiVendorExtension", "true");
        properties.setProperty("camel.rest.apiProperties[dummyKey]", "Dummy Value");
        properties.setProperty("camel.rest.apiProperties[api.title]", "My Title");
        properties.setProperty("camel.rest.apiProperties[api.version]", "1.2.3");
        properties.setProperty("camel.rest.apiProperties[base.path]", "/mybase");

        Main main = new Main();
        main.setOverrideProperties(properties);

        try {
            main.start();

            RestConfiguration rf = main.getCamelContext().getRestConfiguration();
            assertEquals("platform-http", rf.getComponent());
            assertTrue(rf.isEnableCORS());
            assertEquals("/openapi", rf.getApiContextPath());
            assertTrue(rf.isApiVendorExtension());

            Map<String, Object> map = rf.getApiProperties();
            Assertions.assertNotNull(map);

            assertEquals("Dummy Value", map.get("dummyKey"));
            assertEquals("My Title", map.get("api.title"));
            assertEquals("1.2.3", map.get("api.version"));
            assertEquals("/mybase", map.get("base.path"));
        } finally {
            main.stop();
        }
    }
}
