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
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.TypeConverterSupport;
import org.apache.camel.support.component.ApiMethodHelperTest.TestMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producer of an API component converts the message body to the type of the parameter that inBody names.
 */
public class AbstractApiProducerTest extends ContextTestSupport {

    private static final ApiMethodHelper<TestMethod> METHOD_HELPER
            = new ApiMethodHelper<>(TestMethod.class, Map.of(), List.of());

    @Test
    public void testBodyIsConvertedToTheTypeOfTheInBodyParameter() throws Exception {
        Endpoint endpoint = createEndpoint("greetMe", "name");

        // the parameter is a String, and the endpoint configurer does not know it, as it is not an endpoint option
        Object out = template.requestBody(endpoint, "World".getBytes());
        assertEquals("Greetings World", out);
    }

    @Test
    public void testBodyOfTheTypeOfTheInBodyParameter() throws Exception {
        Endpoint endpoint = createEndpoint("greetMe", "name");

        Object out = template.requestBody(endpoint, "World");
        assertEquals("Greetings World", out);
    }

    @Test
    public void testBodyIsConvertedToAPrimitiveParameter() throws Exception {
        Endpoint endpoint = createEndpoint("greetTimes", "times", Map.of("name", "World"));

        Object out = template.requestBody(endpoint, "2");
        assertTrue(out instanceof String[], "Should be an array: " + out);
        assertEquals(2, ((String[]) out).length);
    }

    @Test
    public void testArgumentHeaderSetByTheConversionOfTheBody() throws Exception {
        // as the converter of a Google Drive file sets the header of its media content
        context.getTypeConverterRegistry().addTypeConverter(String.class, TestName.class, new TypeConverterSupport() {
            @Override
            public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
                exchange.getIn().setHeader("CamelTestApi.times", 2);
                return type.cast(((TestName) value).name());
            }
        });
        Endpoint endpoint = createEndpoint("greetTimes", "name");

        Object out = template.requestBody(endpoint, new TestName("World"));
        assertTrue(out instanceof String[], "Should be an array: " + out);
        assertEquals(2, ((String[]) out).length);
    }

    record TestName(String name) {
    }

    private Endpoint createEndpoint(String methodName, String inBody) throws Exception {
        return createEndpoint(methodName, inBody, Map.of());
    }

    private Endpoint createEndpoint(String methodName, String inBody, Map<String, Object> options) throws Exception {
        TestApiComponent component = new TestApiComponent(context);
        TestApiEndpoint endpoint = new TestApiEndpoint("test-api:" + methodName, component, methodName);
        endpoint.setInBody(inBody);
        endpoint.configureProperties(new HashMap<>(options));
        context.addEndpoint(endpoint.getEndpointUri(), endpoint);
        return endpoint;
    }

    enum TestApiName implements ApiName {
        TEST;

        @Override
        public String getName() {
            return "test";
        }
    }

    public static class TestConfiguration {
    }

    @SuppressWarnings("unused")
    public static class TestEndpointConfiguration extends TestConfiguration {
        private String name;
        private Integer times;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getTimes() {
            return times;
        }

        public void setTimes(Integer times) {
            this.times = times;
        }
    }

    static class TestApiComponent extends DefaultComponent {

        // as generated for an API component: it knows the options of the endpoint, not the parameters of the methods
        private final PropertyConfigurer configurer = new TestEndpointConfigurer();

        TestApiComponent(CamelContext context) {
            super(context);
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PropertyConfigurer getEndpointPropertyConfigurer() {
            return configurer;
        }
    }

    static class TestEndpointConfigurer implements PropertyConfigurer, PropertyConfigurerGetter {

        @Override
        public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
            return false;
        }

        @Override
        public Class<?> getOptionType(String name, boolean ignoreCase) {
            return "inBody".equalsIgnoreCase(name) ? String.class : null;
        }

        @Override
        public Object getOptionValue(Object target, String name, boolean ignoreCase) {
            return null;
        }
    }

    static class TestApiEndpoint extends AbstractApiEndpoint<TestApiName, TestConfiguration> {

        private final ApiMethodPropertiesHelper<TestConfiguration> propertiesHelper;

        TestApiEndpoint(String uri, Component component, String methodName) {
            super(uri, component, TestApiName.TEST, methodName, METHOD_HELPER, new TestEndpointConfiguration());
            this.propertiesHelper = new ApiMethodPropertiesHelper<>(
                    component.getCamelContext(), TestConfiguration.class, "CamelTestApi.") {
            };
        }

        @Override
        protected ApiMethodPropertiesHelper<TestConfiguration> getPropertiesHelper() {
            return propertiesHelper;
        }

        @Override
        protected void afterConfigureProperties() {
            // noop
        }

        @Override
        public Object getApiProxy(ApiMethod method, Map<String, Object> args) {
            return new TestProxy();
        }

        @Override
        protected String getThreadProfileName() {
            return "TestApiThreadProfile";
        }

        @Override
        public Producer createProducer() {
            return new AbstractApiProducer<>(this, propertiesHelper) {
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            throw new UnsupportedOperationException();
        }
    }
}
