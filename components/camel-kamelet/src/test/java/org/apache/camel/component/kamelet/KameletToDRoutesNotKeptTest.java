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
package org.apache.camel.component.kamelet;

import org.apache.camel.Route;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Kamelet called with toD and cacheSize=-1 (the documented setting for a dynamic endpoint that is different for every
 * message) gets a new endpoint for every call, which creates a route from the template. The endpoint is discarded after
 * the call, so its route must be removed as well, otherwise every call keeps one more route.
 */
class KameletToDRoutesNotKeptTest extends CamelTestSupport {

    private static final int MESSAGES = 50;

    @Test
    void routesOfDiscardedKameletEndpointsAreRemoved() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(MESSAGES);

        for (int i = 0; i < MESSAGES; i++) {
            template.sendBodyAndHeader("direct:prototype", "body", "id", "id" + i);
        }
        mock.assertIsSatisfied();
        for (int i = 0; i < MESSAGES; i++) {
            assertEquals("id" + i + "-body", mock.getReceivedExchanges().get(i).getMessage().getBody());
        }

        assertEquals(0, kameletRoutes(), "routes created by the Kamelet and still kept after " + MESSAGES + " calls");
        assertEquals(3, context.getEndpointRegistry().size(),
                "endpoints after " + MESSAGES + " calls: " + context.getEndpointRegistry().values());
    }

    @Test
    void sameValueEveryTimeDoesNotKeepRoutes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(MESSAGES);

        for (int i = 0; i < MESSAGES; i++) {
            template.sendBodyAndHeader("direct:prototype", "body" + i, "id", "same");
        }
        mock.assertIsSatisfied();
        for (int i = 0; i < MESSAGES; i++) {
            assertEquals("same-body" + i, mock.getReceivedExchanges().get(i).getMessage().getBody());
        }

        assertEquals(0, kameletRoutes(), "routes created by the Kamelet and still kept after " + MESSAGES + " calls");
    }

    @Test
    void cachedKameletEndpointsKeepTheirRoutes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a-1", "b-2", "a-3", "b-4");

        String[] ids = { "a", "b", "a", "b" };
        for (int i = 0; i < ids.length; i++) {
            template.sendBodyAndHeader("direct:cached", String.valueOf(i + 1), "id", ids[i]);
        }
        mock.assertIsSatisfied();

        // the default toD keeps the endpoints and so their routes for the next messages
        assertEquals(2, kameletRoutes());
    }

    private long kameletRoutes() {
        return context.getRoutes().stream().filter(Route::isCreatedByKamelet).count();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("prefix")
                        .templateParameter("value")
                        .from("kamelet:source")
                        .setBody().simple("{{value}}-${body}");

                from("direct:prototype").routeId("prototype")
                        .toD("kamelet:prefix?value=${header.id}", -1)
                        .to("mock:result");

                from("direct:cached").routeId("cached")
                        .toD("kamelet:prefix?value=${header.id}")
                        .to("mock:result");
            }
        };
    }
}
