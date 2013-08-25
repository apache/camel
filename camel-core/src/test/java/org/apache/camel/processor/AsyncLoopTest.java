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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.async.MyAsyncComponent;

/**
 * @version 
 */
public class AsyncLoopTest extends ContextTestSupport {
    
    private static final String BASE_PAYLOAD = "<Hello n='4'/>";
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
        lastEndpoint.expectedBodiesReceived(BASE_PAYLOAD + new String(new char[2]).replace("\0", " Hello Camel"));
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
        MockEndpoint lastEndpoint = resolveMandatoryEndpoint("mock:last", MockEndpoint.class);
        lastEndpoint.expectedMessageCount(1);
        lastEndpoint.expectedBodiesReceived(BASE_PAYLOAD + new String(new char[10]).replace("\0", " Hello Camel"));
        performLoopTest("direct:e", 10);
        lastEndpoint.assertIsSatisfied();
    }

    private void performLoopTest(String endpointUri, int expectedIterations, String header) throws InterruptedException {
        resultEndpoint.expectedMessageCount(expectedIterations);
        List<String> results = new ArrayList<String>(expectedIterations);
        for (int i = 0; i < expectedIterations; i++) {
            results.add(BASE_PAYLOAD + new String(new char[i + 1]).replace("\0", " Hello Camel"));
        }
        resultEndpoint.expectedBodiesReceived(results);
        
        template.sendBodyAndHeader(endpointUri, BASE_PAYLOAD, "loop", header);
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
                context.addComponent("async", new MyAsyncComponent());

                from("direct:a")
                    .loop(8)
                        .to("async:hello:camel?append=true")
                        .to("mock:result");

                from("direct:b")
                    .loop(header("loop"))
                        .to("async:hello:camel?append=true")
                        .to("mock:result");

                from("direct:c")
                    .loop().xpath("/Hello/@n")
                        .to("async:hello:camel?append=true")
                        .to("mock:result");

                from("direct:d")
                    .loop(2)
                        .to("async:hello:camel?append=true")
                        .to("mock:result")
                    .end()
                    .to("mock:last");

                from("direct:e")
                    .loop(10)
                        .to("async:hello:camel?append=true")
                        .process(loopTest)
                        .to("mock:result")
                    .end()
                    .to("mock:last");
            }
        };
    }
}
