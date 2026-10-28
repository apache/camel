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
package org.apache.camel.component.minio;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.apache.camel.spi.ExchangeFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The same tests with pooled exchanges, and the exchange of an object whose body cannot be got is given back to the
 * pool.
 */
class MinioConsumerObjectThatCannotBeGotPooledExchangeTest extends MinioConsumerObjectThatCannotBeGotTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        PooledExchangeFactory factory = new PooledExchangeFactory();
        factory.setStatisticsEnabled(true);
        context.getCamelContextExtension().setExchangeFactory(factory);
        return context;
    }

    @Test
    void exchangeOfAnObjectWhoseBodyCannotBeGotIsGivenBackToThePool() throws Exception {
        server.put("a.txt", "a");
        server.put("b.txt", "b");
        server.put("c.txt", "c");
        server.refuseGets("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("a", "c");

        // polled from the test: the route is synchronous, so the exchanges are done when poll returns
        addConsumerRoute("&startScheduler=false&exceptionHandler=#recordErrors");
        MinioConsumer consumer = (MinioConsumer) context.getRoute("consumer").getConsumer();
        context.getCamelContextExtension().getExchangeFactoryManager().setStatisticsEnabled(true);

        assertEquals(2, consumer.poll());

        mock.assertIsSatisfied();
        // the exchange created for b.txt is given back, as are those of a.txt and c.txt
        ExchangeFactory.Statistics stats = context.getCamelContextExtension().getExchangeFactoryManager().getStatistics();
        assertEquals(3, stats.getCreatedCounter() + stats.getAcquiredCounter());
        assertEquals(3, stats.getReleasedCounter());
        assertEquals(0, stats.getDiscardedCounter());
    }
}
