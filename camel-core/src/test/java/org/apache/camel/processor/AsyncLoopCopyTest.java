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
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.async.MyAsyncComponent;

/**
 * @version 
 */
public class AsyncLoopCopyTest extends ContextTestSupport {

    private static String beforeThreadName;
    private static String afterThreadName;
    
    public void testAsyncLoopCopy() throws Exception {
        getMockEndpoint("mock:before").expectedBodiesReceived("Hello Camel");
        getMockEndpoint("mock:loopIterationStart").expectedBodiesReceived("Hello Camel", "Hello Camel");
        getMockEndpoint("mock:loopIterationEnd").expectedBodiesReceived("Bye Camel", "Bye Camel");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Camel");

        String reply = template.requestBodyAndHeader("direct:start", "Hello Camel", "NumberIterations", 2, String.class);
        assertEquals("Hello Camel", reply);

        assertMockEndpointsSatisfied();

        assertFalse("Should use different threads", beforeThreadName.equalsIgnoreCase(afterThreadName));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("async", new MyAsyncComponent());

                from("direct:start")
                    .to("mock:before")                  // Should receive Hello Camel
                    .to("log:before")               
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            beforeThreadName = Thread.currentThread().getName();
                        }
                    })
                    .loop(header("NumberIterations")).copy()
                        .to("mock:loopIterationStart")  // Should receive 2x Hello Camel
                        .to("async:bye:camel")          // Will transform the body to Bye Camel
                        .to("mock:loopIterationEnd")    // Should receive 2x Bye Camel
                    .end()
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            afterThreadName = Thread.currentThread().getName();
                        }
                    })
                    .to("log:after")               
                    .to("mock:result");                 // Should receive 1x Hello Camel (original message)
            }
        };
    }
}
