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
package org.apache.camel.component.neo4j;

import org.apache.camel.Exchange;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The operation option of the endpoint selects the operation, and the CamelNeo4jOperation header wins over it. The
 * endpoint has no label and no vector index name, so each operation fails on the option it misses, before it calls
 * Neo4j: no server is needed.
 */
class Neo4jOperationOptionTest extends CamelTestSupport {

    private static final String URI = "neo4j:test?databaseUrl=bolt://localhost:7999&username=neo4j&password=secret";

    @Test
    void operationOption() {
        Exchange result = fluentTemplate.to(URI + "&operation=RETRIEVE_NODES")
                .request(Exchange.class);

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, result.getException());
        assertEquals("label must be specified", e.getMessage());
    }

    @Test
    void headerWinsOverOperationOption() {
        Exchange result = fluentTemplate.to(URI + "&operation=RETRIEVE_NODES")
                .withHeader(Neo4jHeaders.OPERATION, Neo4Operation.VECTOR_SIMILARITY_SEARCH)
                .request(Exchange.class);

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, result.getException());
        assertEquals("vectorIndexName must be specified", e.getMessage());
    }

    @Test
    void noOperation() {
        Exchange result = fluentTemplate.to(URI)
                .request(Exchange.class);

        assertInstanceOf(NoSuchHeaderException.class, result.getException());
    }
}
