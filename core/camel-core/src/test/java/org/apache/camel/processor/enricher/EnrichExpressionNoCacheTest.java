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
package org.apache.camel.processor.enricher;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class EnrichExpressionNoCacheTest extends ContextTestSupport {

    @Test
    public void testEnrichExpression() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World", "Bye World", "Hello World");

        template.sendBodyAndHeader("direct:start", null, "source", "direct:foo");
        template.sendBodyAndHeader("direct:start", null, "source", "direct:bar");
        template.sendBodyAndHeader("direct:start", null, "source", "direct:foo");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .enrich().header("source").cacheSize(-1)
                    .to("mock:result");

                from("direct:foo").transform().constant("Hello World");

                from("direct:bar").transform().constant("Bye World");
            }
        };
    }
}
