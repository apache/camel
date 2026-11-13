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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.TransientCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * With transferException, an exception that is sent back as the reply commits the transacted session, whatever its
 * type: the reply reaches the caller and the request is not redelivered.
 */
public class JmsTransferExceptionTransactedTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new TransientCamelContextExtension();

    private static final String REQUEST_OPTIONS = "?transferException=true&requestTimeout=5000";
    private static final String CONSUMER_OPTIONS = "?transacted=true&transferException=true";

    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicInteger wrappedCounter = new AtomicInteger();
    protected CamelContext context;
    protected ProducerTemplate template;

    @Test
    public void testRuntimeCamelExceptionIsSentBack() {
        assertSentBackOnce("activemq:queue:JmsTransferExceptionTransactedTest", counter);
    }

    @Test
    public void testExceptionCausedByRuntimeCamelExceptionIsSentBack() {
        assertSentBackOnce("activemq:queue:JmsTransferExceptionTransactedTest.wrapped", wrappedCounter);
    }

    private void assertSentBackOnce(String uri, AtomicInteger processed) {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody(uri + REQUEST_OPTIONS, "Hello World"));
        assertEquals("Kaboom", e.getCause().getMessage());

        // the request was committed together with the reply, so it is not redelivered
        await().during(1, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS).until(() -> processed.get() == 1);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected JmsComponent setupComponent(
            CamelContext camelContext, ConnectionFactory connectionFactory, String componentName) {
        JmsComponent component = super.setupComponent(camelContext, connectionFactory, componentName);
        component.setObjectMessageEnabled(true);
        return component;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:JmsTransferExceptionTransactedTest" + CONSUMER_OPTIONS)
                        .process(exchange -> {
                            counter.incrementAndGet();
                            throw new RuntimeCamelException("Kaboom");
                        });

                from("activemq:queue:JmsTransferExceptionTransactedTest.wrapped" + CONSUMER_OPTIONS)
                        .process(exchange -> {
                            wrappedCounter.incrementAndGet();
                            throw new IllegalStateException("Kaboom", new RuntimeCamelException("Inner"));
                        });
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
    }
}
