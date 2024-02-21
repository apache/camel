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
package org.apache.camel.component.arangodb.integration;

import java.util.Collection;

import com.arangodb.ArangoDBException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperties;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.apache.camel.component.arangodb.ArangoDbConstants.AQL_QUERY;
import static org.apache.camel.component.arangodb.ArangoDbConstants.RESULT_CLASS_TYPE;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIfSystemProperties({
        @DisabledIfSystemProperty(named = "ci.env.name", matches = "apache.org",
                                  disabledReason = "Apache CI nodes are too resource constrained for this test"),
        @DisabledIfSystemProperty(named = "arangodb.tests.disable", matches = "true",
                                  disabledReason = "Manually disabled tests")
})
public class ArangoGraphQueriesIT extends BaseGraph {
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:query")
                        .to("arangodb:{{arangodb.testDb}}?operation=AQL_QUERY");
            }
        };
    }

    @Test
    public void testTravesalOutbound() {
        // depth = 3
        Exchange result = getResultOutboundQuery(3, vertexA.getId(), " OUTBOUND ");
        assertTrue(result.getMessage().getBody() instanceof Collection);

        Collection<String> list = (Collection<String>) result.getMessage().getBody();
        assertThat(list, hasItems("B", "C", "D", "E", "F", "G", "H", "I", "J"));

        // depth = 2
        result = getResultOutboundQuery(2, vertexA.getId(), " OUTBOUND ");
        assertTrue(result.getMessage().getBody() instanceof Collection);

        list = (Collection<String>) result.getMessage().getBody();
        assertThat(list, hasItems("B", "C", "D", "E", "F", "G"));
    }

    private Exchange getResultOutboundQuery(int depth, String vertexId, String outInBound) {
        String query = "FOR v IN 1.." + depth + outInBound + " '" + vertexId + "' GRAPH '" + GRAPH_NAME + "' RETURN v._key";
        return getResult(query);
    }

    private Exchange getResult(String query) {
        Exchange result = template.request("direct:query", exchange -> {
            exchange.getMessage().setHeader(AQL_QUERY, query);
            exchange.getMessage().setHeader(RESULT_CLASS_TYPE, String.class);
        });
        return result;
    }

    @Test
    public void testTravesalInbound() {
        // depth = 3
        Exchange result = getResultOutboundQuery(3, vertexH.getId(), " INBOUND ");
        assertTrue(result.getMessage().getBody() instanceof Collection);

        Collection<String> list = (Collection<String>) result.getMessage().getBody();
        assertEquals(3, list.size());
        assertThat(list, hasItems("A", "B", "D"));

        // depth = 2
        result = getResultOutboundQuery(2, vertexH.getId(), " INBOUND ");
        assertTrue(result.getMessage().getBody() instanceof Collection);

        list = (Collection<String>) result.getMessage().getBody();
        assertEquals(2, list.size());
        assertThat(list, hasItems("B", "D"));
    }

    @Test
    public void queryShortestPathFromAToH() throws ArangoDBException {
        String query = "FOR v, e IN OUTBOUND SHORTEST_PATH '" + vertexA.getId() + "' TO '" + vertexH.getId() + "' GRAPH '"
                       + GRAPH_NAME + "' RETURN v._key";
        Exchange result = getResult(query);
        Collection<String> list = (Collection<String>) result.getMessage().getBody();
        assertEquals(4, list.size());
        assertThat(list, hasItems("A", "B", "D", "H"));
    }

}
