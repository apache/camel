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
package org.apache.camel.support.component;

import java.util.HashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ApiMethodPropertiesHelperTest {

    private static final String TEST_PREFIX = "CamelTest.";

    private static final String PROPERTY_1 = TEST_PREFIX + "property1";
    private static final String PROPERTY_2 = TEST_PREFIX + "property2";
    private static final String PROPERTY_3 = TEST_PREFIX + "property3";
    private static final String PROPERTY_4 = TEST_PREFIX + "property4";
    // test camel case property names
    private static final String PROPERTY_5 = TEST_PREFIX.substring(0, TEST_PREFIX.length() - 1) + "Property5";

    private static final String VALUE_1 = "value1";
    private static final long VALUE_2 = 2;
    private static final String VALUE_3 = "value3";
    private static final String VALUE_4 = "true";
    private static final String VALUE_5 = "CamelCaseValue";

    private static ApiMethodPropertiesHelper<TestComponentConfiguration> propertiesHelper
            = new ApiMethodPropertiesHelper<TestComponentConfiguration>(
                    new DefaultCamelContext(), TestComponentConfiguration.class,
                    TEST_PREFIX) {
            };

    @Test
    public void testGetExchangeProperties() throws Exception {
        final CamelContext camelContext = new DefaultCamelContext();
        MockEndpoint mock = new MockEndpoint(null, new MockComponent(camelContext));

        final HashMap<String, Object> properties = new HashMap<>();
        final DefaultExchange exchange = DefaultExchange.newFromEndpoint(mock);
        exchange.getIn().setHeader(PROPERTY_1, VALUE_1);
        exchange.getIn().setHeader(PROPERTY_2, VALUE_2);
        exchange.getIn().setHeader(PROPERTY_3, VALUE_3);
        exchange.getIn().setHeader(PROPERTY_4, VALUE_4);
        exchange.getIn().setHeader(PROPERTY_5, VALUE_5);
        propertiesHelper.getExchangeProperties(exchange, properties);
        assertEquals(5, properties.size());
    }

    @Test
    public void testGetEndpointProperties() throws Exception {
        final HashMap<String, Object> properties = new HashMap<>();
        final TestEndpointConfiguration endpointConfiguration = new TestEndpointConfiguration();
        endpointConfiguration.setProperty1(VALUE_1);
        endpointConfiguration.setProperty2(VALUE_2);
        endpointConfiguration.setProperty3(VALUE_3);
        endpointConfiguration.setProperty4(Boolean.valueOf(VALUE_4));
        propertiesHelper.getEndpointProperties(new DefaultCamelContext(), endpointConfiguration, properties);
        assertEquals(2, properties.size());
    }

    @Test
    public void testGetEndpointPropertyNames() throws Exception {
        final TestEndpointConfiguration endpointConfiguration = new TestEndpointConfiguration();
        endpointConfiguration.setProperty1(VALUE_1);
        endpointConfiguration.setProperty4(Boolean.valueOf(VALUE_4));
        assertEquals(1, propertiesHelper.getEndpointPropertyNames(new DefaultCamelContext(), endpointConfiguration).size());
    }

    @Test
    public void testGetValidEndpointProperties() throws Exception {
        assertEquals(2,
                propertiesHelper.getValidEndpointProperties(new DefaultCamelContext(), new TestEndpointConfiguration()).size());
    }

    @SuppressWarnings("unused")
    private static class TestComponentConfiguration {
        private String property1;
        private Long property2;

        public String getProperty1() {
            return property1;
        }

        public void setProperty1(String property1) {
            this.property1 = property1;
        }

        public long getProperty2() {
            return property2;
        }

        public void setProperty2(Long property2) {
            this.property2 = property2;
        }
    }

    @SuppressWarnings("unused")
    private static class TestEndpointConfiguration extends TestComponentConfiguration {
        private String property3;
        private Boolean property4;

        public String getProperty3() {
            return property3;
        }

        public void setProperty3(String property3) {
            this.property3 = property3;
        }

        public Boolean getProperty4() {
            return property4;
        }

        public void setProperty4(Boolean property4) {
            this.property4 = property4;
        }
    }

}
