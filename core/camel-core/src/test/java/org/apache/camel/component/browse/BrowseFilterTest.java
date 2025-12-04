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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BrowsableEndpoint;
import org.junit.jupiter.api.Test;

public class BrowseFilterTest extends ContextTestSupport {

    protected final Object body1 = "one";
    protected final Object body2 = "two";
    protected final Object body3 = "three";
    protected final Object body4 = "four";
    protected final Object body5 = "five";

    @Test
    public void testFilter() throws Exception {
        template.sendBody("browse:foo", body1);
        template.sendBody("browse:foo", body2);
        template.sendBody("browse:foo", body3);
        template.sendBody("browse:foo", body4);
        template.sendBody("browse:foo", body5);

        Collection<Endpoint> list = context.getEndpoints();
        assertEquals(2, list.size(), "number of endpoints");

        BrowsableEndpoint be1 = context.getEndpoint("browse:foo", BrowsableEndpoint.class);
        assertEquals(5, be1.getExchanges().size());

        BrowsableEndpoint be2 = context.getEndpoint("browse:bar?filter=#evenFilter", BrowsableEndpoint.class);
        assertEquals(2, be2.getExchanges().size());
        assertEquals("two", be2.getExchanges().get(0).getMessage().getBody());
        assertEquals("four", be2.getExchanges().get(1).getMessage().getBody());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                context.getRegistry().bind("evenFilter", new EvenPredicate());

                from("browse:foo").to("browse:bar?filter=#evenFilter");
            }
        };
    }

    private static class EvenPredicate implements java.util.function.Predicate<Exchange> {

        @Override
        public boolean test(Exchange exchange) {
            String b = exchange.getMessage().getBody(String.class);
            return "two".equals(b) || "four".equals(b);
        }
    }
}
