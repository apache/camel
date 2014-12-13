/*
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.processor.idempotent.cassandraql;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import org.apache.camel.component.cassandraql.CassandraUnitUtils;
import org.junit.Test;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
/**
 * Unite test for {@link CassandraQlIdempotentRepository}
 */
public class CassandraQlIdempotentTest extends CamelTestSupport {
    private Cluster cluster;
    private CassandraQlIdempotentRepository idempotentRepository;
    
    @Override
    protected void doPreSetup() throws Exception {
        CassandraUnitUtils.startEmbeddedCassandra();
        cluster = CassandraUnitUtils.cassandraCluster();        
        Session rootSession=cluster.connect();
        CassandraUnitUtils.loadCQLDataSet(rootSession, "IdempotentDataSet.cql");
        rootSession.close();
        idempotentRepository = new NamedCassandraQlIdempotentRepository(cluster, CassandraUnitUtils.KEYSPACE, "ID");
        idempotentRepository.start();
        super.doPreSetup();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        idempotentRepository.stop();
        CassandraUnitUtils.cleanEmbeddedCassandra();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:input")
                        .idempotentConsumer(header("idempotentId"), idempotentRepository)
                        .to("mock:output");
            }
        };
    }
    private void send(String idempotentId, String body) {
        super.template.sendBodyAndHeader("direct:input", body, "idempotentId", idempotentId);
    }
    @Test
    public void testIdempotentRoute() throws Exception {
        // Given
        MockEndpoint mockOutput = getMockEndpoint("mock:output");
        mockOutput.expectedMessageCount(2);
        mockOutput.expectedBodiesReceivedInAnyOrder("A","B");
        // When
        send("1", "A");
        send("2", "B");
        send("1", "A");
        send("2", "B");
        send("1", "A");
        // Then
        mockOutput.assertIsSatisfied();
        
    }
}
