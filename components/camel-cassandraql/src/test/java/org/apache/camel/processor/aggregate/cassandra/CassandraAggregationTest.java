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
package org.apache.camel.processor.aggregate.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cassandra.CassandraUnitUtils;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Unite test for {@link CassandraAggregationRepository}
 */
public class CassandraAggregationTest extends CamelTestSupport {
    private Cluster cluster;
    private CassandraAggregationRepository aggregationRepository;

    public static boolean canTest() {
        // we cannot test on CI
        return System.getenv("BUILD_ID") == null;
    }

    @Override
    protected void doPreSetup() throws Exception {
        if (canTest()) {
            CassandraUnitUtils.startEmbeddedCassandra();
            cluster = CassandraUnitUtils.cassandraCluster();
            Session rootSession = cluster.connect();
            CassandraUnitUtils.loadCQLDataSet(rootSession, "NamedAggregationDataSet.cql");
            rootSession.close();
            aggregationRepository = new NamedCassandraAggregationRepository(cluster, CassandraUnitUtils.KEYSPACE, "ID");
            aggregationRepository.setTable("NAMED_CAMEL_AGGREGATION");
            aggregationRepository.start();
        }
        super.doPreSetup();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (canTest()) {
            aggregationRepository.stop();
            cluster.close();
            try {
                CassandraUnitUtils.cleanEmbeddedCassandra();
            } catch (Throwable e) {
                // ignore shutdown errors
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                AggregationStrategy aggregationStrategy = new AggregationStrategy() {
                    @Override
                    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                        if (oldExchange == null) {
                            return newExchange;
                        }
                        String oldBody = oldExchange.getIn().getBody(String.class);
                        String newBody = newExchange.getIn().getBody(String.class);
                        oldExchange.getIn().setBody(oldBody + "," + newBody);
                        return oldExchange;
                    }
                };
                from("direct:input")
                        .aggregate(header("aggregationId"), aggregationStrategy)
                        .completionSize(3).completionTimeout(3000L)
                        .aggregationRepository(aggregationRepository)
                        .to("mock:output");
            }
        };
    }

    private void send(String aggregationId, String body) {
        super.template.sendBodyAndHeader("direct:input", body, "aggregationId", aggregationId);
    }

    @Test
    public void testAggregationRoute() throws Exception {
        if (!canTest()) {
            return;
        }

        // Given
        MockEndpoint mockOutput = getMockEndpoint("mock:output");
        mockOutput.expectedMessageCount(2);
        mockOutput.expectedBodiesReceivedInAnyOrder("A,C,E", "B,D");
        // When
        send("1", "A");
        send("2", "B");
        send("1", "C");
        send("2", "D");
        send("1", "E");
        // Then
        mockOutput.assertIsSatisfied(4000L);
    }
}
