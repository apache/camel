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
package org.apache.camel.processor.aggregator;

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AbstractListAggregationStrategy;

/**
 *
 */
public class CustomListAggregationStrategyCompletionFromBatchConsumerTest extends ContextTestSupport {

    @SuppressWarnings("unchecked")
    public void testCustomAggregationStrategy() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        template.sendBodyAndHeader("file:target/batch", "100", Exchange.FILE_NAME, "1.txt");
        template.sendBodyAndHeader("file:target/batch", "150", Exchange.FILE_NAME, "2.txt");
        template.sendBodyAndHeader("file:target/batch", "130", Exchange.FILE_NAME, "3.txt");

        context.startRoute("foo");

        assertMockEndpointsSatisfied();

        // the list will be stored as the message body by default
        List<Integer> numbers = result.getExchanges().get(0).getIn().getBody(List.class);
        assertNotNull(numbers);
        assertEquals(Integer.valueOf("100"), numbers.get(0));
        assertEquals(Integer.valueOf("150"), numbers.get(1));
        assertEquals(Integer.valueOf("130"), numbers.get(2));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/batch?initialDelay=0&delay=10&sortBy=file:name").routeId("foo").noAutoStartup()
                    .aggregate(new MyListOfNumbersStrategy()).constant(true)
                    .completionFromBatchConsumer()
                    .to("mock:result");
            }
        };
    }

    /**
     * Our strategy just group a list of integers.
     */
    public final class MyListOfNumbersStrategy extends AbstractListAggregationStrategy<Integer> {

        @Override
        public Integer getValue(Exchange exchange) {
            String s = exchange.getIn().getBody(String.class);
            return Integer.valueOf(s);
        }
    }

}
