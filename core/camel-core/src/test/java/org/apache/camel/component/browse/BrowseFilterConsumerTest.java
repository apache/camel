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
package org.apache.camel.component.browse;

import java.util.List;
import java.util.function.Predicate;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.BrowsableEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The filter of a browse endpoint only chooses the exchanges kept for browsing: the route that consumes from the
 * endpoint gets every exchange.
 */
class BrowseFilterConsumerTest extends ContextTestSupport {

    @Test
    void theConsumerGetsTheExchangesTheFilterDoesNotKeep() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:consumed");
        mock.expectedBodiesReceived("one", "two", "three", "four");

        for (String body : List.of("one", "two", "three", "four")) {
            template.sendBody("direct:start", body);
        }

        assertMockEndpointsSatisfied();
        BrowsableEndpoint browse = context.getEndpoint("browse:bar?filter=#evenFilter", BrowsableEndpoint.class);
        assertEquals(List.of("two", "four"),
                browse.getExchanges().stream().map(e -> e.getMessage().getBody(String.class)).toList());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                context.getRegistry().bind("evenFilter", (Predicate<Exchange>) exchange -> {
                    String body = exchange.getMessage().getBody(String.class);
                    return "two".equals(body) || "four".equals(body);
                });

                from("direct:start").to("browse:bar?filter=#evenFilter");
                from("browse:bar?filter=#evenFilter").to("mock:consumed");
            }
        };
    }
}
