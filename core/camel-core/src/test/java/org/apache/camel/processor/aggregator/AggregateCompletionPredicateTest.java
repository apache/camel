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
package org.apache.camel.processor.aggregator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.junit.Test;

public class AggregateCompletionPredicateTest extends ContextTestSupport {

    @Test
    public void testCompletionPredicateBeforeTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedBodiesReceived("A+B+C+END");
        // should be faster than 10 seconds
        mock.setResultWaitTime(10000);

        template.sendBodyAndHeader("direct:start", "A", "id", "foo");
        template.sendBodyAndHeader("direct:start", "B", "id", "foo");
        template.sendBodyAndHeader("direct:start", "C", "id", "foo");
        template.sendBodyAndHeader("direct:start", "END", "id", "foo");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMultipleCompletionPredicateBeforeTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedBodiesReceived("A+B+C+END", "D+E+END", "F+G+H+I+END");

        template.sendBodyAndHeader("direct:start", "A", "id", "foo");
        template.sendBodyAndHeader("direct:start", "B", "id", "foo");
        template.sendBodyAndHeader("direct:start", "C", "id", "foo");
        template.sendBodyAndHeader("direct:start", "END", "id", "foo");

        template.sendBodyAndHeader("direct:start", "D", "id", "foo");
        template.sendBodyAndHeader("direct:start", "E", "id", "foo");
        template.sendBodyAndHeader("direct:start", "END", "id", "foo");

        template.sendBodyAndHeader("direct:start", "F", "id", "foo");
        template.sendBodyAndHeader("direct:start", "G", "id", "foo");
        template.sendBodyAndHeader("direct:start", "H", "id", "foo");
        template.sendBodyAndHeader("direct:start", "I", "id", "foo");
        template.sendBodyAndHeader("direct:start", "END", "id", "foo");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCompletionPredicateBeforeTimeoutTwoGroups() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedBodiesReceived("A+B+C+END", "1+2+3+4+END");
        // should be faster than 10 seconds
        mock.setResultWaitTime(10000);

        template.sendBodyAndHeader("direct:start", "A", "id", "foo");
        template.sendBodyAndHeader("direct:start", "1", "id", "bar");
        template.sendBodyAndHeader("direct:start", "2", "id", "bar");
        template.sendBodyAndHeader("direct:start", "B", "id", "foo");
        template.sendBodyAndHeader("direct:start", "C", "id", "foo");
        template.sendBodyAndHeader("direct:start", "3", "id", "bar");
        template.sendBodyAndHeader("direct:start", "END", "id", "foo");
        template.sendBodyAndHeader("direct:start", "4", "id", "bar");
        template.sendBodyAndHeader("direct:start", "END", "id", "bar");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMultipleCompletionPredicateBeforeTimeoutTwoGroups() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedBodiesReceived("A+B+C+END", "1+2+3+4+END", "5+6+END", "D+E+END", "7+8+END", "F+G+H+I+END");

        template.sendBodyAndHeader("direct:start", "A", "id", "foo");
        template.sendBodyAndHeader("direct:start", "B", "id", "foo");
        template.sendBodyAndHeader("direct:start", "C", "id", "foo");
        template.sendBodyAndHeader("direct:start", "1", "id", "bar");
        template.sendBodyAndHeader("direct:start", "2", "id", "bar");
        template.sendBodyAndHeader("direct:start", "END", "id", "foo");

        template.sendBodyAndHeader("direct:start", "D", "id", "foo");
        template.sendBodyAndHeader("direct:start", "3", "id", "bar");
        template.sendBodyAndHeader("direct:start", "4", "id", "bar");
        template.sendBodyAndHeader("direct:start", "END", "id", "bar");

        template.sendBodyAndHeader("direct:start", "5", "id", "bar");
        template.sendBodyAndHeader("direct:start", "6", "id", "bar");
        template.sendBodyAndHeader("direct:start", "E", "id", "foo");
        template.sendBodyAndHeader("direct:start", "END", "id", "bar");

        template.sendBodyAndHeader("direct:start", "END", "id", "foo");

        template.sendBodyAndHeader("direct:start", "F", "id", "foo");
        template.sendBodyAndHeader("direct:start", "7", "id", "bar");
        template.sendBodyAndHeader("direct:start", "G", "id", "foo");
        template.sendBodyAndHeader("direct:start", "H", "id", "foo");
        template.sendBodyAndHeader("direct:start", "8", "id", "bar");
        template.sendBodyAndHeader("direct:start", "END", "id", "bar");

        template.sendBodyAndHeader("direct:start", "I", "id", "foo");
        template.sendBodyAndHeader("direct:start", "END", "id", "foo");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").aggregate(header("id"), new BodyInAggregatingStrategy()).completionPredicate(body().contains("END")).completionTimeout(20000)
                    .to("mock:aggregated");
            }
        };
    }
}
