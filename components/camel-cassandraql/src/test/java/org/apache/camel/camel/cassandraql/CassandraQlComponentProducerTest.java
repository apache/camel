package org.apache.camel.camel.cassandraql;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import java.util.Arrays;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.cassandraunit.CassandraCQLUnit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

public class CassandraQlComponentProducerTest extends CamelTestSupport {
    @Rule
    public CassandraCQLUnit cassandra=CassandraUnitUtils.cassandraCQLUnit();
    @Produce(uri = "direct:input")
    ProducerTemplate producerTemplate;
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
    private static final String CQL = "insert into camel_user(login, first_name, last_name) values (?, ?, ?)";
    private static final String NOT_CONSISTENT_URI="cql://localhost/camel_ks?cql="+CQL+"&consistencyLevel=ANY";
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                
                from("direct:input")
                  .to("cql://localhost/camel_ks?cql="+CQL);
                from("direct:inputNotConsistent")
                  .to(NOT_CONSISTENT_URI);
            }
        };
    }
    @Test
    public void testRequest_UriCql() throws Exception {
        Object response = producerTemplate.requestBody(Arrays.asList("w_jiang","Willem","Jiang"));
        
        Session session = CassandraUnitUtils.connectCassandra();
        ResultSet resultSet = session.execute("select login, first_name, last_name from camel_user where login = ?", "w_jiang");
        Row row = resultSet.one();
        assertNotNull(row);
        assertEquals("Willem", row.getString("first_name"));
        assertEquals("Jiang", row.getString("last_name"));
        session.close();
    }
    @Test
    public void testRequest_MessageCql() throws Exception {
        Object response = producerTemplate.requestBodyAndHeader(new Object[]{"Claus 2","Ibsen 2", "c_ibsen"}, CassandraQlConstants.CQL_QUERY, "update camel_user set first_name=?, last_name=? where login=?");
        
        Session session = CassandraUnitUtils.connectCassandra();
        ResultSet resultSet = session.execute("select login, first_name, last_name from camel_user where login = ?", "c_ibsen");
        Row row = resultSet.one();
        assertNotNull(row);
        assertEquals("Claus 2", row.getString("first_name"));
        assertEquals("Ibsen 2", row.getString("last_name"));
        session.close();
    }

    @Test
    public void testRequest_NotConsistent() throws Exception {
        
        CassandraQlEndpoint endpoint = getMandatoryEndpoint(NOT_CONSISTENT_URI, CassandraQlEndpoint.class);
        assertEquals(ConsistencyLevel.ANY, endpoint.getConsistencyLevel());
        
        Object response = notConsistentProducerTemplate.requestBody(Arrays.asList("j_anstey","Jonathan","Anstey"));
    }
}
