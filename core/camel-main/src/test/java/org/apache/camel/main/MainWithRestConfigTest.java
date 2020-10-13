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

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MainWithRestConfigTest {
    @Test
    public void testRestConfigurationPropagation() {
        Properties properties = new Properties();
        properties.setProperty("camel.rest.port", "8989");
        properties.setProperty("camel.rest.component", "undertow");

        Main main = new Main();
        try {
            main.configure().addRoutesBuilder(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    restConfiguration();
                }
            });
            main.setOverrideProperties(properties);
            main.setDefaultPropertyPlaceholderLocation("false");
            main.start();

            assertEquals(8989, main.getCamelContext().getRestConfiguration().getPort());
            assertEquals("undertow", main.getCamelContext().getRestConfiguration().getComponent());
        } finally {
            main.stop();
        }
    }

    @Test
    public void testRestConfigurationOverride() {
        Properties properties = new Properties();
        properties.setProperty("camel.rest.port", "8989");
        properties.setProperty("camel.rest.component", "undertow");

        Main main = new Main();
        try {
            main.configure().addRoutesBuilder(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    restConfiguration()
                            .component("jetty");
                }
            });
            main.setOverrideProperties(properties);
            main.setDefaultPropertyPlaceholderLocation("false");
            main.start();

            assertEquals(8989, main.getCamelContext().getRestConfiguration().getPort());
            assertEquals("jetty", main.getCamelContext().getRestConfiguration().getComponent());
        } finally {
            main.stop();
        }
    }
}
