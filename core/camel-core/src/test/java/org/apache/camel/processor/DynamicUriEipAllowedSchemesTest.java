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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The optional {@code allowedSchemes} allow-list introduced on {@code toD} (CAMEL-24298) is also honoured by the
 * sibling dynamic-uri EIPs: {@code recipientList}, {@code routingSlip}, {@code dynamicRouter}, {@code enrich} and
 * {@code pollEnrich}. A resolved recipient whose scheme is not in the list is rejected.
 */
class DynamicUriEipAllowedSchemesTest extends ContextTestSupport {

    @Test
    void recipientListAllowsMatchingScheme() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello");

        template.sendBodyAndHeader("direct:rl-ok", "Hello", "target", "mock:result");

        mock.assertIsSatisfied();
    }

    @Test
    void recipientListRejectsDisallowedScheme() {
        assertRejected("direct:rl");
    }

    @Test
    void routingSlipRejectsDisallowedScheme() {
        assertRejected("direct:rs");
    }

    @Test
    void dynamicRouterRejectsDisallowedScheme() {
        assertRejected("direct:dr");
    }

    @Test
    void enrichRejectsDisallowedScheme() {
        assertRejected("direct:en");
    }

    @Test
    void pollEnrichRejectsDisallowedScheme() {
        assertRejected("direct:pe");
    }

    private void assertRejected(String from) {
        assertThatThrownBy(() -> template.sendBodyAndHeader(from, "Hello", "target", "seda:blocked"))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(ResolveEndpointFailedException.class)
                .hasMessageContaining("not in the allowed schemes");
    }

    /**
     * Dynamic router bean: routes to the header-supplied recipient on the first hop, then stops.
     */
    public String slip(@Header("target") String target, @Header(Exchange.SLIP_ENDPOINT) String previous) {
        return previous == null ? target : null;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:rl-ok").recipientList(header("target")).allowedSchemes("mock");
                from("direct:rl").recipientList(header("target")).allowedSchemes("mock");
                from("direct:rs").routingSlip(header("target")).allowedSchemes("mock");
                from("direct:dr").dynamicRouter(method(DynamicUriEipAllowedSchemesTest.this, "slip")).allowedSchemes("mock");
                from("direct:en").enrich().simple("${header.target}").allowedSchemes("mock");
                from("direct:pe").pollEnrich().simple("${header.target}").allowedSchemes("mock").timeout(1000).end();
            }
        };
    }
}
