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
package org.apache.camel.component.directvm;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StopWatch;
import org.junit.Test;

public class DirectVmProducerBlockingTest extends ContextTestSupport {

    @Test
    public void testProducerBlocksForSuspendedConsumer() throws Exception {
        DirectVmEndpoint endpoint = getMandatoryEndpoint("direct-vm:suspended", DirectVmEndpoint.class);
        endpoint.getConsumer().suspend();

        StopWatch watch = new StopWatch();
        try {
            template.sendBody("direct-vm:suspended?block=true&timeout=500&failIfNoConsumers=false", "hello world");
            fail("Expected CamelExecutionException");
        } catch (CamelExecutionException e) {
            DirectVmConsumerNotAvailableException cause = assertIsInstanceOf(DirectVmConsumerNotAvailableException.class, e.getCause());
            assertIsInstanceOf(CamelExchangeException.class, cause);
            assertTrue(watch.taken() > 490);
        }
    }

    @Test
    public void testProducerBlocksWithNoConsumers() throws Exception {
        DirectVmEndpoint endpoint = getMandatoryEndpoint("direct-vm:suspended", DirectVmEndpoint.class);
        endpoint.getConsumer().suspend();

        StopWatch watch = new StopWatch();
        try {
            template.sendBody("direct-vm:start?block=true&timeout=500&failIfNoConsumers=false", "hello world");
            fail("Expected CamelExecutionException");
        } catch (CamelExecutionException e) {
            DirectVmConsumerNotAvailableException cause = assertIsInstanceOf(DirectVmConsumerNotAvailableException.class, e.getCause());
            assertIsInstanceOf(CamelExchangeException.class, cause);

            assertTrue(watch.taken() > 490);
        }
    }
    
    @Test
    public void testProducerBlocksFailIfNoConsumerFalse() throws Exception {
        DirectVmEndpoint endpoint = getMandatoryEndpoint("direct-vm:suspended", DirectVmEndpoint.class);
        endpoint.getConsumer().suspend();

        try {
            template.sendBody("direct-vm:start?block=true&timeout=500&failIfNoConsumers=true", "hello world");
            fail("Expected CamelExecutionException");
        } catch (CamelExecutionException e) {
            DirectVmConsumerNotAvailableException cause = assertIsInstanceOf(DirectVmConsumerNotAvailableException.class, e.getCause());
            assertIsInstanceOf(CamelExchangeException.class, cause);
        }
    }

    @Test
    public void testProducerBlocksResumeTest() throws Exception {
        context.getRouteController().suspendRoute("foo");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(200);
                    log.info("Resuming consumer");
                    context.getRouteController().resumeRoute("foo");
                } catch (Exception e) {
                    // ignore
                }
            }
        });

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct-vm:suspended?block=true&timeout=1000&failIfNoConsumers=false", "hello world");

        assertMockEndpointsSatisfied();

        executor.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct-vm:suspended").routeId("foo").to("mock:result");
            }
        };
    }

}
