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
package org.apache.camel.component.sjms.batch;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsCommonTestSupport;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BatchConsumerExceptionHandlerTest extends JmsCommonTestSupport {

    private static final String SJMS_QUEUE_NAME = "sjms:queue:batch.consumer.BatchConsumerExceptionHandlerTest";
    private static final String MOCK_START = "mock:start";
    private static final String MOCK_FINISH = "mock:finish";
    private static final String EXCEPTION_MESSAGE = "Always throw an exception!";

    private CapturingExceptionHandler exceptionHandler;

    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createSingletonVMService();

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    protected String getBrokerUri() {
        return service.serviceAddress();
    }

    /**
     * Set up the Broker
     */
    @BeforeEach
    protected final void setupBrokerUri() {
        brokerUri = getBrokerUri();
    }

    @Override
    protected void setupFactoryExternal(ActiveMQConnectionFactory factory) {
        setupFactoryExternal(factory, service);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        exceptionHandler = new CapturingExceptionHandler();
        camelContext.getRegistry().bind("myHandler", exceptionHandler);

        return camelContext;
    }

    @Test
    public void testBatchConsumerExceptionHandler() throws InterruptedException {
        MockEndpoint mockStart = getMockEndpoint(MOCK_START);
        MockEndpoint mockFinish = getMockEndpoint(MOCK_FINISH);

        mockStart.expectedMessageCount(1);
        mockFinish.expectedMessageCount(0);

        template.sendBodyAndHeader(SJMS_QUEUE_NAME, "Hello there!", "cheese", "y");

        assertTrue(exceptionHandler.latch.await(5, TimeUnit.SECONDS),
                "An exchange exception should be reported to the endpoint exceptionHandler");
        assertNotNull(exceptionHandler.captured.get(), "The exceptionHandler should always get a cause");
        assertEquals("java.lang.IllegalArgumentException: " + EXCEPTION_MESSAGE, exceptionHandler.captured.get().getMessage());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                fromF("%s?batching=true&exceptionHandler=#myHandler", SJMS_QUEUE_NAME)
                        .to(MOCK_START)
                        .process(exchange -> {
                            throw new IllegalArgumentException(EXCEPTION_MESSAGE);
                        })
                        .to(MOCK_FINISH);
            }
        };
    }

    private static final class CapturingExceptionHandler implements ExceptionHandler {

        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<Throwable> captured = new AtomicReference<>();

        @Override
        public void handleException(Throwable exception) {
            handleException(null, null, exception);
        }

        @Override
        public void handleException(String message, Throwable exception) {
            handleException(message, null, exception);
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            captured.compareAndSet(null, exception);
            latch.countDown();
        }
    }
}
