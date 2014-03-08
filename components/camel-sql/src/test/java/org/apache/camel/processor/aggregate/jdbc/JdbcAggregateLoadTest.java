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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbcAggregateLoadTest extends AbstractJdbcAggregationTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcAggregateLoadTest.class);
    private static final int SIZE = 500;

    @Test
    public void testLoadTestJdbcAggregate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.setResultWaitTime(50 * 1000);

        LOG.info("Staring to send " + SIZE + " messages.");

        for (int i = 0; i < SIZE; i++) {
            final int value = 1;
            char id = 'A';
            LOG.debug("Sending {} with id {}", value, id);
            template.sendBodyAndHeader("seda:start?size=" + SIZE, value, "id", "" + id);
        }

        LOG.info("Sending all " + SIZE + " message done. Now waiting for aggregation to complete.");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start?size=" + SIZE)
                        .to("log:input?groupSize=500")
                        .aggregate(header("id"), new MyAggregationStrategy())
                        .aggregationRepository(repo)
                        .completionSize(SIZE)
                        .to("log:output?showHeaders=true")
                        .to("mock:result")
                        .end();
            }
        };
    }
}