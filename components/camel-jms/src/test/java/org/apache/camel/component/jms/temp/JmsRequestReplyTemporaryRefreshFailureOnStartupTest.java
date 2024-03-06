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
package org.apache.camel.component.jms.temp;

import java.util.concurrent.TimeUnit;

import jakarta.jms.ConnectionFactory;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisVMService;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tags({ @Tag("not-parallel") })
public final class JmsRequestReplyTemporaryRefreshFailureOnStartupTest extends CamelTestSupport {

    public static ArtemisService service = new ArtemisVMService.ReusableArtemisVMService();

    private final Long recoveryInterval = 1000L;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        service.initialize();

        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(service.serviceAddress());
        camelContext.addComponent("jms", jmsComponentAutoAcknowledge(connectionFactory));

        service.shutdown();

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to(ExchangePattern.InOut,
                                "jms:queue:JmsRequestReplyTemporaryRefreshFailureOnStartupTest?recoveryInterval="
                                                   + recoveryInterval)
                        .to("mock:result");

                from("jms:queue:JmsRequestReplyTemporaryRefreshFailureOnStartupTest")
                        .setBody(simple("pong"));
            }
        };
    }

    @DisplayName("Test that throws and exception on connection failure")
    @Test
    @Order(1)
    public void testTemporaryRefreshFailureOnStartup() throws Exception {
        //the first message will fail
        //the second message must be handled
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(0);

        //the first request will return with an error
        //because the broker is not started yet
        Assertions.assertThrows(Exception.class,
                () -> template.requestBody("direct:start", "ping"));

        mockEndpoint.assertIsSatisfied();
    }

    @DisplayName("Test that reconnects after dealing with an exception on connection failure")
    @Test
    @Order(2)
    public void testTemporaryRefreshFailureOnStartupReconnect() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.reset();
        mockEndpoint.expectedMessageCount(1);

        service.initialize();

        Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Assertions.assertDoesNotThrow(() -> template.requestBody("direct:start", "ping"));
                });

        mockEndpoint.assertIsSatisfied();
    }
}
