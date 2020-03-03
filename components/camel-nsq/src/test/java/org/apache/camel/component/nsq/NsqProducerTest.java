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
package org.apache.camel.component.nsq;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.brainlag.nsq.NSQConsumer;
import com.github.brainlag.nsq.lookup.DefaultNSQLookup;
import com.github.brainlag.nsq.lookup.NSQLookup;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class NsqProducerTest extends NsqTestSupport {

    private static final int NUMBER_OF_MESSAGES = 10000;
    private static final String TEST_MESSAGE = "Hello NSQProducer!";

    @Test
    public void testProducer() throws Exception {

        CountDownLatch lock = new CountDownLatch(1);

        template.sendBody("direct:send", TEST_MESSAGE);

        AtomicInteger counter = new AtomicInteger(0);
        NSQLookup lookup = new DefaultNSQLookup();
        lookup.addLookupAddress("localhost", 4161);

        NSQConsumer consumer = new NSQConsumer(lookup, "test", "testconsumer", message -> {
            counter.incrementAndGet();
            message.finished();
            lock.countDown();
            assertEquals(TEST_MESSAGE, new String(message.getMessage()));
        });
        consumer.start();

        lock.await(30, TimeUnit.SECONDS);

        assertEquals(1, counter.get());
        consumer.shutdown();
    }

    @Test
    public void testLoadProducer() throws Exception {

        CountDownLatch lock = new CountDownLatch(NUMBER_OF_MESSAGES);

        for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            template.sendBody("direct:send", "test" + i);
        }

        AtomicInteger counter = new AtomicInteger(0);
        NSQLookup lookup = new DefaultNSQLookup();
        lookup.addLookupAddress("localhost", 4161);

        NSQConsumer consumer = new NSQConsumer(lookup, "test", "testconsumer", message -> {
            counter.incrementAndGet();
            message.finished();
            lock.countDown();
            assertEquals(1, message.getAttempts());
        });
        consumer.start();

        lock.await(30, TimeUnit.SECONDS);

        assertEquals(NUMBER_OF_MESSAGES, counter.get());
        consumer.shutdown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                NsqComponent nsq = context.getComponent("nsq", NsqComponent.class);
                nsq.setServers(getNsqProducerUrl());

                from("direct:send").to("nsq://test");
            }
        };
    }
}
