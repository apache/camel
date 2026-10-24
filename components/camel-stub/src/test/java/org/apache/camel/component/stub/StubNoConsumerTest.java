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
package org.apache.camel.component.stub;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * CAMEL-25969: without a consumer a stub endpoint keeps the latest messages (up to its size), and drops the oldest, so
 * the producer does not fail with Queue full.
 */
public class StubNoConsumerTest {

    private CamelContext context;
    private ProducerTemplate template;

    @BeforeEach
    public void setUp() {
        context = new DefaultCamelContext();
        context.addComponent("stub", new StubComponent());
        context.start();
        template = context.createProducerTemplate();
    }

    @AfterEach
    public void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    public void testKeepLatestMessages() {
        for (int i = 1; i <= 8; i++) {
            template.sendBody("stub:nobody?size=5", "Message " + i);
        }

        StubEndpoint endpoint = context.getEndpoint("stub:nobody?size=5", StubEndpoint.class);
        List<String> bodies = endpoint.getQueue().stream().map(e -> e.getMessage().getBody(String.class)).toList();
        assertEquals(List.of("Message 4", "Message 5", "Message 6", "Message 7", "Message 8"), bodies);
    }

    @Test
    public void testDefaultSizeNotFull() {
        for (int i = 1; i <= 1001; i++) {
            Exchange out = template.send("stub:jms:queue:orders", e -> e.getIn().setBody("Order"));
            assertNull(out.getException());
        }

        StubEndpoint endpoint = context.getEndpoint("stub:jms:queue:orders", StubEndpoint.class);
        assertEquals(1000, endpoint.getQueue().size());
    }

    @Test
    public void testDroppedMessageIsDone() {
        AtomicInteger done = new AtomicInteger();

        Exchange first = template.send("stub:nobody?size=1", e -> {
            e.getIn().setBody("First");
            e.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                @Override
                public void onDone(Exchange exchange) {
                    done.incrementAndGet();
                }
            });
        });
        assertNull(first.getException());
        assertEquals(0, done.get());

        Exchange second = template.send("stub:nobody?size=1", e -> e.getIn().setBody("Second"));
        assertNull(second.getException());
        // the first message is dropped to make room for the second, and its on completions are done
        assertEquals(1, done.get());

        StubEndpoint endpoint = context.getEndpoint("stub:nobody?size=1", StubEndpoint.class);
        assertEquals("Second", endpoint.getQueue().peek().getMessage().getBody(String.class));
    }
}
