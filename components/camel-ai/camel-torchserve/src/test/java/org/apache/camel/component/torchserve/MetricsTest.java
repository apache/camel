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
package org.apache.camel.component.torchserve;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;

class MetricsTest extends TorchServeTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        var context = super.createCamelContext();
        var component = context.getComponent("torchserve", TorchServeComponent.class);
        var configuration = component.getConfiguration();
        configuration.setMetricsAddress(mockServer.baseUrl());
        return context;
    }

    @Test
    void testMetrics() throws Exception {
        mockServer.stubFor(get("/metrics")
                .willReturn(okForContentType("text/plain", "# HELP test\n# TYPE test counter\n")));
        var mock = getMockEndpoint("mock:result");
        mock.expectedBodyReceived().constant("# HELP test\n# TYPE test counter\n");

        template.sendBody("direct:metrics", null);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:metrics")
                        .to("torchserve:metrics/metrics")
                        .to("mock:result");
            }
        };
    }
}
