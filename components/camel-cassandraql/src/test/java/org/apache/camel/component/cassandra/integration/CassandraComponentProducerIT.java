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
package org.apache.camel.component.cassandra.integration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.update.Update;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cassandra.CassandraConstants;
import org.apache.camel.component.cassandra.CassandraEndpoint;
import org.apache.camel.component.cassandra.MockLoadBalancingPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CassandraComponentProducerIT extends BaseCassandra {

    static final String CQL = "insert into camel_user(login, first_name, last_name) values (?, ?, ?)";
    static final String NO_PARAMETER_CQL = "select login, first_name, last_name from camel_user";

    @Produce("direct:input")
    ProducerTemplate producerTemplate;

    @Produce("direct:inputNoParameter")
    ProducerTemplate noParameterProducerTemplate;

    @Produce("direct:inputNotConsistent")
    ProducerTemplate notConsistentProducerTemplate;

    @Produce("direct:loadBalancingPolicy")
    ProducerTemplate loadBalancingPolicyTemplate;

    @Produce("direct:inputNoEndpointCql")
    ProducerTemplate producerTemplateNoEndpointCql;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                from("direct:input").toF("cql://%s/%s?cql=%s", getUrl(), KEYSPACE_NAME, CQL);
                from("direct:inputNoParameter")
                        .toF("cql://%s/%s?cql=%s", getUrl(), KEYSPACE_NAME, NO_PARAMETER_CQL);
                from("direct:loadBalancingPolicy")
                        .toF("cql://%s/%s?cql=%s&loadBalancingPolicyClass=org.apache.camel.component.cassandra.MockLoadBalancingPolicy",
                                getUrl(), KEYSPACE_NAME, NO_PARAMETER_CQL);
                from("direct:inputNotConsistent")
                        .toF("cql://%s/%s?cql=%s&consistencyLevel=ANY", getUrl(), KEYSPACE_NAME, CQL);
                from("direct:inputNoEndpointCql").toF("cql://%s/%s", getUrl(), KEYSPACE_NAME);
            }
        };
    }

    @Test
    public void testRequestUriCql() {
        producerTemplate.requestBody(Arrays.asList("w_jiang", "Willem", "Jiang"));

        ResultSet resultSet = getSession()
                .execute("select login, first_name, last_name from camel_user where login = ?", "w_jiang");
        Row row = resultSet.one();
        assertNotNull(row);
        assertEquals("Willem", row.getString("first_name"));
        assertEquals("Jiang", row.getString("last_name"));
    }

    @Test
    public void testRequestNoParameterNull() {
        Object response = noParameterProducerTemplate.requestBody(null);

        assertNotNull(response);
        assertIsInstanceOf(List.class, response);
    }

    @Test
    public void testRequestNoParameterEmpty() {
        Object response = noParameterProducerTemplate.requestBody(Collections.emptyList());

        assertNotNull(response);
        assertIsInstanceOf(List.class, response);
    }

    @Test
    public void testRequestMessageCql() {
        producerTemplate.requestBodyAndHeader(new Object[] { "Claus 2", "Ibsen 2", "c_ibsen" }, CassandraConstants.CQL_QUERY,
                "update camel_user set first_name=?, last_name=? where login=?");

        ResultSet resultSet = getSession()
                .execute("select login, first_name, last_name from camel_user where login = ?", "c_ibsen");
        Row row = resultSet.one();
        assertNotNull(row);
        assertEquals("Claus 2", row.getString("first_name"));
        assertEquals("Ibsen 2", row.getString("last_name"));
    }

    @Test
    public void testLoadBalancing() {
        loadBalancingPolicyTemplate.requestBodyAndHeader(new Object[] { "Claus 2", "Ibsen 2", "c_ibsen" },
                CassandraConstants.CQL_QUERY,
                "update camel_user set first_name=?, last_name=? where login=?");

        ResultSet resultSet = getSession()
                .execute("select login, first_name, last_name from camel_user where login = ?", "c_ibsen");
        Row row = resultSet.one();
        assertNotNull(row);
        assertEquals("Claus 2", row.getString("first_name"));
        assertEquals("Ibsen 2", row.getString("last_name"));

        Assertions.assertTrue(MockLoadBalancingPolicy.used);
    }

    /**
     * Test with incoming message containing a header with RegularStatement.
     */
    @Test
    public void testRequestMessageStatement() {

        Update update = QueryBuilder.update("camel_user")
                .setColumn("first_name", bindMarker())
                .setColumn("last_name", bindMarker())
                .whereColumn("login").isEqualTo(bindMarker());
        producerTemplate.requestBodyAndHeader(new Object[] { "Claus 2", "Ibsen 2", "c_ibsen" }, CassandraConstants.CQL_QUERY,
                update.build());

        ResultSet resultSet = getSession()
                .execute("select login, first_name, last_name from camel_user where login = ?", "c_ibsen");
        Row row = resultSet.one();
        assertNotNull(row);
        assertEquals("Claus 2", row.getString("first_name"));
        assertEquals("Ibsen 2", row.getString("last_name"));
    }

    /**
     * Simulate different CQL statements in the incoming message containing a header with RegularStatement, justifying
     * the cassandracql endpoint not containing a "cql" Uri parameter
     */
    @Test
    public void testEndpointNoCqlParameter() {
        Update update = QueryBuilder.update("camel_user")
                .setColumn("first_name", bindMarker())
                .whereColumn("login").isEqualTo(bindMarker());
        producerTemplateNoEndpointCql.sendBodyAndHeader(new Object[] { "Claus 2", "c_ibsen" }, CassandraConstants.CQL_QUERY,
                update.build());

        ResultSet resultSet1 = getSession()
                .execute("select login, first_name, last_name from camel_user where login = ?", "c_ibsen");
        Row row1 = resultSet1.one();
        assertNotNull(row1);
        assertEquals("Claus 2", row1.getString("first_name"));
        assertEquals("Ibsen", row1.getString("last_name"));

        update = QueryBuilder.update("camel_user")
                .setColumn("last_name", bindMarker())
                .whereColumn("login").isEqualTo(bindMarker());
        producerTemplateNoEndpointCql.sendBodyAndHeader(new Object[] { "Ibsen 2", "c_ibsen" }, CassandraConstants.CQL_QUERY,
                update.build());

        ResultSet resultSet2 = getSession()
                .execute("select login, first_name, last_name from camel_user where login = ?", "c_ibsen");
        Row row2 = resultSet2.one();
        assertNotNull(row2);
        assertEquals("Claus 2", row2.getString("first_name"));
        assertEquals("Ibsen 2", row2.getString("last_name"));
    }

    @Test
    public void testRequestNotConsistent() {
        CassandraEndpoint endpoint
                = getMandatoryEndpoint(String.format("cql://%s/%s?cql=%s&consistencyLevel=ANY", getUrl(), KEYSPACE_NAME, CQL),
                        CassandraEndpoint.class);
        assertEquals(ConsistencyLevel.ANY, endpoint.getConsistencyLevel());

        notConsistentProducerTemplate.requestBody(Arrays.asList("j_anstey", "Jonathan", "Anstey"));
    }
}
