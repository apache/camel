/**
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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class RemoveHeadersExcludeTest extends ContextTestSupport {

    public void testRemoveHeadersWildcard() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:end");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("duck", "Donald");

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("dudeCool", "cool");
        headers.put("dudeWicket", "wicket");
        headers.put("duck", "Donald");
        headers.put("foo", "bar");

        template.sendBodyAndHeaders("direct:start", "Hello World", headers);

        assertMockEndpointsSatisfied();

        // there is also a breadcrumb header
        assertEquals(2, mock.getReceivedExchanges().get(0).getIn().getHeaders().size());
    }

    public void testRemoveHeadersRegEx() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:end");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("duck", "Donald");
        mock.expectedHeaderReceived("BeerHeineken", "Good");
        mock.expectedHeaderReceived("BeerTuborg", "Also Great");

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("dudeCool", "cool");
        headers.put("dudeWicket", "wicket");
        headers.put("duck", "Donald");
        headers.put("BeerCarlsberg", "Great");
        headers.put("BeerTuborg", "Also Great");
        headers.put("BeerHeineken", "Good");

        template.sendBodyAndHeaders("direct:start", "Hello World", headers);

        assertMockEndpointsSatisfied();

        // there is also a breadcrumb header
        assertEquals(4, mock.getReceivedExchanges().get(0).getIn().getHeaders().size());
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .removeHeaders("dude*")
                    // remove all beers, excluding Heineken or Tuborg, which we want to keep
                    .removeHeaders("Beer*", ".*Heineken.*", ".*Tuborg.*")
                    .removeHeaders("foo")
                    .to("mock:end");
            }
        };
    }
}