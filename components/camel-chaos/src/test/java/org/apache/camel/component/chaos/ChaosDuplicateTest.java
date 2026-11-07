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
package org.apache.camel.component.chaos;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code duplicate} fault (CAMEL-26607): a send that happens twice, to test that the receiver is idempotent.
 */
class ChaosDuplicateTest extends ChaosTestSupport {

    private final AtomicInteger replies = new AtomicInteger();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start").to("mock:target").to("mock:after");

                from("direct:inout").routeId("inout").to("direct:reply");
                from("direct:reply").routeId("reply")
                        .process(e -> e.getMessage().setBody("reply-" + replies.incrementAndGet()));

                from("direct:stream").routeId("stream").to("direct:read");
                from("direct:read").routeId("read").convertBodyTo(String.class).to("mock:read");

                from("direct:orders").routeId("orders").to("direct:booking");
                from("direct:booking").routeId("booking")
                        .idempotentConsumer(header("orderId"), MemoryIdempotentRepository.memoryIdempotentRepository())
                        .to("mock:booked");
            }
        };
    }

    @Test
    void inOnlySendsTheMessageTwice() throws Exception {
        String id = chaos().addRule("endpoint=mock:target duplicate");
        MockEndpoint target = getMockEndpoint("mock:target");
        target.expectedBodiesReceived("hello", "hello");
        getMockEndpoint("mock:after").expectedBodiesReceived("hello");

        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("hello"));

        assertThat(out.getException()).isNull();
        MockEndpoint.assertIsSatisfied(context);
        // one fault for the two sends
        assertThat(fired(id, "send")).isEqualTo(1);
    }

    @Test
    void inOutTheSecondReplyWins() {
        String id = chaos().addRule("endpoint=direct:reply duplicate");

        Object reply = template.requestBody("direct:inout", "hello");

        assertThat(replies.get()).isEqualTo(2);
        assertThat(reply).isEqualTo("reply-2");
        assertThat(fired(id, "send")).isEqualTo(1);
    }

    @Test
    void aFailedFirstSendIsNotRepeated() throws Exception {
        chaos().addRule("endpoint=mock:target duplicate");
        MockEndpoint target = getMockEndpoint("mock:target");
        target.whenAnyExchangeReceived(e -> {
            throw new IllegalStateException("down");
        });
        target.expectedMessageCount(1);
        getMockEndpoint("mock:after").expectedMessageCount(0);

        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("hello"));

        assertThat(out.getException()).isInstanceOf(IllegalStateException.class).hasMessage("down");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void aStreamBodyIsSentTwiceIntact() throws Exception {
        chaos().addRule("endpoint=direct:read duplicate");
        getMockEndpoint("mock:read").expectedBodiesReceived("hello stream", "hello stream");

        template.sendBody("direct:stream", new ByteArrayInputStream("hello stream".getBytes(StandardCharsets.UTF_8)));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void anIdempotentConsumerDropsTheDuplicate() throws Exception {
        String id = chaos().addRule("endpoint=direct:booking duplicate");
        getMockEndpoint("mock:booked").expectedBodiesReceived("order 1", "order 2");

        template.sendBodyAndHeader("direct:orders", "order 1", "orderId", "1");
        template.sendBodyAndHeader("direct:orders", "order 2", "orderId", "2");

        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "send")).isEqualTo(2);
        // the two orders completed once each: the duplicates are dropped by the idempotent consumer
        assertThat(routeCount("orders", "completed")).isEqualTo(2);
    }

    @Test
    void theRateDecidesWhichSendsAreDuplicated() throws Exception {
        String id = chaos().addRule("endpoint=mock:target duplicate rate=every:2");
        getMockEndpoint("mock:target").expectedMessageCount(6);

        for (int i = 0; i < 4; i++) {
            template.sendBody("direct:start", "hello " + i);
        }

        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "send")).isEqualTo(2);
        assertThat(chaos().report().toText()).contains("duplicate");
    }
}
