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
package org.apache.camel.processor.aggregator;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.processor.aggregate.ClosedCorrelationKeyException;

/**
 * @version 
 */
public class AggregateClosedCorrelationKeyTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testAggregateClosedCorrelationKey() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .aggregate(header("id"), new BodyInAggregatingStrategy())
                        .completionSize(2).closeCorrelationKeyOnCompletion(1000)
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("A+B");

        template.sendBodyAndHeader("direct:start", "A", "id", 1);
        template.sendBodyAndHeader("direct:start", "B", "id", 1);

        // should be closed
        try {
            template.sendBodyAndHeader("direct:start", "C", "id", 1);
            fail("Should throw an exception");
        } catch (CamelExecutionException e) {
            ClosedCorrelationKeyException cause = assertIsInstanceOf(ClosedCorrelationKeyException.class, e.getCause());
            assertEquals("1", cause.getCorrelationKey());
            assertTrue(cause.getMessage().startsWith("The correlation key [1] has been closed."));
        }

        assertMockEndpointsSatisfied();
    }

    public void testAggregateClosedCorrelationKeyCache() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .aggregate(header("id"), new BodyInAggregatingStrategy())
                        .completionSize(2).closeCorrelationKeyOnCompletion(2)
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("A+B", "C+D", "E+F");

        template.sendBodyAndHeader("direct:start", "A", "id", 1);
        template.sendBodyAndHeader("direct:start", "B", "id", 1);
        template.sendBodyAndHeader("direct:start", "C", "id", 2);
        template.sendBodyAndHeader("direct:start", "D", "id", 2);
        template.sendBodyAndHeader("direct:start", "E", "id", 3);
        template.sendBodyAndHeader("direct:start", "F", "id", 3);

        // 2 of them should now be closed
        int closed = 0;

        // should NOT be closed because only 2 and 3 is remembered as they are the two last used
        try {
            template.sendBodyAndHeader("direct:start", "G", "id", 1);
        } catch (CamelExecutionException e) {
            closed++;
            ClosedCorrelationKeyException cause = assertIsInstanceOf(ClosedCorrelationKeyException.class, e.getCause());
            assertEquals("1", cause.getCorrelationKey());
            assertTrue(cause.getMessage().startsWith("The correlation key [1] has been closed."));
        }

        // should be closed
        try {
            template.sendBodyAndHeader("direct:start", "H", "id", 2);
        } catch (CamelExecutionException e) {
            closed++;
            ClosedCorrelationKeyException cause = assertIsInstanceOf(ClosedCorrelationKeyException.class, e.getCause());
            assertEquals("2", cause.getCorrelationKey());
            assertTrue(cause.getMessage().startsWith("The correlation key [2] has been closed."));
        }

        // should be closed
        try {
            template.sendBodyAndHeader("direct:start", "I", "id", 3);
        } catch (CamelExecutionException e) {
            closed++;
            ClosedCorrelationKeyException cause = assertIsInstanceOf(ClosedCorrelationKeyException.class, e.getCause());
            assertEquals("3", cause.getCorrelationKey());
            assertTrue(cause.getMessage().startsWith("The correlation key [3] has been closed."));
        }

        assertMockEndpointsSatisfied();

        assertEquals("There should be 2 closed", 2, closed);
    }

}
