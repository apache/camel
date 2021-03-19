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
package org.apache.camel.processor.intercept;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

/**
 * Based on an issue on the camel user forum.
 */
public class InterceptSendToRecipientListTest extends ContextTestSupport {

    @Test
    public void testInterceptSendToRecipientList() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);
        getMockEndpoint("mock:intercepted").expectedBodiesReceived("Hello Bar");

        template.sendBodyAndHeader("direct:start", "Hello Bar", "whereTo", "seda:beer");
        template.sendBodyAndHeader("direct:start", "Hello Home", "whereTo", "seda:home");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("seda:b*").skipSendToOriginalEndpoint().to("mock:intercepted");

                from("direct:start")
                        .recipientList().header("whereTo").to("mock:result");

            }
        };
    }
}
