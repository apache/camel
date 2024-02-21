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
package org.apache.camel.component.file;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileConsumerDeleteExchangePooledTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        ExtendedCamelContext extendedCamelContext = context.getCamelContextExtension();

        extendedCamelContext.getExchangeFactoryManager().setStatisticsEnabled(true);

        PooledExchangeFactory pef = new PooledExchangeFactory();
        extendedCamelContext.setExchangeFactory(pef);

        extendedCamelContext.getExchangeFactory().setStatisticsEnabled(true);
        extendedCamelContext.getProcessorExchangeFactory().setStatisticsEnabled(true);
        return context;
    }

    @Test
    public void testDelete() throws Exception {
        ExtendedCamelContext ecc = context.getCamelContextExtension();
        assertEquals(0, ecc.getExchangeFactoryManager().getStatistics().getReleasedCounter());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(1, ecc.getExchangeFactoryManager().getStatistics().getReleasedCounter());
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri("?delete=true&initialDelay=0&delay=10"))
                        .to("mock:result");
            }
        };
    }
}
