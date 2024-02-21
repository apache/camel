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
package org.apache.camel.processor.idempotent.cassandra;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cassandra.integration.BaseCassandra;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unite test for {@link CassandraIdempotentRepository}
 */
public class CassandraIdempotentIT extends BaseCassandra {

    private CassandraIdempotentRepository idempotentRepository = createRepo();

    protected CassandraIdempotentRepository createRepo() {
        CassandraIdempotentRepository idempotentRepository = new NamedCassandraIdempotentRepository(getSession(), "ID");
        idempotentRepository.setTable("NAMED_CAMEL_IDEMPOTENT");
        idempotentRepository.start();

        return idempotentRepository;
    }

    @AfterEach
    public void tearDown() {
        idempotentRepository.stop();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:input").idempotentConsumer(header("idempotentId"), idempotentRepository).to("mock:output");
            }
        };
    }

    private void send(String idempotentId, String body) {
        camelContextExtension.getProducerTemplate()
                .sendBodyAndHeader("direct:input", body, "idempotentId", idempotentId);
    }

    @Test
    public void testIdempotentRoute() throws Exception {
        // Given
        MockEndpoint mockOutput = getMockEndpoint("mock:output");
        mockOutput.expectedMessageCount(2);
        mockOutput.expectedBodiesReceivedInAnyOrder("A", "B");
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
