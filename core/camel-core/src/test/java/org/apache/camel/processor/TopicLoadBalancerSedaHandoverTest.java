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
package org.apache.camel.processor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Synchronization;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The copies of the topic load balancer use the unit of work of the exchange: a copy that sends InOnly to seda must not
 * hand the on completions of the exchange (such as the consumer's commit) to its seda copy, as for a part with
 * shareUnitOfWork (CAMEL-26576). They run when the exchange is done, with its outcome.
 */
class TopicLoadBalancerSedaHandoverTest extends ContextTestSupport {

    private final List<String> events = new CopyOnWriteArrayList<>();

    @Test
    void theCommitRunsWithTheExchangeWhenALaterEndpointFails() throws Exception {
        NotifyBuilder seda = new NotifyBuilder(context).fromRoute("seda").whenDone(1).create();

        Exchange out = template.send("direct:topic", e -> {
            e.getExchangeExtension().addOnCompletion(new Recorder("commit"));
            e.getIn().setBody("bad");
        });

        assertTrue(out.isFailed());
        assertTrue(seda.matches(20, TimeUnit.SECONDS));
        assertEquals(List.of("commit:failure:" + out.getExchangeId()), events);
    }

    @Test
    void theCommitRunsWithTheExchangeWhenAllEndpointsSucceed() throws Exception {
        NotifyBuilder seda = new NotifyBuilder(context).fromRoute("seda").whenDone(1).create();

        Exchange out = template.send("direct:topic", e -> {
            e.getExchangeExtension().addOnCompletion(new Recorder("commit"));
            e.getIn().setBody("a");
        });

        assertFalse(out.isFailed());
        assertTrue(seda.matches(20, TimeUnit.SECONDS));
        assertEquals(List.of("commit:complete:" + out.getExchangeId()), events);
    }

    @Test
    void aCopyStillHandsOverTheOnCompletionsItAdded() throws Exception {
        NotifyBuilder seda = new NotifyBuilder(context).fromRoute("seda").whenDone(1).create();

        Exchange out = template.send("direct:own", e -> e.getIn().setBody("a"));

        assertFalse(out.isFailed());
        assertTrue(seda.matches(20, TimeUnit.SECONDS));
        // the on completion of the copy ran with its seda copy, not with the exchange
        assertEquals(1, events.size(), events::toString);
        assertTrue(events.get(0).startsWith("own:complete:"), events::toString);
        assertFalse(events.get(0).endsWith(out.getExchangeId()), events::toString);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:topic")
                        .loadBalance().topic()
                            .to("seda:line")
                            .to("direct:line")
                        .end();

                from("direct:own")
                        .loadBalance().topic()
                            .pipeline()
                                .process(e -> e.getExchangeExtension().addOnCompletion(new Recorder("own")))
                                .to("seda:line")
                            .end()
                        .end();

                from("direct:line")
                        .filter(body().isEqualTo("bad"))
                            .throwException(new IllegalArgumentException("bad line"))
                        .end()
                        .to("mock:line");

                from("seda:line").routeId("seda").to("mock:seda");
            }
        };
    }

    private final class Recorder implements Synchronization {
        private final String name;

        private Recorder(String name) {
            this.name = name;
        }

        @Override
        public void onComplete(Exchange exchange) {
            events.add(name + ":complete:" + exchange.getExchangeId());
        }

        @Override
        public void onFailure(Exchange exchange) {
            events.add(name + ":failure:" + exchange.getExchangeId());
        }
    }
}
