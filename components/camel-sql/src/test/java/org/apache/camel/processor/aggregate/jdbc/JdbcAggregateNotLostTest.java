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
package org.apache.camel.processor.aggregate.jdbc;

import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class JdbcAggregateNotLostTest extends AbstractJdbcAggregationTestSupport {

    @Test
    public void testJdbcAggregateNotLost() throws Exception {
        getMockEndpoint("mock:aggregated").expectedBodiesReceived("ABCDE");
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);
        template.sendBodyAndHeader("direct:start", "C", "id", 123);
        template.sendBodyAndHeader("direct:start", "D", "id", 123);
        template.sendBodyAndHeader("direct:start", "E", "id", 123);

        assertMockEndpointsSatisfied(30, TimeUnit.SECONDS);

        String exchangeId = getMockEndpoint("mock:aggregated").getReceivedExchanges().get(0).getExchangeId();

        // the exchange should be in the completed repo where we should be able to find it
        Exchange completed = repo.recover(context, exchangeId);
        // assert the exchange was not lost and we got all the information still
        assertNotNull(completed);
        // should retain the exchange id
        assertEquals(exchangeId, completed.getExchangeId());
        assertEquals("ABCDE", completed.getIn().getBody());
        assertEquals(123, completed.getIn().getHeader("id"));
        assertEquals("size", completed.getProperty(Exchange.AGGREGATED_COMPLETED_BY));
        assertEquals(5, completed.getProperty(Exchange.AGGREGATED_SIZE));
        // will store correlation keys as String
        assertEquals("123", completed.getProperty(Exchange.AGGREGATED_CORRELATION_KEY));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .aggregate(header("id"), new MyAggregationStrategy())
                        .completionSize(5).aggregationRepository(repo)
                        .log("aggregated exchange id ${exchangeId} with ${body}")
                        .to("mock:aggregated")
                                // throw an exception to fail, which we then will loose this message
                        .throwException(new IllegalArgumentException("Damn"))
                        .to("mock:result")
                        .end();
            }
        };
    }
}