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
package org.apache.camel.component.sjms.consumer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An InOut reply that cannot be turned into a JMS message must fail the JMS message (rollback when transacted), and not
 * be dropped silently while the request is committed.
 */
public class InOutConsumerReplyFailureTest extends JmsTestSupport {

    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicInteger pojoCounter = new AtomicInteger();

    @Override
    protected boolean useJmx() {
        return false;
    }

    @Test
    public void testReplyFailureRollsBack() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody(
                        "sjms:queue:in.out.queue.InOutConsumerReplyFailureTest?replyTo=in.out.queue.InOutConsumerReplyFailureTest.reply&requestTimeout=2000",
                        "Hello World"));
        assertInstanceOf(ExchangeTimedOutException.class, e.getCause());

        // the reply could not be sent, so the request is rolled back and redelivered
        await().atMost(10, TimeUnit.SECONDS).until(() -> counter.get() >= 2);
    }

    @Test
    public void testUnmappableReplyRollsBack() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody(
                        "sjms:queue:in.out.queue.InOutConsumerReplyFailureTest.pojo?replyTo=in.out.queue.InOutConsumerReplyFailureTest.pojo.reply&requestTimeout=2000",
                        "Hello World"));
        assertInstanceOf(ExchangeTimedOutException.class, e.getCause());

        // the reply body maps to no JMS message type, so no empty reply is sent, and the request is rolled back
        await().atMost(10, TimeUnit.SECONDS).until(() -> pojoCounter.get() >= 2);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("sjms:queue:in.out.queue.InOutConsumerReplyFailureTest?transacted=true")
                        .process(exchange -> {
                            counter.incrementAndGet();
                            // an Integer is Serializable, and ObjectMessage is disabled by default
                            exchange.getMessage().setBody(123);
                        });

                from("sjms:queue:in.out.queue.InOutConsumerReplyFailureTest.pojo?transacted=true")
                        .process(exchange -> {
                            pojoCounter.incrementAndGet();
                            // a POJO that is not Serializable maps to no JMS message type
                            exchange.getMessage().setBody(new MyPojo("Bye"));
                        });
            }
        };
    }

    /**
     * A POJO that is not Serializable and has no type converter to a JMS body.
     */
    public static class MyPojo {
        private final String name;

        public MyPojo(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

}
