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
package org.apache.camel.processor.interceptor;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ProcessorDefinition;

/**
 * @version 
 */
public class TraceFormatterTest extends ContextTestSupport {

    private List<String> tracedBodies = new ArrayList<String>();

    public void testSendingSomeMessagesBeingTraced() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Bye World");

        MockEndpoint mock = getMockEndpoint("mock:traced");
        mock.expectedMessageCount(4);

        template.sendBodyAndHeader("direct:start", "Hello London", "to", "James");

        assertMockEndpointsSatisfied();

        // assert we received the correct bodies at the given time of interception
        // and that the bodies haven't changed during the routing of the original
        // exchange that changes its body over time (Hello London -> Bye World)
        assertEquals("Hello London", tracedBodies.get(0));
        assertEquals("Hello World", tracedBodies.get(1));
        assertEquals("Goodday World", tracedBodies.get(2));
        assertEquals("Bye World", tracedBodies.get(3));
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // we create a tracer where we want to use our own formatter instead of the default one
                Tracer tracer = new Tracer();

                // use our own formatter instead of the default one
                MyTraceFormatter formatter = new MyTraceFormatter();
                tracer.setFormatter(formatter);

                // and we must remeber to add the tracer to Camel
                getContext().addInterceptStrategy(tracer);
                // END SNIPPET: e1

                // this is only for unit testing to use mock for assertion
                tracer.setDestinationUri("direct:traced");

                from("direct:start")
                        .process(new MyProcessor("Hello World"))
                        .process(new MyProcessor("Goodday World"))
                        .process(new MyProcessor("Bye World"))
                        .to("mock:result");

                from("direct:traced")
                        .process(new MyTraveAssertProcessor())
                        .to("mock:traced");
            }
        };
    }

    class MyProcessor implements Processor {

        private String msg;

        MyProcessor(String msg) {
            this.msg = msg;
        }

        public void process(Exchange exchange) throws Exception {
            exchange.getIn().setBody(msg);
        }
    }

    class MyTraveAssertProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            // take a snapshot at current time for assertion later
            // after mock assertions in unit test method
            TraceEventMessage event = exchange.getIn().getBody(DefaultTraceEventMessage.class);
            tracedBodies.add(new String(event.getBody()));
        }
    }

    // START SNIPPET: e2
    // here we have out own formatter where we can create the output we want for trace logs
    // as this is a test we just create a simple string with * around the body
    class MyTraceFormatter implements TraceFormatter {

        public Object format(TraceInterceptor interceptor, ProcessorDefinition<?> node, Exchange exchange) {
            return "***" + exchange.getIn().getBody(String.class) + "***";
        }
    }
    // END SNIPPET: e2
}