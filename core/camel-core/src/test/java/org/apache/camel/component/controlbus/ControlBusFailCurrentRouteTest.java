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
package org.apache.camel.component.controlbus;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.apache.camel.spi.PooledObjectFactory;
import org.apache.camel.spi.RouteError;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fail of the route of the exchange runs asynchronously, so it must mark the route as failed with the exception the
 * exchange had when the control bus step ran, even when the task runs after the exchange is done and back in the pool.
 */
public class ControlBusFailCurrentRouteTest extends ContextTestSupport {

    // holds the control bus thread until the exchange is back in the pool
    private final CountDownLatch exchangeReleased = new CountDownLatch(1);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ExtendedCamelContext ecc = camelContext.getCamelContextExtension();
        ecc.setExchangeFactory(new PooledExchangeFactory());
        ecc.getExchangeFactory().setStatisticsEnabled(true);
        camelContext.getExecutorServiceManager().addThreadFactoryListener((source, factory) -> {
            if (source instanceof ControlBusComponent) {
                return task -> factory.newThread(() -> {
                    awaitExchangeReleased();
                    task.run();
                });
            }
            return factory;
        });
        return camelContext;
    }

    private void awaitExchangeReleased() {
        try {
            exchangeReleased.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void testFailCurrentRouteFromOnException() {
        PooledObjectFactory.Statistics stat
                = context.getCamelContextExtension().getExchangeFactoryManager().getStatistics();
        await().atMost(5, TimeUnit.SECONDS).until(() -> stat.getReleasedCounter() >= 1);
        exchangeReleased.countDown();

        Route route = context.getRoute("fail");
        await().atMost(5, TimeUnit.SECONDS).until(() -> route.getLastError() != null);
        RouteError error = route.getLastError();
        assertTrue(error.isUnhealthy());
        assertEquals(RouteError.Phase.STOP, error.getPhase());
        assertEquals("Forced", error.getException().getMessage());
        assertTrue(context.getRouteController().getRouteStatus("fail").isStopped());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:fail?delay=10&repeatCount=1").routeId("fail")
                        .onException(IllegalArgumentException.class).handled(true)
                            .to("controlbus:route?routeId=current&action=fail")
                        .end()
                        .throwException(new IllegalArgumentException("Forced"));
            }
        };
    }
}
