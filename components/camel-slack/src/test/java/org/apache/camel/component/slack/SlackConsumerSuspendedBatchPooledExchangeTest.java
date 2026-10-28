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
package org.apache.camel.component.slack;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.apache.camel.spi.ExchangeFactory;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The same with the pooled exchange factory: the exchanges created for the messages that a suspending consumer does not
 * route are given back to the pool, once, so the messages routed after the resume do not share an exchange.
 */
class SlackConsumerSuspendedBatchPooledExchangeTest extends SlackConsumerSuspendedBatchTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        PooledExchangeFactory factory = new PooledExchangeFactory();
        factory.setStatisticsEnabled(true);
        context.getCamelContextExtension().setExchangeFactory(factory);
        return context;
    }

    @Test
    void exchangesOfTheMessagesThatWereNotRoutedAreGivenBackToThePool() throws Exception {
        context.getCamelContextExtension().getExchangeFactoryManager().setStatisticsEnabled(true);
        startRouteAndReceiveFirstMessage();
        suspendAtMessage = "m4";

        postBurst();
        awaitRouteSuspended();
        assertEquals(List.of("m5", "m4"), routed);
        // one exchange for the first message, and one per message of the burst: two were routed, and the two that
        // were not are given back as well
        awaitExchangesTakenAndReleased(5);

        context.getRouteController().resumeRoute(ROUTE_ID);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(List.of("m5", "m4", "m3", "m2"), routed, "messages routed after the resume"));
        // and only once: the pool does not hold an exchange twice
        awaitExchangesTakenAndReleased(7);
    }

    private void awaitExchangesTakenAndReleased(int expected) {
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            ExchangeFactory.Statistics stats
                    = context.getCamelContextExtension().getExchangeFactoryManager().getStatistics();
            assertEquals(expected, stats.getCreatedCounter() + stats.getAcquiredCounter(), "exchanges taken");
            assertEquals(expected, stats.getReleasedCounter(), "exchanges given back to the pool");
            assertEquals(0, stats.getDiscardedCounter(), "exchanges discarded");
        });
    }
}
