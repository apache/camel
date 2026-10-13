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
package org.apache.camel.component.aws2.ddbstream;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.apache.camel.spi.ExchangeFactory;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The same with the pooled exchange factory: the exchanges created for the records that a suspending consumer does not
 * route are given back to the pool, once, so the records routed after the resume do not share an exchange.
 */
class Ddb2StreamConsumerSuspendedBatchPooledExchangeTest extends Ddb2StreamConsumerSuspendedBatchTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        PooledExchangeFactory factory = new PooledExchangeFactory();
        factory.setStatisticsEnabled(true);
        context.getCamelContextExtension().setExchangeFactory(factory);
        return context;
    }

    @Test
    void exchangesOfTheRecordsThatWereNotRoutedAreGivenBackToThePool() throws Exception {
        client.put("1", "2", "3", "4");
        suspendAtRecord = "2";

        context.getCamelContextExtension().getExchangeFactoryManager().setStatisticsEnabled(true);
        context.getRouteController().startRoute(ROUTE_ID);
        awaitRouteSuspended();
        assertEquals(List.of("1", "2"), routed);
        // one exchange per record of the response: two were routed, and the two that were not are given back as well
        awaitExchangesTakenAndReleased(4);

        context.getRouteController().resumeRoute(ROUTE_ID);
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(List.of("1", "2", "3", "4"), routed, "records routed after the resume"));
        // and only once: the pool does not hold an exchange twice
        awaitExchangesTakenAndReleased(6);
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
