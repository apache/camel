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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class WireTapFilterIssueTest extends ContextTestSupport {

    private static final String BEFORE_FILTER = "mock:beforeFilter";
    private static final String MOCK_IN_FILTER = "mock:inFilter";
    private static final String MOCK_IN_SUBROUTE = "mock:inSubRoute";
    private static final String MOCK_AFTER_FILTER = "mock:afterFilter";
    private static final String DIRECT_START_WIRETAP_ROUTE = "direct:startWireTapRoute";
    private static final String DIRECT_START_REGULAR_TO_ROUTE = "direct:startRegularToRoute";
    private static final String DIRECT_SUBROUTE_ENDPOINT = "direct:subRoute";
    private static final String HEADER_FILTER = "filter";
    private MockEndpoint mockBeforeFilter;
    private MockEndpoint mockInFilter;
    private MockEndpoint mockInSubroute;
    private MockEndpoint mockAfterFilter;

    @AfterEach
    void afterEach() throws Exception {
        mockBeforeFilter.assertIsSatisfied();
        mockInFilter.assertIsSatisfied();
        mockInSubroute.assertIsSatisfied();
        mockAfterFilter.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        mockBeforeFilter = getMockEndpoint(BEFORE_FILTER);
        mockInFilter = getMockEndpoint(MOCK_IN_FILTER);
        mockInSubroute = getMockEndpoint(MOCK_IN_SUBROUTE);
        mockAfterFilter = getMockEndpoint(MOCK_AFTER_FILTER);

        return new RouteBuilder() {
            @Override
            public void configure() {
                //@formatter:off
                from(DIRECT_START_REGULAR_TO_ROUTE)
                        .to(BEFORE_FILTER)
                        .filter(header(HEADER_FILTER))
                            .to(MOCK_IN_FILTER)
                            .to(DIRECT_SUBROUTE_ENDPOINT)
                        .end()
                        .to(MOCK_AFTER_FILTER)
                        .end();

                from(DIRECT_START_WIRETAP_ROUTE)
                        .to(BEFORE_FILTER)
                        .filter(header(HEADER_FILTER))
                            .to(MOCK_IN_FILTER)
                            .wireTap(DIRECT_SUBROUTE_ENDPOINT)
                            .end()
                        .end()
                        .to(MOCK_AFTER_FILTER)
                        .end();

                from(DIRECT_SUBROUTE_ENDPOINT)
                        .to(MOCK_IN_SUBROUTE)
                        .end();
                //@formatter:on
            }
        };
    }

    @Test
    void regularToFilterTrue() {
        expectFilter(true);

        sendExchange(DIRECT_START_REGULAR_TO_ROUTE, true);
    }

    @Test
    void regularToFilterFalse() {
        expectFilter(false);

        sendExchange(DIRECT_START_REGULAR_TO_ROUTE, false);
    }

    @Test
    void wireTapFilterTrue() {
        expectFilter(true);

        sendExchange(DIRECT_START_WIRETAP_ROUTE, true);
    }

    @Test
    void wireTapFilterFalse() {
        expectFilter(false);

        sendExchange(DIRECT_START_WIRETAP_ROUTE, false);
    }

    private void expectFilter(boolean filter) {
        mockBeforeFilter.expectedMessageCount(1);
        mockInFilter.expectedMessageCount(filter ? 1 : 0);
        mockInSubroute.expectedMessageCount(filter ? 1 : 0);
        mockAfterFilter.expectedMessageCount(1);
    }

    private void sendExchange(String directStart, boolean filtering) {
        DefaultExchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(HEADER_FILTER, filtering);

        template.send(directStart, exchange);
    }

}
