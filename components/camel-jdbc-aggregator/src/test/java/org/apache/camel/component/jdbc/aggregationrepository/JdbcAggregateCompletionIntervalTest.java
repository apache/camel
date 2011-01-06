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
package org.apache.camel.component.jdbc.aggregationrepository;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class JdbcAggregateCompletionIntervalTest extends CamelTestSupport {

    private JdbcAggregationRepository repo;

    @Before
    @Override
    public void setUp() throws Exception {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/component/jdbc/aggregationrepository/JdbcSpringDataSource.xml");
        repo = applicationContext.getBean("repo1", JdbcAggregationRepository.class);
        super.setUp();
    }

    @Test
    public void testJdbcAggregateCompletionInterval() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.setResultWaitTime(30 * 1000L);
        mock.expectedBodiesReceived("ABCD", "E");

        // wait a bit so we complete on the next poll
        Thread.sleep(2000);

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);
        template.sendBodyAndHeader("direct:start", "C", "id", 123);
        template.sendBodyAndHeader("direct:start", "D", "id", 123);

        Thread.sleep(6000);

        template.sendBodyAndHeader("direct:start", "E", "id", 123);

        assertMockEndpointsSatisfied();

        // from endpoint should be preserved
        assertEquals("direct://start", mock.getReceivedExchanges().get(0).getFromEndpoint().getEndpointUri());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            // START SNIPPET: e1
            public void configure() throws Exception {
                // here is the Camel route where we aggregate
                from("direct:start")
                        .aggregate(header("id"), new MyAggregationStrategy())
                                // complete every 5th seconds
                        .completionInterval(5000).aggregationRepository(repo)
                        .to("mock:aggregated");
            }
            // END SNIPPET: e1
        };
    }

    public static class MyAggregationStrategy implements AggregationStrategy {

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }
            String body1 = oldExchange.getIn().getBody(String.class);
            String body2 = newExchange.getIn().getBody(String.class);

            oldExchange.getIn().setBody(body1 + body2);
            return oldExchange;
        }
    }
}