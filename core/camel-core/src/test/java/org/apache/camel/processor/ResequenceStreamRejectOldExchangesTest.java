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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.resequencer.MessageRejectedException;
import org.junit.Test;

/**
 *
 */
public class ResequenceStreamRejectOldExchangesTest extends ContextTestSupport {

    @Test
    public void testInSequenceAfterCapacityReached() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("A", "B", "C", "E");
        getMockEndpoint("mock:error").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "B", "seqno", 2);
        template.sendBodyAndHeader("direct:start", "C", "seqno", 3);
        template.sendBodyAndHeader("direct:start", "A", "seqno", 1);
        template.sendBodyAndHeader("direct:start", "E", "seqno", 5);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDuplicateAfterCapacityReached() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("A", "B", "C");
        getMockEndpoint("mock:error").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "B", "seqno", 2);
        template.sendBodyAndHeader("direct:start", "C", "seqno", 3);
        template.sendBodyAndHeader("direct:start", "A", "seqno", 1);
        template.sendBodyAndHeader("direct:start", "C", "seqno", 3);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOutOfSequenceAfterCapacityReachedSimple() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("B", "C", "D");
        getMockEndpoint("mock:error").expectedBodiesReceived("A");

        template.sendBodyAndHeader("direct:start", "D", "seqno", 4);
        template.sendBodyAndHeader("direct:start", "C", "seqno", 3);
        template.sendBodyAndHeader("direct:start", "B", "seqno", 2);
        template.sendBodyAndHeader("direct:start", "A", "seqno", 1);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOutOfSequenceAfterCapacityReachedComplex() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("A", "D", "E", "F");
        getMockEndpoint("mock:error").expectedBodiesReceived("B", "C");

        template.sendBodyAndHeader("direct:start", "E", "seqno", 5);
        template.sendBodyAndHeader("direct:start", "D", "seqno", 4);
        template.sendBodyAndHeader("direct:start", "A", "seqno", 1);

        Thread.sleep(100);

        template.sendBodyAndHeader("direct:start", "B", "seqno", 2);
        template.sendBodyAndHeader("direct:start", "C", "seqno", 3);
        template.sendBodyAndHeader("direct:start", "F", "seqno", 6);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:start").onException(MessageRejectedException.class).maximumRedeliveries(0).handled(true).to("mock:error").end().resequence(header("seqno")).stream()
                    .capacity(3).rejectOld().timeout(50).deliveryAttemptInterval(10) // use
                                                                                     // low
                                                                                     // timeout
                                                                                     // to
                                                                                     // run
                                                                                     // faster
                    .to("mock:result");
            }
        };
    }
}
