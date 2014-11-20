package org.apache.camel.component.cassandraql;

import com.datastax.driver.core.Row;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.cassandraunit.CassandraCQLUnit;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CassandraQlComponentConsumerTest extends CamelTestSupport {
    @Rule
    public CassandraCQLUnit cassandra=CassandraUnitUtils.cassandraCQLUnit();
    private final Logger logger = LoggerFactory.getLogger(getClass());
    @BeforeClass
    public static void setUpClass() throws Exception {
        CassandraUnitUtils.startEmbeddedCassandra();
    }
    @AfterClass
    public static void tearDownClass() throws Exception {
        CassandraUnitUtils.cleanEmbeddedCassandra();
    }
    @Test
    public void testConsume_All() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultAll");
        mock.expectedMinimumMessageCount(1);       
        mock.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Object body = exchange.getIn().getBody();
                assertTrue(body instanceof List);
            }
        });
        mock.await(1, TimeUnit.SECONDS);
        assertMockEndpointsSatisfied();
    }
    @Test
    public void testConsume_One() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultOne");
        mock.expectedMinimumMessageCount(1);       
        mock.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Object body = exchange.getIn().getBody();
                assertTrue(body instanceof Row);
            }
        });
        mock.await(1, TimeUnit.SECONDS);
        
        assertMockEndpointsSatisfied();
    }
    private static final String CQL = "select login, first_name, last_name from camel_user";
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {                
                from("cql://localhost/camel_ks?cql="+CQL)
                  .to("mock:resultAll");
                from("cql://localhost/camel_ks?cql="+CQL+"&resultSetConversionStrategy=ONE")
                  .to("mock:resultOne");
            }
        };
    }
}
