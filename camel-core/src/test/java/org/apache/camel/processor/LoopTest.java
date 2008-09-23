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
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision:  $
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

    private void performLoopTest(String endpointUri, int expectedIterations) throws InterruptedException {
        resultEndpoint.expectedMessageCount(expectedIterations);
        template.sendBodyAndHeader(endpointUri, "<hello times='4'>world!</hello>", "loop", "6");
        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.reset();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: ex
                from("direct:a").loop(8).to("mock:result");
                // END SNIPPET: ex
                // START SNIPPET: ex2
                from("direct:b").loop(header("loop")).to("mock:result");
                // END SNIPPET: ex2
                // START SNIPPET: ex3
                from("direct:c").loop().xpath("/hello/@times").to("mock:result");
                // END SNIPPET: ex3
            }
        };
    }
}
