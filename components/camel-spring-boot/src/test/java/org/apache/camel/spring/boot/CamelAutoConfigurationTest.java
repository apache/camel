/**
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
package org.apache.camel.spring.boot;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.TypeConverter;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DirtiesContext
@RunWith(SpringRunner.class)
@EnableAutoConfiguration
@SpringBootTest(
    classes = {
        CamelAutoConfigurationTest.TestConfig.class,
        CamelAutoConfigurationTest.class,
        RouteConfigWithCamelContextInjected.class },
    properties = {
        "camel.springboot.consumerTemplateCacheSize=100",
        "camel.springboot.jmxEnabled=true",
        "camel.springboot.name=customName",
        "camel.springboot.typeConversion=true"}
)
public class CamelAutoConfigurationTest extends Assert {

    // Collaborators fixtures

    @Autowired
    CamelContext camelContext;

    @Autowired
    CamelContextConfiguration camelContextConfiguration;

    @Autowired
    ProducerTemplate producerTemplate;

    @Autowired
    ConsumerTemplate consumerTemplate;

    @Autowired
    TypeConverter typeConverter;

    // Spring context fixtures

    @EndpointInject(uri = "mock:xmlAutoLoading")
    MockEndpoint xmlAutoLoadingMock;

    // Tests

    @Test
    public void shouldCreateCamelContext() {
        assertNotNull(camelContext);
    }

    @Test
    public void shouldDetectRoutes() {
        // When
        Route route = camelContext.getRoute(TestConfig.ROUTE_ID);

        // Then
        assertNotNull(route);
    }

    @Test
    public void shouldLoadProducerTemplate() {
        assertNotNull(producerTemplate);
    }

    @Test
    public void shouldLoadConsumerTemplate() {
        assertNotNull(consumerTemplate);
    }

    @Test
    public void shouldLoadConsumerTemplateWithSizeFromProperties() {
        assertEquals(100, consumerTemplate.getMaximumCacheSize());
    }

    @Test
    public void shouldSendAndReceiveMessageWithTemplates() {
        // Given
        String message = "message";
        String seda = "seda:test";

        // When
        producerTemplate.sendBody(seda, message);
        String receivedBody = consumerTemplate.receiveBody(seda, String.class);

        // Then
        assertEquals(message, receivedBody);
    }

    @Test
    public void shouldLoadTypeConverters() {
        // Given
        Long hundred = 100L;

        // When
        Long convertedLong = typeConverter.convertTo(Long.class, hundred.toString());

        // Then
        assertEquals(hundred, convertedLong);
    }

    @Test
    public void shouldCallCamelContextConfiguration() {
        verify(camelContextConfiguration).beforeApplicationStart(camelContext);
        verify(camelContextConfiguration).afterApplicationStart(camelContext);
    }

    @Test
    public void shouldChangeContextNameViaConfigurationCallback() {
        assertEquals("customName", camelContext.getName());
        assertEquals(camelContext.getName(), camelContext.getManagementName());
    }

    @Test
    public void shouldStartRoute() {
        // Given
        String message = "msg";

        // When
        producerTemplate.sendBody("seda:test", message);
        String receivedMessage = consumerTemplate.receiveBody("seda:test", String.class);

        // Then
        assertEquals(message, receivedMessage);
    }

    @Test
    public void shouldLoadXmlRoutes() throws InterruptedException {
        // Given
        String message = "msg";
        xmlAutoLoadingMock.expectedBodiesReceived(message);

        // When
        producerTemplate.sendBody("direct:xmlAutoLoading", message);

        // Then
        xmlAutoLoadingMock.assertIsSatisfied();
    }

    @Configuration
    public static class TestConfig {
        // Constants
        static final String ROUTE_ID = "testRoute";

        // Test bean
        @Bean
        RouteBuilder routeBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:test").routeId(ROUTE_ID).to("mock:test");
                }
            };
        }

        @Bean
        CamelContextConfiguration camelContextConfiguration() {
            return mock(CamelContextConfiguration.class);
        }
    }
}