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
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class LoopTest extends ContextTestSupport {
    MockEndpoint resultEndpoint;

    public void testCounterLoop() throws Exception {
        performLoopTest("direct:a", 8);
    }

    public void testExpressionLoop() throws Exception {
        performLoopTest("direct:b", 6);
    }

    public void testExpressionClauseLoop() throws Exception {
        performLoopTest("direct:c", 4);
    }

    public void testLoopAsBlock() throws Exception {
        MockEndpoint lastEndpoint = resolveMandatoryEndpoint("mock:last", MockEndpoint.class);
        lastEndpoint.expectedMessageCount(1);
        performLoopTest("direct:d", 2);
        lastEndpoint.assertIsSatisfied();
    }

    public void testLoopWithInvalidExpression() throws Exception {
        try {
            performLoopTest("direct:b", 4, "invalid");
            fail("Exception expected for invalid expression");
        } catch (RuntimeCamelException e) {
            // expected
        }
    }

    public void testLoopProperties() throws Exception {
        performLoopTest("direct:e", 10);
    }

    private void performLoopTest(String endpointUri, int expectedIterations, String header) throws InterruptedException {
        resultEndpoint.expectedMessageCount(expectedIterations);
        template.sendBodyAndHeader(endpointUri, "<hello times='4'>world!</hello>", "loop", header);
        resultEndpoint.assertIsSatisfied();
    }

    private void performLoopTest(String endpointUri, int expectedIterations) throws InterruptedException {
        performLoopTest(endpointUri, expectedIterations, "6");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.reset();
    }

    protected RouteBuilder createRouteBuilder() {
        final Processor loopTest = new LoopTestProcessor(10);

        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: ex1
                from("direct:a").loop(8).to("mock:result");
                // END SNIPPET: ex1
                // START SNIPPET: ex2
                from("direct:b").loop(header("loop")).to("mock:result");
                // END SNIPPET: ex2
                // START SNIPPET: ex3
                from("direct:c").loop().xpath("/hello/@times").to("mock:result");
                // END SNIPPET: ex3
                // START SNIPPET: ex4
                from("direct:d").loop(2).to("mock:result").end().to("mock:last");
                // END SNIPPET: ex4
                // START SNIPPET: ex5
                from("direct:e").loop(10).process(loopTest).to("mock:result");
                // END SNIPPET: ex5
            }
        };
    }
}
