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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The corrupt fault on the messages that enter a route ({@code route=}).
 */
class ChaosEntryFaultsTest extends ChaosTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:orders").routeId("orders").to("mock:orders").to("direct:billing");
                from("direct:billing").routeId("billing").to("mock:billing");
            }
        };
    }

    @Test
    void corruptHeadersAtTheEntryOfTheRoute() throws Exception {
        String id = chaos().addRule("route=orders corrupt mode=headers");
        MockEndpoint orders = getMockEndpoint("mock:orders");
        orders.expectedMessageCount(1);

        template.sendBodyAndHeaders("direct:orders", "hello", Map.of("country", "ES"));

        MockEndpoint.assertIsSatisfied(context);
        assertThat(orders.getReceivedExchanges().get(0).getMessage().getHeader("country", String.class))
                .isNotEqualTo("ES").hasSize(16);
        assertThat(fired(id, "entry")).isEqualTo(1);
    }

    @Test
    void corruptCharsetAtTheEntryOfAFileRoute(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("order.txt"), "an order");
        String id = chaos().addRule("route=files corrupt mode=charset");
        MockEndpoint files = getMockEndpoint("mock:files");
        files.expectedMessageCount(1);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("file:" + dir + "?noop=true&initialDelay=0&delay=10").routeId("files").to("mock:files");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        assertThat(files.getReceivedExchanges().get(0).getMessage().getBody())
                .isEqualTo("an order".getBytes(StandardCharsets.UTF_16));
        assertThat(fired(id, "entry")).isEqualTo(1);
    }

    @Test
    void theRouteIdPatternSelectsTheRoute() throws Exception {
        // the exchange enters orders and then billing: only billing matches
        String id = chaos().addRule("route=bill* corrupt mode=charset");
        MockEndpoint orders = getMockEndpoint("mock:orders");
        orders.expectedBodiesReceived("hello");
        MockEndpoint billing = getMockEndpoint("mock:billing");
        billing.expectedMessageCount(1);

        template.sendBody("direct:orders", "hello");

        MockEndpoint.assertIsSatisfied(context);
        assertThat(billing.getReceivedExchanges().get(0).getMessage().getBody())
                .isEqualTo("hello".getBytes(StandardCharsets.UTF_16));
        assertThat(fired(id, "entry")).isEqualTo(1);
    }

    @Test
    void truncateAtTheEntry() throws Exception {
        String id = chaos().addRule("route=orders corrupt mode=truncate");
        MockEndpoint orders = getMockEndpoint("mock:orders");
        orders.expectedMessageCount(1);

        template.sendBody("direct:orders", new ByteArrayInputStream("abcd".getBytes(StandardCharsets.UTF_8)));

        MockEndpoint.assertIsSatisfied(context);
        Exchange received = orders.getReceivedExchanges().get(0);
        assertThat(received.getMessage().getBody(InputStream.class).readAllBytes())
                .isEqualTo("ab".getBytes(StandardCharsets.UTF_8));
        assertThat(fired(id, "entry")).isEqualTo(1);
    }

    @Test
    void entryRuleDoesNotFireAtSendsOfTheRoute() throws Exception {
        // a route= rule fires only at the entry, so the sends of the route are not touched
        String id = chaos().addRule("route=orders corrupt mode=headers rate=every:1000000");
        getMockEndpoint("mock:orders").expectedBodiesReceived("hello");
        getMockEndpoint("mock:billing").expectedBodiesReceived("hello");

        template.sendBody("direct:orders", "hello");

        MockEndpoint.assertIsSatisfied(context);
        assertThat(ruleReport(id).getLong("matches")).isEqualTo(1);
    }

    @Test
    void routesAddedLaterGetTheEntryHookToo() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:late").routeId("late").to("mock:late");
            }
        });
        String id = chaos().addRule("route=late corrupt mode=charset");
        MockEndpoint late = getMockEndpoint("mock:late");
        late.expectedMessageCount(1);

        template.sendBody("direct:late", "hello");

        MockEndpoint.assertIsSatisfied(context);
        assertThat(late.getReceivedExchanges().get(0).getMessage().getBody(byte[].class))
                .isEqualTo("hello".getBytes(StandardCharsets.UTF_16));
        assertThat(fired(id, "entry")).isEqualTo(1);
    }
}
