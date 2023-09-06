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
package org.apache.camel.impl;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultEndpointRegistry;
import org.apache.camel.impl.engine.SimpleCamelContext;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.support.NormalizedUri;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultEndpointRegistryTest {

    @Test
    public void testMigration() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.start();
        DefaultEndpointRegistry reg = (DefaultEndpointRegistry) ctx.getEndpointRegistry();

        // creates a new endpoint after context is stated and therefore dynamic
        ctx.getEndpoint("direct:error");
        assertTrue(reg.isDynamic("direct:error"));

        ctx.removeEndpoints("direct:error");

        // mark we are setting up routes (done = false)
        ctx.getCamelContextExtension().setupRoutes(false);
        ctx.getEndpoint("direct:error");
        assertTrue(reg.isStatic("direct:error"));
    }

    @Test
    public void testMigrationRoute() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("direct:error")
                        .maximumRedeliveries(2)
                        .redeliveryDelay(0));

                from("direct:error")
                        .routeId("error")
                        .errorHandler(deadLetterChannel("log:dead?level=ERROR"))
                        .to("mock:error")
                        .to("file:error");
            }
        });
        ctx.start();

        EndpointRegistry reg = ctx.getEndpointRegistry();
        assertTrue(reg.isStatic("direct:error"));
        assertTrue(reg.isStatic("mock:error"));
        assertTrue(reg.isStatic("file:error"));
    }

    //Testing the issue https://issues.apache.org/jira/browse/CAMEL-19295
    @Test
    public void testConcurrency() throws InterruptedException {

        SimpleCamelContext context = new SimpleCamelContext();
        context.start();

        ProducerTemplate producerTemplate = context.createProducerTemplate();
        EndpointRegistry<NormalizedUri> endpointRegistry = context.getEndpointRegistry();

        int nThreads = 4;
        ExecutorService executorService = Executors.newFixedThreadPool(nThreads);
        int iterations = 500;

        for (int j = 0; j < iterations; j++) {
            CountDownLatch allThreadCompletionSemaphore = new CountDownLatch(nThreads);
            for (int i = 0; i < nThreads; i++) {

                executorService.submit(() -> {

                    producerTemplate.requestBody("controlbus:route?routeId=route1&action=ACTION_STATUS&loggingLevel=off", null,
                            ServiceStatus.class);
                    producerTemplate.requestBody("controlbus:route?routeId=route2&action=ACTION_STATUS&loggingLevel=off", null,
                            ServiceStatus.class);
                    producerTemplate.requestBody("controlbus:route?routeId=route3&action=ACTION_STATUS&loggingLevel=off", null,
                            ServiceStatus.class);
                    producerTemplate.requestBody("controlbus:route?routeId=route4&action=ACTION_STATUS&loggingLevel=off", null,
                            ServiceStatus.class);
                    producerTemplate.requestBody("controlbus:route?routeId=route5&action=ACTION_STATUS&loggingLevel=off", null,
                            ServiceStatus.class);

                    allThreadCompletionSemaphore.countDown();

                });
            }

            allThreadCompletionSemaphore.await();

            assertNotNull(endpointRegistry.values().toArray());

        }

        executorService.shutdown();

    }

}
