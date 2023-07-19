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
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 *
 */
public class ResequenceStreamNotIgnoreInvalidExchangesTest extends ContextTestSupport {

    @Test
    public void testBadFirstMessage() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("B", "C", "D");

        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:start", "A"),
                "Should have thrown an exception");

        template.sendBodyAndHeader("direct:start", "D", "seqno", 4);
        template.sendBodyAndHeader("direct:start", "C", "seqno", 3);
        template.sendBodyAndHeader("direct:start", "B", "seqno", 2);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testBadSecondMessage() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("B", "C", "D");

        template.sendBodyAndHeader("direct:start", "D", "seqno", 4);

        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:start", "A"),
                "Should have thrown an exception");
        template.sendBodyAndHeader("direct:start", "C", "seqno", 3);
        template.sendBodyAndHeader("direct:start", "B", "seqno", 2);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testBadThirdMessage() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("B", "C", "D");

        template.sendBodyAndHeader("direct:start", "D", "seqno", 4);
        template.sendBodyAndHeader("direct:start", "C", "seqno", 3);

        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:start", "A"),
                "Should have thrown an exception");

        template.sendBodyAndHeader("direct:start", "B", "seqno", 2);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testBadForthMessage() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("B", "C", "D");

        template.sendBodyAndHeader("direct:start", "D", "seqno", 4);
        template.sendBodyAndHeader("direct:start", "C", "seqno", 3);
        template.sendBodyAndHeader("direct:start", "B", "seqno", 2);

        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:start", "A"),
                "Should have thrown an exception");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").resequence(header("seqno")).stream().timeout(150).deliveryAttemptInterval(10)
                        .to("mock:result");
            }
        };
    }
}
