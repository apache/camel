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
package org.apache.camel.component.sjms.consumer;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.apache.camel.impl.engine.PooledProcessorExchangeFactory;
import org.apache.camel.spi.PooledObjectFactory;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InOnlyPooledExchangeTest extends JmsTestSupport {

    private static final String SJMS_QUEUE_NAME = "sjms:queue:in.only.consumer.queue.InOnlyPooledExchangeTest";
    private static final String MOCK_RESULT = "mock:result";

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ExtendedCamelContext ecc = camelContext.getCamelContextExtension();

        ecc.setExchangeFactory(new PooledExchangeFactory());
        ecc.setProcessorExchangeFactory(new PooledProcessorExchangeFactory());
        ecc.getExchangeFactory().setStatisticsEnabled(true);
        ecc.getProcessorExchangeFactory().setStatisticsEnabled(true);

        return camelContext;
    }

    @Test
    public void testSynchronous() throws Exception {
        final String expectedBody = "Hello World";
        MockEndpoint mock = getMockEndpoint(MOCK_RESULT);
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(expectedBody);

        template.sendBody(SJMS_QUEUE_NAME, expectedBody);

        mock.assertIsSatisfied();

        Awaitility.waitAtMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            PooledObjectFactory.Statistics stat
                    = context.getCamelContextExtension().getExchangeFactoryManager().getStatistics();
            assertEquals(1, stat.getCreatedCounter());
            assertEquals(0, stat.getAcquiredCounter());
            assertEquals(1, stat.getReleasedCounter());
            assertEquals(0, stat.getDiscardedCounter());
        });
    }

    @Test
    public void testTwoSynchronous() throws Exception {
        MockEndpoint mock = getMockEndpoint(MOCK_RESULT);
        mock.expectedBodiesReceived("Hello World", "Bye World");

        template.sendBody(SJMS_QUEUE_NAME, "Hello World");
        template.sendBody(SJMS_QUEUE_NAME, "Bye World");

        mock.assertIsSatisfied();

        Awaitility.waitAtMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            PooledObjectFactory.Statistics stat
                    = context.getCamelContextExtension().getExchangeFactoryManager().getStatistics();
            assertEquals(1, stat.getCreatedCounter());
            assertEquals(1, stat.getAcquiredCounter());
            assertEquals(2, stat.getReleasedCounter());
            assertEquals(0, stat.getDiscardedCounter());
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(SJMS_QUEUE_NAME)
                        .to(MOCK_RESULT);
            }
        };
    }

}
