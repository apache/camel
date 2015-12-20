package org.apache.camel.component.sql.sspt;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sql.SqlComponent;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by snurmine on 12/30/15.
 */
public class ProducerTest extends CamelTestSupport {


    private EmbeddedDatabase db;

    @Before
    public void setUp() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.DERBY).addScript("sql/storedProcedureTest.sql").build();

        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        db.shutdown();
    }

    @Test
    public void shouldExecuteStoredProcedure() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:query");
        mock.expectedMessageCount(1);


        Map<String, Object> headers = new HashMap<>();
        headers.put("num1", 1);
        headers.put("num2", 2);
        template.requestBodyAndHeaders("direct:query", null, headers);

        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);

        assertEquals(Integer.valueOf(3), exchange.getIn().getHeader("resultofsum"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // required for the sql component
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                from("direct:query").to("sql:sspt:ADDNUMBERS(INTEGER ${headers.num1},INTEGER ${headers"
                        + ".num2},OUT INTEGER resultofsum)").to("mock:query");
            }
        };
    }
}
