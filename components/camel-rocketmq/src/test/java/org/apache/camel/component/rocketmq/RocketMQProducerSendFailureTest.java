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
package org.apache.camel.component.rocketmq;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.AsyncProducer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.rocketmq.client.exception.MQClientException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A send that fails asynchronously (here: the client rejects an empty message before it contacts the name server) must
 * fail the exchange, also for an InOut exchange without replyToTopic. With waitForSendResult=false the exchange is done
 * before the send result arrives, so the failure is only logged.
 */
public class RocketMQProducerSendFailureTest extends CamelTestSupport {

    private static final String ROCKETMQ_URI = "rocketmq:START_TOPIC?namesrvAddr=localhost:9876&producerGroup=p1";

    @Test
    public void testInOnlySendFailure() {
        Exchange exchange = template.send(ROCKETMQ_URI, ExchangePattern.InOnly, e -> e.getIn().setBody(""));

        assertInstanceOf(MQClientException.class, exchange.getException());
    }

    @Test
    public void testInOnlySendFailureWithWaitForSendResult() {
        Exchange exchange = template.send(ROCKETMQ_URI + "&waitForSendResult=true", ExchangePattern.InOnly,
                e -> e.getIn().setBody(""));

        assertInstanceOf(MQClientException.class, exchange.getException());
    }

    @Test
    public void testInOnlyWithoutWaitForSendResultIsDoneSynchronously() throws Exception {
        AsyncProducer producer = context.getEndpoint(ROCKETMQ_URI + "&waitForSendResult=false").createAsyncProducer();
        producer.start();
        try {
            Exchange exchange = new DefaultExchange(context, ExchangePattern.InOnly);
            exchange.getIn().setBody("");
            AtomicReference<Boolean> doneSync = new AtomicReference<>();
            AtomicReference<Thread> callbackThread = new AtomicReference<>();

            boolean sync = producer.process(exchange, d -> {
                doneSync.set(d);
                callbackThread.set(Thread.currentThread());
            });

            // a processor that returns true must have called the callback, with doneSync=true, before it returns
            assertTrue(sync);
            assertEquals(Boolean.TRUE, doneSync.get());
            assertSame(Thread.currentThread(), callbackThread.get());
            assertNull(exchange.getException());
        } finally {
            producer.stop();
        }
    }

    @Test
    public void testInOutSendFailureWithoutReplyToTopic() {
        Exchange exchange = template.send(ROCKETMQ_URI, ExchangePattern.InOut, e -> e.getIn().setBody(""));

        assertInstanceOf(MQClientException.class, exchange.getException());
    }
}
