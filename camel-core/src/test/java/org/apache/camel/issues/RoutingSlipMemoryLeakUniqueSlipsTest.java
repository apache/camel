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
package org.apache.camel.issues;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.RoutingSlip;

public class RoutingSlipMemoryLeakUniqueSlipsTest extends ContextTestSupport {

    private static final AtomicInteger counter = new AtomicInteger(0);

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/output");
        super.setUp();
    }

    /**
     * Reproducer for the memory leak: CAMEL-10048/CAMEL-10050
     */
    public void testMemoryLeakInExceptionHandlerCaching() throws Exception {
        int messageCount = 100;
        for (int i = 0; i < messageCount; i++) {
            template.sendBody("direct:start", "message " + i);
        }
        RoutingSlip routingSlip = context.getProcessor("memory-leak", RoutingSlip.class);
        assertNotNull(routingSlip);

        Map<?, ?> errorHandlers = getRoutingSlipErrorHandlers(routingSlip);
        assertEquals("Error handlers cache must contain only one value", 1, errorHandlers.size());
    }

    private Map<?, ?> getRoutingSlipErrorHandlers(RoutingSlip routingSlip) throws Exception {
        Field errorHandlersField = routingSlip.getClass().getDeclaredField("errorHandlers");
        errorHandlersField.setAccessible(true);
        Map<?, ?> errorHandlers = (Map<?, ?>) errorHandlersField.get(routingSlip);
        return errorHandlers;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                from("direct:start")
                    .routingSlip(method(SlipProvider.class)).id("memory-leak");
            }
        };
    }

    public static class SlipProvider {

        public String computeSlip(String body) {
            /*
             * It is important to have a processor here, that does not extend
             * AsyncProcessor. Only in this case
             * AsyncProcessorConverterHelper.convert() creates a new object,
             * thus leading to a memory leak. For example, if you replace file
             * endpoint with mock endpoint, then everything goes fine, because
             * MockEndpoint.createProducer() creates an implementation of
             * AsyncProcessor.
             */
            return "mock:" + counter.incrementAndGet();
        }
    }
}