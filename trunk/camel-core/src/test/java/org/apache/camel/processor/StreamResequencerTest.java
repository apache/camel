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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.Channel;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.EventDrivenConsumerRoute;

public class StreamResequencerTest extends ContextTestSupport {

    protected MockEndpoint resultEndpoint;

    protected void sendBodyAndHeader(String endpointUri, final Object body,
                                     final String headerName, final Object headerValue) {
        template.send(endpointUri, new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody(body);
                in.setHeader(headerName, headerValue);
                in.setHeader("testCase", getName());
            }
        });
    }

    public void testSendMessagesInWrongOrderButReceiveThemInCorrectOrder() throws Exception {
        resultEndpoint.expectedBodiesReceived("msg1", "msg2", "msg3", "msg4");
        sendBodyAndHeader("direct:start", "msg4", "seqnum", 4L);
        sendBodyAndHeader("direct:start", "msg1", "seqnum", 1L);
        sendBodyAndHeader("direct:start", "msg3", "seqnum", 3L);
        sendBodyAndHeader("direct:start", "msg2", "seqnum", 2L);
        resultEndpoint.assertIsSatisfied();
    }

    public void testMultithreaded() throws Exception {
        int numMessages = 100;

        ExecutorService service = Executors.newFixedThreadPool(2);
        service.execute(new Sender(context.createProducerTemplate(), 0, numMessages, 2));
        service.execute(new Sender(context.createProducerTemplate(), 1, numMessages + 1, 2));

        Object[] bodies = new Object[numMessages];
        for (int i = 0; i < numMessages; i++) {
            bodies[i] = "msg" + i;
        }

        resultEndpoint.expectedBodiesReceived(bodies);
        resultEndpoint.setResultWaitTime(20000);
        resultEndpoint.assertIsSatisfied();

        service.shutdownNow();
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        disableJMX();
        resultEndpoint = getMockEndpoint("mock:result");
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        enableJMX();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                from("direct:start").resequence(header("seqnum")).stream().to("mock:result");
                // END SNIPPET: example
            }
        };
    }

    public void testStreamResequencerTypeWithJmx() throws Exception {
        doTestStreamResequencerType();
    }

    public void testStreamResequencerTypeWithoutJmx() throws Exception {
        log.debug("This will now fail");
        disableJMX();
        doTestStreamResequencerType();
    }

    protected void doTestStreamResequencerType() throws Exception {
        List<Route> list = getRouteList(createRouteBuilder());
        assertEquals("Number of routes created: " + list, 1, list.size());

        Route route = list.get(0);
        EventDrivenConsumerRoute consumerRoute = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);

        Channel channel = unwrapChannel(consumerRoute.getProcessor());

        assertIsInstanceOf(DefaultErrorHandler.class, channel.getErrorHandler());
        assertIsInstanceOf(StreamResequencer.class, channel.getNextProcessor());
    }
    
    private static class Sender extends Thread {
        
        ProducerTemplate template;

        int start;
        int end;
        int increment;
        
        public Sender(ProducerTemplate template, int start, int end, int increment) {
            this.template = template;
            this.start = start;
            this.end = end;
            this.increment = increment;
        }

        @Override
        public void run() {
            for (long i = start; i < end; i += increment) {
                try {
                    Thread.sleep(4);
                } catch (InterruptedException e) {
                    // ignore
                }
                template.sendBodyAndHeader("direct:start", "msg" + i, "seqnum", i);
            }
        }
        
    }
}

