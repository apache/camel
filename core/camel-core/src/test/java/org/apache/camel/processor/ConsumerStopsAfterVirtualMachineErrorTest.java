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
package org.apache.camel.processor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContextAware;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheck.State;
import org.apache.camel.impl.health.ConsumerHealthCheck;
import org.apache.camel.support.DefaultConsumer;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A consumer that pulls work in a loop stops when one of its exchanges fails with a {@link VirtualMachineError} that
 * the route does not catch: its health check reports DOWN until the route is restarted. A route that catches the
 * {@link Error} explicitly keeps its consumer going (CAMEL-27515).
 */
public class ConsumerStopsAfterVirtualMachineErrorTest extends ContextTestSupport {

    private final AtomicInteger sedaCalls = new AtomicInteger();
    private final AtomicInteger fileCalls = new AtomicInteger();

    @Test
    public void sedaConsumerStopsUntilTheRouteIsRestarted() throws Exception {
        DefaultConsumer consumer = (DefaultConsumer) context.getRoute("seda-fatal").getConsumer();
        ConsumerHealthCheck check = consumerHealthCheck("seda-fatal");

        template.sendBody("seda:fatal", "a");
        await().atMost(10, TimeUnit.SECONDS).until(() -> consumer.getFatalError() != null);
        assertInstanceOf(StackOverflowError.class, consumer.getFatalError());
        HealthCheck.Result result = check.callReadiness();
        assertEquals(State.DOWN, result.getState());
        assertTrue(result.getMessage().orElse("").contains("Restart the route"), result.getMessage().orElse(""));

        // the consumer no longer consumes
        template.sendBody("seda:fatal", "b");
        await().during(Duration.ofMillis(500)).atMost(2, TimeUnit.SECONDS).until(() -> sedaCalls.get() == 1);

        // restarting the route brings it back: it consumes the waiting message
        context.getRouteController().stopRoute("seda-fatal");
        context.getRouteController().startRoute("seda-fatal");
        await().atMost(10, TimeUnit.SECONDS).until(() -> sedaCalls.get() == 2);
    }

    @Test
    public void consumerGoesOnWhenTheRouteCatchesTheError() throws Exception {
        getMockEndpoint("mock:caught").expectedMessageCount(2);

        template.sendBody("seda:caught", "a");
        template.sendBody("seda:caught", "b");

        assertMockEndpointsSatisfied();
        assertNull(((DefaultConsumer) context.getRoute("seda-caught").getConsumer()).getFatalError());
        assertEquals(State.UP, consumerHealthCheck("seda-caught").callReadiness().getState());
    }

    @Test
    public void pollingConsumerStopsAfterAnUnhandledVirtualMachineError() {
        DefaultConsumer consumer = (DefaultConsumer) context.getRoute("file-fatal").getConsumer();

        template.sendBodyAndHeader(fileUri(), "Hello", Exchange.FILE_NAME, "first.txt");
        await().atMost(10, TimeUnit.SECONDS).until(() -> consumer.getFatalError() != null);
        assertEquals(State.DOWN, consumerHealthCheck("file-fatal").callReadiness().getState());

        // the consumer no longer polls: a new file stays there
        template.sendBodyAndHeader(fileUri(), "Hello", Exchange.FILE_NAME, "second.txt");
        await().during(Duration.ofMillis(500)).atMost(2, TimeUnit.SECONDS).until(() -> fileCalls.get() == 1);
    }

    private ConsumerHealthCheck consumerHealthCheck(String routeId) {
        ConsumerHealthCheck check = new ConsumerHealthCheck(context.getRoute(routeId), "consumer:" + routeId);
        CamelContextAware.trySetCamelContext(check, context);
        return check;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:fatal").routeId("seda-fatal")
                        .process(e -> {
                            sedaCalls.incrementAndGet();
                            throw new StackOverflowError("simulated");
                        });

                from("seda:caught").routeId("seda-caught")
                        .onException(StackOverflowError.class).handled(true).to("mock:caught").end()
                        .process(e -> {
                            throw new StackOverflowError("simulated");
                        });

                from(fileUri("?initialDelay=0&delay=10")).routeId("file-fatal")
                        .process(e -> {
                            fileCalls.incrementAndGet();
                            throw new StackOverflowError("simulated");
                        });
            }
        };
    }
}
