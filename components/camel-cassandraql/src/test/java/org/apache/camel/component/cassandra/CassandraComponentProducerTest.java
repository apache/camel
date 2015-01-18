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
package org.apache.camel.component.cassandra;

import com.datastax.driver.core.*;
import static com.datastax.driver.core.querybuilder.QueryBuilder.*;
import com.datastax.driver.core.querybuilder.Update;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.cassandraunit.CassandraCQLUnit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CassandraComponentProducerTest extends CamelTestSupport {

    private static final String CQL = "insert into camel_user(login, first_name, last_name) values (?, ?, ?)";
    private static final String NO_PARAMETER_CQL = "select login, first_name, last_name from camel_user";
    private static final String NOT_CONSISTENT_URI = "cql://localhost/camel_ks?cql=" + CQL + "&consistencyLevel=ANY";

    @Rule
    public CassandraCQLUnit cassandra = CassandraUnitUtils.cassandraCQLUnit();

    @Produce(uri = "direct:input")
    ProducerTemplate producerTemplate;

    @Produce(uri = "direct:inputNoParameter")
    ProducerTemplate noParameterProducerTemplate;

    @Produce(uri = "direct:inputNotConsistent")
    ProducerTemplate notConsistentProducerTemplate;

    @BeforeClass
    public static void setUpClass() throws Exception {
        CassandraUnitUtils.startEmbeddedCassandra();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        CassandraUnitUtils.cleanEmbeddedCassandra();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {

                from("direct:input")
                        .to("cql://localhost/camel_ks?cql=" + CQL);
                from("direct:inputNoParameter")
                        .to("cql://localhost/camel_ks?cql=" + NO_PARAMETER_CQL);
                from("direct:inputNotConsistent")
                        .to(NOT_CONSISTENT_URI);
            }
        };
    }

    @Test
    public void testRequestUriCql() throws Exception {
        Object response = producerTemplate.requestBody(Arrays.asList("w_jiang", "Willem", "Jiang"));

        Cluster cluster = CassandraUnitUtils.cassandraCluster();
        Session session = cluster.connect(CassandraUnitUtils.KEYSPACE);
        ResultSet resultSet = session.execute("select login, first_name, last_name from camel_user where login = ?", "w_jiang");
        Row row = resultSet.one();
        assertNotNull(row);
        assertEquals("Willem", row.getString("first_name"));
        assertEquals("Jiang", row.getString("last_name"));
        session.close();
        cluster.close();
    }

    @Test
    public void testRequestNoParameter_Null() throws Exception {
        Object response = noParameterProducerTemplate.requestBody(null);

        assertNotNull(response);
        assertIsInstanceOf(List.class, response);
        List<Row> rows = (List<Row>) response;
    }

    @Test
    public void testRequestNoParameter_Empty() throws Exception {
        Object response = noParameterProducerTemplate.requestBody(Collections.emptyList());

        assertNotNull(response);
        assertIsInstanceOf(List.class, response);
        List<Row> rows = (List<Row>) response;
    }

    @Test
    public void testRequestMessageCql() throws Exception {
        Object response = producerTemplate.requestBodyAndHeader(new Object[]{"Claus 2", "Ibsen 2", "c_ibsen"},
                CassandraConstants.CQL_QUERY, "update camel_user set first_name=?, last_name=? where login=?");

        Cluster cluster = CassandraUnitUtils.cassandraCluster();
        Session session = cluster.connect(CassandraUnitUtils.KEYSPACE);
        ResultSet resultSet = session.execute("select login, first_name, last_name from camel_user where login = ?", "c_ibsen");
        Row row = resultSet.one();
        assertNotNull(row);
        assertEquals("Claus 2", row.getString("first_name"));
        assertEquals("Ibsen 2", row.getString("last_name"));
        session.close();
        cluster.close();
    }

    /**
     * Test with incoming message containing a header with RegularStatement.
     */
    @Test
    public void testRequestMessageStatement() throws Exception {
        Update.Where update = update("camel_user")
                .with(set("first_name", bindMarker()))
                .and(set("last_name", bindMarker()))
                .where(eq("login", bindMarker()));
        Object response = producerTemplate.requestBodyAndHeader(new Object[]{"Claus 2", "Ibsen 2", "c_ibsen"},
                CassandraConstants.CQL_QUERY, update);

        Cluster cluster = CassandraUnitUtils.cassandraCluster();
        Session session = cluster.connect(CassandraUnitUtils.KEYSPACE);
        ResultSet resultSet = session.execute("select login, first_name, last_name from camel_user where login = ?", "c_ibsen");
        Row row = resultSet.one();
        assertNotNull(row);
        assertEquals("Claus 2", row.getString("first_name"));
        assertEquals("Ibsen 2", row.getString("last_name"));
        session.close();
        cluster.close();
    }

    @Test
    public void testRequestNotConsistent() throws Exception {

        CassandraEndpoint endpoint = getMandatoryEndpoint(NOT_CONSISTENT_URI, CassandraEndpoint.class);
        assertEquals(ConsistencyLevel.ANY, endpoint.getConsistencyLevel());

        Object response = notConsistentProducerTemplate.requestBody(Arrays.asList("j_anstey", "Jonathan", "Anstey"));
    }
}
