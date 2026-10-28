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
package org.apache.camel.component.aws2.s3;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.apache.camel.spi.ExchangeFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The same with the pooled exchange factory: the exchange of an object that is skipped after it was created (its
 * download failed) is never routed, and is given back to the pool, once, so the objects after it do not share it.
 */
class AWS2S3ConsumerDeletedObjectPooledExchangeTest extends AWS2S3ConsumerDeletedObjectTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        PooledExchangeFactory factory = new PooledExchangeFactory();
        factory.setStatisticsEnabled(true);
        context.getCamelContextExtension().setExchangeFactory(factory);
        return context;
    }

    @Test
    void exchangeOfAnObjectWhoseDownloadFailsIsGivenBackToThePool() throws Exception {
        s3.put("a.txt", "a");
        s3.put("b.txt", "b");
        s3.put("c.txt", "c");
        s3.put("d.txt", "d");
        // the connection is closed while the content of b.txt is read into the body
        s3.cutBodyOfNextGet("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a", "c", "d");
        AWS2S3Consumer consumer = startRoute("");
        context.getCamelContextExtension().getExchangeFactoryManager().setStatisticsEnabled(true);

        assertEquals(3, consumer.poll());

        mock.assertIsSatisfied();
        assertFalse(consumer.getEndpoint().getInProgressRepository().contains("b.txt"), "b.txt is still in progress");
        ExchangeFactory.Statistics stats
                = context.getCamelContextExtension().getExchangeFactoryManager().getStatistics();
        // every exchange that was taken (those of a, c and d that were routed, and the one of b.txt that was not) was
        // given back, and only once: the pool does not hold an exchange twice
        assertEquals(4, stats.getCreatedCounter() + stats.getAcquiredCounter());
        assertEquals(4, stats.getReleasedCounter());
        assertEquals(0, stats.getDiscardedCounter());

        // and b.txt is consumed by the next poll
        mock.reset();
        mock.expectedBodiesReceived("b");
        assertEquals(1, consumer.poll());
        mock.assertIsSatisfied();
    }
}
