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
package org.apache.camel.processor.aggregate.jdbc;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class ClusteredJdbcAggregateRecoverTest extends AbstractClusteredJdbcAggregationTestSupport {

    private static AtomicInteger counter = new AtomicInteger();

    @Override
    void configureJdbcAggregationRepository() {
        // enable recovery
        repo.setUseRecovery(true);
        // check faster
        repo.setRecoveryInterval(500, TimeUnit.MILLISECONDS);
        repo.setRecoveryByInstance(true);
        repo.setInstanceId("INSTANCE1");
        repobis.setUseRecovery(true);
        repobis.setRecoveryInterval(50, TimeUnit.MILLISECONDS);
        repobis.setRecoveryByInstance(true);
        repobis.setInstanceId("INSTANCE2");

    }

    @Test
    public void testJdbcAggregateRecover() throws Exception {
        // should fail the first 2 times and then recover
        getMockEndpoint("mock:aggregated").expectedMessageCount(3);
        getMockEndpoint("mock:result").expectedBodiesReceived("ABCDE");
        // should be marked as redelivered
        getMockEndpoint("mock:result").message(0).header(Exchange.REDELIVERED).isEqualTo(Boolean.TRUE);
        // on the 2nd redelivery attempt we success
        getMockEndpoint("mock:result").message(0).header(Exchange.REDELIVERY_COUNTER).isEqualTo(2);

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);
        template.sendBodyAndHeader("direct:start", "C", "id", 123);
        template.sendBodyAndHeader("direct:start", "D", "id", 123);
        template.sendBodyAndHeader("direct:start", "E", "id", 123);

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").aggregate(header("id"), new MyAggregationStrategy()).completionSize(5)
                        .aggregationRepository(repo)
                        .log("aggregated exchange id ${exchangeId} with ${body}").to("mock:aggregated").delay(1000)
                        // simulate errors the first two times
                        .process(new Processor() {
                            public void process(Exchange exchange) {
                                int count = counter.incrementAndGet();
                                if (count <= 2) {
                                    throw new IllegalArgumentException("Damn");
                                }
                            }
                        }).to("mock:result").end();
                from("direct:tutu").aggregate(header("id"), new MyAggregationStrategy()).completionSize(5).aggregationRepository(repobis)
                        .log("aggregated exchange id ${exchangeId} with ${body}").log("recover bis!!!!!!!!!!!!!!!!!").end();
            }
        };
    }
}
