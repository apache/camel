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
package org.apache.camel.impl.lw;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.CamelException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.MyAggregationStrategy;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.junit.Before;
import org.junit.Test;

public class SplitterLightweightTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        setUseLightweightContext(true);
        super.setUp();
    }

    @Test
    public void testSendingAMessageUsingMulticastReceivesItsOwnExchange() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedBodiesReceived("James", "Guillaume", "Hiram", "Rob");

        // InOnly
        template.send("direct:seqential", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody("James,Guillaume,Hiram,Rob");
                in.setHeader("foo", "bar");
            }
        });

        assertMockEndpointsSatisfied();

        Set<String> ids = new HashSet<>();
        Set<String> ids2 = new HashSet<>();

        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        for (int i = 0; i < 4; i++) {
            Exchange exchange = list.get(i);
            Message in = exchange.getIn();
            ids.add(in.getMessageId());
            ids2.add(exchange.getExchangeId());
            assertNotNull("The in message should not be null.", in);
            assertProperty(exchange, Exchange.SPLIT_INDEX, i);
            assertProperty(exchange, Exchange.SPLIT_SIZE, 4);
        }

        assertEquals("The sub messages should have unique message ids", 4, ids.size());
        assertEquals("The sub messages should have unique exchange ids", 4, ids2.size());
    }

    @Test
    public void testSplitterWithAggregationStrategy() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedBodiesReceived("James", "Guillaume", "Hiram", "Rob", "Roman");

        Exchange result = template.request("direct:seqential", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody("James,Guillaume,Hiram,Rob,Roman");
                in.setHeader("foo", "bar");
            }
        });

        assertMockEndpointsSatisfied();
        Message out = result.getMessage();
        assertEquals("Roman", out.getBody());
        assertMessageHeader(out, "foo", "bar");
        assertProperty(result, Exchange.SPLIT_INDEX, 4);
    }

    @Test
    public void testEmptyBody() {
        Exchange result = template.request("direct:seqential", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader("foo", "bar");
            }
        });

        assertFalse("Should not have out", result.hasOut());
    }

    @Test
    public void testSendingAMessageUsingMulticastReceivesItsOwnExchangeParallel() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");

        resultEndpoint.expectsNoDuplicates(body());
        resultEndpoint.expectedMessageCount(4);

        // InOnly
        template.send("direct:parallel", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody("James,Guillaume,Hiram,Rob");
                in.setHeader("foo", "bar");
            }
        });

        assertMockEndpointsSatisfied();

        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        Set<Integer> numbersFound = new TreeSet<>();
        final String[] names = {"James", "Guillaume", "Hiram", "Rob"};

        for (int i = 0; i < 4; i++) {
            Exchange exchange = list.get(i);
            Message in = exchange.getIn();
            Integer splitCounter = exchange.getProperty(Exchange.SPLIT_INDEX, Integer.class);
            numbersFound.add(splitCounter);
            assertEquals(names[splitCounter], in.getBody());
            assertProperty(exchange, Exchange.SPLIT_SIZE, 4);
        }

        assertEquals(4, numbersFound.size());
    }

    @Test
    public void testSplitterWithAggregationStrategyParallel() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(5);

        Exchange result = template.request("direct:parallel", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody("James,Guillaume,Hiram,Rob,Roman");
                in.setHeader("foo", "bar");
            }
        });

        assertMockEndpointsSatisfied();
        Message out = result.getMessage();

        assertMessageHeader(out, "foo", "bar");
        assertEquals((Integer)5, result.getProperty("aggregated", Integer.class));
    }

    @Test
    public void testSplitterWithAggregationStrategyParallelStreaming() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(5);
        resultEndpoint.expectedBodiesReceivedInAnyOrder("James", "Guillaume", "Hiram", "Rob", "Roman");

        Exchange result = template.request("direct:parallel-streaming", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody("James,Guillaume,Hiram,Rob,Roman");
                in.setHeader("foo", "bar");
            }
        });

        assertMockEndpointsSatisfied();
        Message out = result.getMessage();

        assertMessageHeader(out, "foo", "bar");
        assertEquals((Integer)5, result.getProperty("aggregated", Integer.class));
    }

    @Test
    public void testSplitterParallelAggregate() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(5);
        resultEndpoint.expectedBodiesReceivedInAnyOrder("James", "Guillaume", "Hiram", "Rob", "Roman");

        Exchange result = template.request("direct:parallelAggregate", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody("James,Guillaume,Hiram,Rob,Roman");
                in.setHeader("foo", "bar");
            }
        });

        assertMockEndpointsSatisfied();
        Message out = result.getMessage();

        assertMessageHeader(out, "foo", "bar");
        // we aggregate parallel and therefore its not thread-safe when setting
        // values
    }

    @Test
    public void testSplitterWithStreamingAndFileBody() throws Exception {
        URL url = this.getClass().getResource("/org/apache/camel/processor/simple.txt");
        assertNotNull("We should find this simple file here.", url);
        File file = new File(url.getFile());
        sendToSplitterWithStreaming(file);
    }

    @Test
    public void testSplitterWithStreamingAndStringBody() throws Exception {
        sendToSplitterWithStreaming("James,Guillaume,Hiram,Rob,Roman");
    }

    public void sendToSplitterWithStreaming(final Object body) throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(5);
        resultEndpoint.expectedHeaderReceived("foo", "bar");

        template.request("direct:streaming", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody(body);
                in.setHeader("foo", "bar");
            }
        });

        assertMockEndpointsSatisfied();

        // check properties with split details is correct
        int size = resultEndpoint.getReceivedExchanges().size();
        for (int i = 0; i < size; i++) {
            Exchange exchange = resultEndpoint.getReceivedExchanges().get(i);
            assertEquals(i, exchange.getProperty(Exchange.SPLIT_INDEX));
            if (i < (size - 1)) {
                assertEquals(Boolean.FALSE, exchange.getProperty(Exchange.SPLIT_COMPLETE));
                // this header cannot be set when streaming is used, except for
                // the last exchange
                assertNull(exchange.getProperty(Exchange.SPLIT_SIZE));
            } else {
                assertEquals(Boolean.TRUE, exchange.getProperty(Exchange.SPLIT_COMPLETE));
                // when we are complete the size is set
                assertEquals(size, exchange.getProperty(Exchange.SPLIT_SIZE));
            }
        }
    }

    @Test
    public void testSplitterWithException() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(4);
        resultEndpoint.expectedHeaderReceived("foo", "bar");

        MockEndpoint failedEndpoint = getMockEndpoint("mock:failed");
        failedEndpoint.expectedMessageCount(1);
        failedEndpoint.expectedHeaderReceived("foo", "bar");

        Exchange result = template.request("direct:exception", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody("James,Guillaume,Hiram,Rob,Exception");
                in.setHeader("foo", "bar");
            }
        });

        assertTrue("The result exchange should have a camel exception", result.getException() instanceof CamelException);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSplitterWithIterable() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(4);
        resultEndpoint.expectedBodiesReceived("A", "B", "C", "D");
        final List<String> data = Arrays.asList("A", "B", "C", "D");
        Iterable<String> itb = new Iterable<String>() {
            public Iterator<String> iterator() {
                return data.iterator();
            }
        };
        sendBody("direct:simple", itb);
        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                onException(CamelException.class).to("mock:failed");

                from("direct:seqential").split(body().tokenize(","), new UseLatestAggregationStrategy()).to("mock:result");
                from("direct:parallel").split(body().tokenize(","), new MyAggregationStrategy()).parallelProcessing().to("mock:result");
                from("direct:parallelAggregate").split(body().tokenize(","), new MyAggregationStrategy()).parallelProcessing().parallelAggregate().to("mock:result");
                from("direct:streaming").split(body().tokenize(",")).streaming().to("mock:result");
                from("direct:parallel-streaming").split(body().tokenize(","), new MyAggregationStrategy()).parallelProcessing().streaming().to("mock:result");
                from("direct:exception").split(body().tokenize(",")).aggregationStrategy(new MyAggregationStrategy()).parallelProcessing().process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String string = exchange.getIn().getBody(String.class);
                        if ("Exception".equals(string)) {
                            throw new CamelException("Just want to throw exception here");
                        }

                    }
                }).to("mock:result");
                from("direct:simple").split(body()).to("mock:result");
            }
        };
    }
}
