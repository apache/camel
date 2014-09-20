package org.apache.camel.camel.cassandraql;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.cassandraunit.CassandraCQLUnit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

public class CassandraQlComponentConsumerTest extends CamelTestSupport {
    @Rule
    public CassandraCQLUnit cassandra=CassandraUnitUtils.cassandraCQLUnit();

    @BeforeClass
    public static void setUpClass() throws Exception {
        CassandraUnitUtils.startEmbeddedCassandra();
    }
    @AfterClass
    public static void tearDownClass() throws Exception {
        CassandraUnitUtils.cleanEmbeddedCassandra();
    }
    @Test
    public void testCassandraQl() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);       
        
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("cql://localhost/camel_ks?cql=select login, first_name, last_name from camel_user")
                  .to("mock:result");
            }
        };
    }
}
