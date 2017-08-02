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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 *
 */
public class ResequenceStreamIgnoreInvalidExchangesTest extends ContextTestSupport {

    public void testBadFirstMessage() throws Exception {
        // bad messages is ignored
        getMockEndpoint("mock:result").expectedBodiesReceived("B", "C", "D");

        template.sendBody("direct:start", "A");
        template.sendBodyAndHeader("direct:start", "D", "seqno", 4);
        template.sendBodyAndHeader("direct:start", "C", "seqno", 3);
        template.sendBodyAndHeader("direct:start", "B", "seqno", 2);

        assertMockEndpointsSatisfied();
    }

    public void testBadSecondMessage() throws Exception {
        // bad messages is ignored
        getMockEndpoint("mock:result").expectedBodiesReceived("B", "C", "D");

        template.sendBodyAndHeader("direct:start", "D", "seqno", 4);
        template.sendBody("direct:start", "A");
        template.sendBodyAndHeader("direct:start", "C", "seqno", 3);
        template.sendBodyAndHeader("direct:start", "B", "seqno", 2);

        assertMockEndpointsSatisfied();
    }

    public void testBadThirdMessage() throws Exception {
        // bad messages is ignored
        getMockEndpoint("mock:result").expectedBodiesReceived("B", "C", "D");

        template.sendBodyAndHeader("direct:start", "D", "seqno", 4);
        template.sendBodyAndHeader("direct:start", "C", "seqno", 3);
        template.sendBody("direct:start", "A");
        template.sendBodyAndHeader("direct:start", "B", "seqno", 2);

        assertMockEndpointsSatisfied();
    }

    public void testBadForthMessage() throws Exception {
        // bad messages is ignored
        getMockEndpoint("mock:result").expectedBodiesReceived("B", "C", "D");

        template.sendBodyAndHeader("direct:start", "D", "seqno", 4);
        template.sendBodyAndHeader("direct:start", "C", "seqno", 3);
        template.sendBodyAndHeader("direct:start", "B", "seqno", 2);
        template.sendBody("direct:start", "A");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    .resequence(header("seqno")).stream().timeout(50).deliveryAttemptInterval(10)
                        // ignore invalid exchanges (they are discarded)
                        .ignoreInvalidExchanges()
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}
