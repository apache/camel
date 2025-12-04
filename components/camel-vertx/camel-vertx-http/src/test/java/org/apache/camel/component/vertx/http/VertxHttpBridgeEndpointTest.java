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

package org.apache.camel.component.vertx.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

class VertxHttpBridgeEndpointTest extends VertxHttpTestSupport {
    private static final int PORT = AvailablePortFinder.getNextAvailable();

    @Test
    void bridgeEndpointWithQueryStringDoesNotDuplicateHeaders() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");

        Map<String, String> queryParams = Map.of("q1", "1", "q2", "2", "q3", "3");
        String queryString = queryParams.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));

        template.sendBody(getProducerUri() + "/upstream?" + queryString, null);

        mockEndpoint.assertIsSatisfied();

        List<Exchange> exchanges = mockEndpoint.getExchanges();
        Exchange exchange = exchanges.get(0);

        // Verify query params were passed on when bridging and did not cause duplicate headers
        Map<String, Object> headers = exchange.getMessage().getHeaders();
        queryParams.keySet().forEach(key -> {
            Object headerValue = headers.get(key);
            assertInstanceOf(String.class, headerValue);
            assertEquals(queryParams.get(key), headerValue);
        });
    }

    @Test
    void bridgeEndpointWhenMatchOnUriPrefixSetsHttpPath() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");

        template.sendBody(getProducerUri() + "/upstream/prefix/test", null);

        mockEndpoint.assertIsSatisfied();
    }

    @Test
    void bridgeEndpointWithBody() throws Exception {
        String body = "Hello World";

        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedHeaderReceived(Exchange.HTTP_METHOD, "POST");
        mockEndpoint.expectedBodiesReceived(body);

        template.sendBody(getProducerUri() + "/upstream", body);

        mockEndpoint.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(getTestServerUri() + "/upstream")
                        .toF("vertx-http:http://localhost:%d/downstream?bridgeEndpoint=true", PORT);

                from(getTestServerUri() + "/upstream/prefix?matchOnUriPrefix=true")
                        .toF("vertx-http:http://localhost:%d/downstream?bridgeEndpoint=true", PORT);

                fromF("undertow:http://localhost:%d/downstream", PORT).to("mock:result");

                fromF("undertow:http://localhost:%d/downstream/test", PORT).to("mock:result");
            }
        };
    }
}
