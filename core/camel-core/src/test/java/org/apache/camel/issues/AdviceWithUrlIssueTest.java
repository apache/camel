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
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.reifier.RouteReifier;
import org.junit.Test;

/**
 *
 */
public class AdviceWithUrlIssueTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:test?concurrentConsumers=1").routeId("sedaroute").to("log:before").to("mock:target");
            }
        };
    }

    @Test
    public void testProducerWithDifferentUri() throws Exception {
        RouteReifier.adviceWith(context.getRouteDefinition("sedaroute"), context, new Advice());

        getMockEndpoint("mock:target").expectedMessageCount(0);
        getMockEndpoint("mock:target2").expectedMessageCount(1);

        template.requestBody("seda:test", "TESTING");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testProducerWithSameUri() throws Exception {
        RouteReifier.adviceWith(context.getRouteDefinition("sedaroute"), context, new Advice());

        getMockEndpoint("mock:target").expectedMessageCount(0);
        getMockEndpoint("mock:target2").expectedMessageCount(1);

        template.requestBody("seda:test?concurrentConsumers=1", "TESTING");

        assertMockEndpointsSatisfied();
    }

    private class Advice extends AdviceWithRouteBuilder {
        @Override
        public void configure() throws Exception {
            interceptSendToEndpoint("mock:target").skipSendToOriginalEndpoint().to("mock:target2");
        }
    }

}
