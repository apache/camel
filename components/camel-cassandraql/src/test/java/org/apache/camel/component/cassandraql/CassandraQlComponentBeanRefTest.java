package org.apache.camel.component.cassandraql;

import org.apache.camel.component.cassandraql.CassandraQlEndpoint;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import java.net.URLEncoder;
import java.util.Arrays;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.client.utils.URLEncodedUtils;
import org.cassandraunit.CassandraCQLUnit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

public class CassandraQlComponentBeanRefTest extends CamelTestSupport {
    @Rule
    public CassandraCQLUnit cassandra=CassandraUnitUtils.cassandraCQLUnit();
    @Produce(uri = "direct:input")
    ProducerTemplate producerTemplate;
    @BeforeClass
    public static void setUpClass() throws Exception {
        CassandraUnitUtils.startEmbeddedCassandra();
    }
    @AfterClass
    public static void tearDownClass() throws Exception {
        CassandraUnitUtils.cleanEmbeddedCassandra();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        Cluster cluster = Cluster.builder()
                .addContactPoint("localhost")
                .build();
        registry.bind("cassandraCluster", cluster);
        registry.bind("cassandraSession", cluster.connect("camel_ks"));
        registry.bind("insertCql", CQL);
        return registry;
    }
    public static final String CQL = "insert into camel_user(login, first_name, last_name) values (?, ?, ?)";
    public static final String SESSION_URI = "cql:bean:cassandraSession?cql=#insertCql";
    public static final String CLUSTER_URI = "cql:bean:cassandraCluster/camel_ks?cql=#insertCql";
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {                
                from("direct:inputSession")
                  .to(SESSION_URI);
                from("direct:inputCluster")
                  .to(CLUSTER_URI);
            }
        };
    }
    @Test
    public void testSession() throws Exception {
        CassandraQlEndpoint endpoint = getMandatoryEndpoint(SESSION_URI, CassandraQlEndpoint.class);

        assertEquals("camel_ks", endpoint.getKeyspace());
        assertEquals(CQL, endpoint.getCql());
    }
    @Test
    public void testCluster() throws Exception {
        CassandraQlEndpoint endpoint = getMandatoryEndpoint(CLUSTER_URI, CassandraQlEndpoint.class);

        assertEquals("camel_ks", endpoint.getKeyspace());
        assertEquals(CQL, endpoint.getCql());
    }

}
