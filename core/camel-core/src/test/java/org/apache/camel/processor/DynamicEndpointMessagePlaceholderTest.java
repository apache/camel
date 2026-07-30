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

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.seda.SedaEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dynamic-uri EIPs {@code toD} and {@code enrich} resolve property placeholders ({@code {{...}}}) once, at build
 * time, on the endpoint uri written in the route (via ToDynamicReifier/EnrichReifier). A {@code {{...}}} token that
 * only appears in the per-message evaluated recipient (e.g. from a header) is therefore treated as a literal endpoint
 * uri and not re-expanded. See CAMEL-24282.
 * <p/>
 * {@code recipientList} / {@code routingSlip} / {@code dynamicRouter} compute their recipients entirely from a runtime
 * expression (no static template resolved at build time), so they continue to resolve {@code {{...}}} in those
 * recipients - this is relied upon e.g. by routes carrying a {@code {{port}}}-style placeholder in the recipient
 * header, and must be preserved. {@code pollEnrich} does resolve its static uri at build time, but its per-message
 * recipient goes through the same shared resolution path, so it too still resolves {@code {{...}}} (left unchanged in
 * this PR).
 */
class DynamicEndpointMessagePlaceholderTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        Properties prop = new Properties();
        prop.setProperty("secretTarget", "resolved");
        context.getPropertiesComponent().setInitialProperties(prop);
        return context;
    }

    @Test
    void toDPlaceholderInHeaderNotResolved() throws Exception {
        getMockEndpoint("mock:resolved").expectedMessageCount(0);
        getMockEndpoint("mock:done").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:tod", "Hello", "target", "mock:{{secretTarget}}");

        assertMockEndpointsSatisfied();
    }

    @Test
    void enrichPlaceholderInHeaderNotResolved() throws Exception {
        getMockEndpoint("mock:resolved").expectedMessageCount(0);
        getMockEndpoint("mock:done").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:en", "Hello", "target", "mock:{{secretTarget}}");

        assertMockEndpointsSatisfied();
    }

    @Test
    void recipientListPlaceholderInHeaderStillResolved() throws Exception {
        // recipientList has no build-time template, so {{...}} in the recipient header is still resolved
        getMockEndpoint("mock:resolved").expectedMessageCount(1);
        getMockEndpoint("mock:done").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:rl", "Hello", "target", "mock:{{secretTarget}}");

        assertMockEndpointsSatisfied();
    }

    @Test
    void routingSlipPlaceholderInHeaderStillResolved() throws Exception {
        getMockEndpoint("mock:resolved").expectedMessageCount(1);
        getMockEndpoint("mock:done").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:rs", "Hello", "target", "mock:{{secretTarget}}");

        assertMockEndpointsSatisfied();
    }

    @Test
    void dynamicRouterPlaceholderInHeaderStillResolved() throws Exception {
        // dynamicRouter extends routingSlip, sharing the same runtime recipient path
        getMockEndpoint("mock:resolved").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:dr", "Hello", "target", "mock:{{secretTarget}}");

        assertMockEndpointsSatisfied();
    }

    @Test
    void pollEnrichPlaceholderInHeaderStillResolved() throws Exception {
        // pollEnrich's per-message recipient goes through the shared resolution path, so {{...}} is still resolved
        template.sendBody("seda:resolved", "SEED");
        getMockEndpoint("mock:done").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:pe", "trigger", "target", "seda:{{secretTarget}}");

        assertMockEndpointsSatisfied();
        // resolved -> pollEnrich drained the seed from seda:resolved (not from the literal seda:{{secretTarget}})
        SedaEndpoint seda = context.getEndpoint("seda:resolved", SedaEndpoint.class);
        assertThat(seda.getQueue()).isEmpty();
    }

    @Test
    void placeholderInStaticTemplateIsResolved() throws Exception {
        // control: a placeholder written in the route template itself is still resolved at build time
        getMockEndpoint("mock:resolved").expectedMessageCount(1);

        template.sendBody("direct:staticTemplate", "Hello");

        assertMockEndpointsSatisfied();
    }

    /**
     * Dynamic router bean: routes to the header-supplied recipient on the first hop, then stops.
     */
    public String route(@Header("target") String target, @Header(Exchange.SLIP_ENDPOINT) String previous) {
        return previous == null ? target : null;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:tod").toD("${header.target}").to("mock:done");
                from("direct:en").enrich().simple("${header.target}")
                        .aggregationStrategy(AggregationStrategies.useOriginal()).to("mock:done");
                from("direct:rl").recipientList(header("target")).to("mock:done");
                from("direct:rs").routingSlip(header("target")).to("mock:done");
                from("direct:dr").dynamicRouter(method(DynamicEndpointMessagePlaceholderTest.this, "route"));
                from("direct:pe").pollEnrich().simple("${header.target}").timeout(2000).end().to("mock:done");
                from("direct:staticTemplate").toD("mock:{{secretTarget}}");
            }
        };
    }
}
