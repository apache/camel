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
import java.util.Date;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class TraceInterceptorDestinationTest extends ContextTestSupport {

    private List<String> tracedBodies = new ArrayList<String>();
    private List<String> tracedHeaders = new ArrayList<String>();

    public void testSendingSomeMessagesBeingTraced() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Bye World", "Foo World", "Foo World");

        MockEndpoint mock = getMockEndpoint("mock:traced");
        mock.expectedMessageCount(8);
        // should be in our CSV format (defined in bottom of this class)
        mock.message(0).body().regex("^direct://start;.*;.*;Hello London");
        mock.message(1).body().regex("^direct://start;.*;.*;Hello World");
        mock.message(2).body().regex("^direct://start;.*;.*;Goodday World");
        mock.message(3).body().regex("^direct://start;.*;.*;Bye World");
        mock.message(4).body().regex("^direct://foo;.*;.*;Hello Copenhagen");
        mock.message(5).body().regex("^direct://foo;.*;.*;Foo World");
        mock.message(6).body().regex("^direct://foo;.*;.*;Hello Beijing");
        mock.message(7).body().regex("^direct://foo;.*;.*;Foo World");

        template.sendBodyAndHeader("direct:start", "Hello London", "to", "James");
        template.sendBody("direct:foo", "Hello Copenhagen");
        // to test sending to same endpoint twice
        template.sendBody("direct:foo", "Hello Beijing");

        assertMockEndpointsSatisfied();

        // assert we received the correct bodies at the given time of interception
        // and that the bodies haven't changed during the routing of the original
        // exchange that changes its body over time (Hello London -> Bye World)
        assertEquals("Hello London", tracedBodies.get(0));
        assertEquals("Hello World", tracedBodies.get(1));
        assertEquals("Goodday World", tracedBodies.get(2));
        assertEquals("Bye World", tracedBodies.get(3));
        assertEquals("Hello Copenhagen", tracedBodies.get(4));
        assertEquals("Foo World", tracedBodies.get(5));
        assertEquals("Hello Beijing", tracedBodies.get(6));
        assertEquals("Foo World", tracedBodies.get(7));

        // assert headers as well
        assertTrue(tracedHeaders.get(0), tracedHeaders.get(0).contains("to=James"));
        assertTrue(tracedHeaders.get(1), tracedHeaders.get(1).contains("to=Hello"));
        assertTrue(tracedHeaders.get(2), tracedHeaders.get(2).contains("to=Goodday"));
        assertTrue(tracedHeaders.get(3), tracedHeaders.get(3).contains("to=Bye"));
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // we create a tracer where we want to send TraveEvents to an endpoint
                // "direct:traced" where we can do some custom processing such as storing
                // it in a file or a database
                Tracer tracer = new Tracer();
                tracer.setDestinationUri("direct:traced");
                // we disable regular trace logging in the log file. You can omit this and
                // have both.
                tracer.setLogLevel(LoggingLevel.OFF);
                // and we must remember to add the tracer to Camel
                getContext().addInterceptStrategy(tracer);
                // END SNIPPET: e1

                from("direct:start")
                        .process(new MyProcessor("Hello World"))
                        .process(new MyProcessor("Goodday World"))
                        .process(new MyProcessor("Bye World"))
                        .to("mock:result");

                from("direct:foo")
                        .process(new MyProcessor("Foo World"))
                        .to("mock:result");

                from("direct:traced")
                        .process(new MyTraveAssertProcessor())
                        .process(new MyTraceMessageProcessor())
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
            exchange.getIn().setHeader("to", msg.split(" ")[0]);
        }
    }

    class MyTraveAssertProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            String nodeId = exchange.getProperty(Exchange.TRACE_EVENT_NODE_ID, String.class);
            Date timestamp = exchange.getProperty(Exchange.TRACE_EVENT_TIMESTAMP, Date.class);
            assertNotNull(nodeId);
            assertNotNull(timestamp);

            // take a snapshot at current time for assertion later
            // after mock assertions in unit test method
            TraceEventMessage msg = exchange.getIn().getBody(DefaultTraceEventMessage.class);
            tracedBodies.add(msg.getBody());
            if (msg.getHeaders() != null) {
                tracedHeaders.add(msg.getHeaders());
            }
        }
    }

    // START SNIPPET: e2
    class MyTraceMessageProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            // here we can transform the message how we like want it
            TraceEventMessage msg = exchange.getIn().getBody(DefaultTraceEventMessage.class);

            // we want to store it as a CSV with from;to;exchangeId;body
            String s = msg.getFromEndpointUri() + ";" + msg.getToNode() + ";" + msg.getExchangeId() + ";" + msg.getBody();

            // so we replace the IN body with our CSV string
            exchange.getIn().setBody(s);
        }
    }
    // END SNIPPET: e2
}