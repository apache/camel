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
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;

/**
 * @version $Revision$
 */
public class SplitterTest extends ContextTestSupport {

    public void testSendingAMessageUsingMulticastReceivesItsOwnExchange() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedBodiesReceived("James", "Guillaume", "Hiram", "Rob");

        template.send("direct:seqential", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody("James,Guillaume,Hiram,Rob");
                in.setHeader("foo", "bar");
            }
        });

        assertMockEndpointsSatisifed();

        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        for (int i = 0; i < 4; i++) {
            Exchange exchange = list.get(i);
            Message in = exchange.getIn();
            assertMessageHeader(in, Splitter.SPLIT_COUNTER, i);
            assertMessageHeader(in, Splitter.SPLIT_SIZE, 4);
        }
    }

    public void testSpliterWithAggregationStrategy() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedBodiesReceived("James", "Guillaume", "Hiram", "Rob", "Roman");

        Exchange result = template.send("direct:seqential", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody("James,Guillaume,Hiram,Rob,Roman");
                in.setHeader("foo", "bar");
            }
        });

        assertMockEndpointsSatisifed();
        Message out = result.getOut();
        assertEquals("Roman", out.getBody());
        assertMessageHeader(out, "foo", "bar");
        assertMessageHeader(out, Splitter.SPLIT_COUNTER, 4);
    }

    public void testEmptyBody() {
        Exchange result = template.send("direct:seqential", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader("foo", "bar");
            }
        });

        assertNull(result.getOut(false));
    }

    public void testSendingAMessageUsingMulticastReceivesItsOwnExchangeParallel() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");

        resultEndpoint.expectsNoDuplicates(body());
        resultEndpoint.expectedMessageCount(4);

        template.send("direct:parallel", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody("James,Guillaume,Hiram,Rob");
                in.setHeader("foo", "bar");
            }
        });

        assertMockEndpointsSatisifed();

        List<Exchange> list = resultEndpoint.getReceivedExchanges();

        Set<Integer> numbersFound = new TreeSet<Integer>();

        final String[] names = {"James", "Guillaume", "Hiram", "Rob"};

        for (int i = 0; i < 4; i++) {
            Exchange exchange = list.get(i);
            Message in = exchange.getIn();
            Integer splitCounter = in.getHeader(Splitter.SPLIT_COUNTER, Integer.class);
            numbersFound.add(splitCounter);
            assertEquals(names[splitCounter], in.getBody());
            assertMessageHeader(in, Splitter.SPLIT_SIZE, 4);
        }

        assertEquals(4, numbersFound.size());
    }

    public void testSpliterWithAggregationStrategyParallel() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(5);

        Exchange result = template.send("direct:parallel", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody("James,Guillaume,Hiram,Rob,Roman");
                in.setHeader("foo", "bar");
            }
        });

        assertMockEndpointsSatisifed();
        Message out = result.getOut();

        assertMessageHeader(out, "foo", "bar");
        assertEquals((Integer)5, result.getProperty("aggregated", Integer.class));
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:seqential").splitter(body().tokenize(","), new UseLatestAggregationStrategy()).to("mock:result");
                from("direct:parallel").splitter(body().tokenize(","), new MyAggregationStrategy(), true).to("mock:result");
            }
        };
    }
}
