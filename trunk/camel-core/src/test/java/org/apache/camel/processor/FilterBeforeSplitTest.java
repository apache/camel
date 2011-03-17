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
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;

/**
 * Unit test to verify that Splitter aggregator clear the filtered history in case
 * filter has been used <b>before</b> the splitter.
 *
 * @version 
 */

public class FilterBeforeSplitTest extends ContextTestSupport {

    public void testFilterBeforeSplit() throws Exception {
        getMockEndpoint("mock:good").expectedBodiesReceived("Hello World how are you?");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello,World,how,are,you?");

        MockEndpoint split = getMockEndpoint("mock:split");
        split.expectedBodiesReceived("Hello", "World", "how", "are", "you?");

        template.sendBody("direct:start", "Hello World how are you?");

        assertMockEndpointsSatisfied();
    }

    public void testFiltered() throws Exception {
        getMockEndpoint("mock:good").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:before").expectedBodiesReceived("I will be filtered", "Hello World");
        getMockEndpoint("mock:result").expectedBodiesReceived("I,will,be,filtered", "Hello,World");
        getMockEndpoint("mock:split").expectedBodiesReceived("I", "will", "be", "filtered", "Hello", "World");

        template.sendBody("direct:start", "I will be filtered");
        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Predicate goodWord = body().contains("World");

                from("direct:start")
                    .to("mock:before")
                    .filter(goodWord)
                        .to("mock:good")
                    .end()
                    .split(body().tokenize(" "), new MyAggregationStrategy())
                        .to("mock:split")
                    .end()
                    .to("mock:result");
            }
        };
    }

    protected class MyAggregationStrategy implements AggregationStrategy {

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            String newBody = newExchange.getIn().getBody(String.class);

            if (oldExchange == null) {
                return newExchange;
            }

            String body = oldExchange.getIn().getBody(String.class);
            body = body + "," + newBody;
            oldExchange.getIn().setBody(body);
            return oldExchange;
        }

    }
}