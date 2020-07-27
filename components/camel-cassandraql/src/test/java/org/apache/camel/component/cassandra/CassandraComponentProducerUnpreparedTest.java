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
package org.apache.camel.component.cassandra;

import java.util.Arrays;
import java.util.List;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.update.Update;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;
import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CassandraComponentProducerUnpreparedTest extends BaseCassandraTest {

    static final String CQL = "insert into camel_user(login, first_name, last_name) values (?, ?, ?)";
    static final String NO_PARAMETER_CQL = "select login, first_name, last_name from camel_user";

    @RegisterExtension
    static CassandraCQLUnit cassandra = CassandraUnitUtils.cassandraCQLUnit();

    @Produce("direct:input")
    ProducerTemplate producerTemplate;

    @Produce("direct:inputNoParameter")
    ProducerTemplate noParameterProducerTemplate;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {

                from("direct:input").to("cql://localhost/camel_ks?cql=" + CQL + "&prepareStatements=false");
                from("direct:inputNoParameter")
                        .to("cql://localhost/camel_ks?cql=" + NO_PARAMETER_CQL + "&prepareStatements=false");
            }
        };
    }

    @Test
    public void testRequestUriCql() throws Exception {
        producerTemplate.requestBody(Arrays.asList("w_jiang", "Willem", "Jiang"));

        CqlSession session = CassandraUnitUtils.cassandraSession();
        ResultSet resultSet = session
                .execute(String.format("select login, first_name, last_name from camel_user where login = '%s'", "w_jiang"));
        Row row = resultSet.one();
        assertNotNull(row);
        assertEquals("Willem", row.getString("first_name"));
        assertEquals("Jiang", row.getString("last_name"));
        session.close();
    }

    @Test
    public void testRequestNoParameterNull() throws Exception {
        Object response = noParameterProducerTemplate.requestBody(null);

        assertNotNull(response);
        assertIsInstanceOf(List.class, response);
    }

    @Test
    public void testRequestNoParameterEmpty() throws Exception {
        Object response = noParameterProducerTemplate.requestBody(null);

        assertNotNull(response);
        assertIsInstanceOf(List.class, response);
    }

    @Test
    public void testRequestMessageCql() throws Exception {
        producerTemplate.requestBodyAndHeader(new Object[] { "Claus 2", "Ibsen 2", "c_ibsen" }, CassandraConstants.CQL_QUERY,
                "update camel_user set first_name=?, last_name=? where login=?");

        CqlSession session = CassandraUnitUtils.cassandraSession();
        ResultSet resultSet = session
                .execute(String.format("select login, first_name, last_name from camel_user where login = '%s'", "c_ibsen"));
        Row row = resultSet.one();
        assertNotNull(row);
        assertEquals("Claus 2", row.getString("first_name"));
        assertEquals("Ibsen 2", row.getString("last_name"));
        session.close();
    }

    /**
     * Test with incoming message containing a header with RegularStatement.
     */
    @Test
    public void testRequestMessageStatement() throws Exception {
        Update update = QueryBuilder.update("camel_user")
                .setColumn("first_name", literal("Claus 2"))
                .setColumn("last_name", literal("Ibsen 2"))
                .whereColumn("login").isEqualTo(literal("c_ibsen"));
        producerTemplate.requestBodyAndHeader(null, CassandraConstants.CQL_QUERY, update.build());

        CqlSession session = CassandraUnitUtils.cassandraSession();
        ResultSet resultSet = session
                .execute(String.format("select login, first_name, last_name from camel_user where login = '%s'", "c_ibsen"));
        Row row = resultSet.one();
        assertNotNull(row);
        assertEquals("Claus 2", row.getString("first_name"));
        assertEquals("Ibsen 2", row.getString("last_name"));
        session.close();
    }

}
