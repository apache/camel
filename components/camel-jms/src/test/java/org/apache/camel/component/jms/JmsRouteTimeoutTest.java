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

package org.apache.camel.component.jms;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Unit test for testing request timeout with a InOut exchange.
 */
public class JmsRouteTimeoutTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    public void testTimeout() {
        // send an in-out with a timeout for 1 sec
        Exception e = assertThrows(
                Exception.class,
                () -> template.requestBody(
                        "activemq:queue:JmsRouteTimeoutTest.testTimeout?requestTimeout=1000", "Hello World"),
                "Should have timed out with an exception");

        assertInstanceOf(
                ExchangeTimedOutException.class, e.getCause(), "Should have timed out with a timeout exception");
    }

    @Test
    public void testNoTimeout() {
        // START SNIPPET: e1
        // send a in-out with a timeout for 5 sec
        Object out = assertDoesNotThrow(() -> template.requestBody(
                "activemq:queue:JmsRouteTimeoutTest.testTimeout?requestTimeout=5000", "Hello World"));
        // END SNIPPET: e1
        assertEquals("Bye World", out);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:queue:JmsRouteTimeoutTest.testTimeout")
                        .delay(3000)
                        .transform(constant("Bye World"));
                from("activemq:queue:JmsRouteTimeoutTest.testNoTimeout").transform(constant("Bye World"));
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }
}
