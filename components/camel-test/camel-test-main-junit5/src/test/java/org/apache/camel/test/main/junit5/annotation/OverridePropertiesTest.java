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
package org.apache.camel.test.main.junit5.annotation;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.PropertyInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.main.MainConfigurationProperties;
import org.apache.camel.test.main.junit5.CamelMainTest;
import org.apache.camel.test.main.junit5.Configure;
import org.apache.camel.test.main.junit5.common.MyConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test ensuring that the default properties can be overridden.
 */
@CamelMainTest(properties = "name=John")
class OverridePropertiesTest {

    @PropertyInject("name")
    String name;

    @EndpointInject("mock:out")
    MockEndpoint mock;

    @EndpointInject("direct:in")
    ProducerTemplate template;

    @Configure
    private void configure(MainConfigurationProperties configuration) {  //NOPMD
        // Add the configuration class
        configuration.addConfiguration(MyConfiguration.class);
    }

    @Test
    void shouldOverrideDefaultProperties() throws Exception {
        mock.expectedBodiesReceived("Hello John!");
        String result = template.requestBody((Object) null, String.class);
        mock.assertIsSatisfied();
        assertEquals("Hello John!", result);
    }

    @Nested
    @CamelMainTest(properties = "name2=Willow")
    class NestedTest {

        @PropertyInject("name2")
        String name2;

        @Test
        void shouldSupportNestedTest() throws Exception {
            assertEquals("John", name);
            assertEquals("Willow", name2);
        }

        @Nested
        class SuperNestedTest {

            @Test
            void shouldSupportNestedTest() throws Exception {
                assertEquals("John", name);
                assertEquals("Willow", name2);
            }
        }
    }
}
