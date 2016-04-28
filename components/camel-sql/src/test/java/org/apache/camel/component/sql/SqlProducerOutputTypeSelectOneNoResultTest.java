package org.apache.camel.component.sql;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.test.spring.CamelSpringDelegatingTestContextLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

public class SqlProducerOutputTypeSelectOneNoResultTest extends CamelTestSupport{

    @EndpointInject(uri = "mock:result")
    MockEndpoint result;

    private EmbeddedDatabase db;

    @Before
    public void setUp() throws Exception {
        db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.DERBY).addScript("sql/createAndPopulateDatabase5.sql").build();

        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        db.shutdown();
    }
    
	@Test
	public void testSqlEndpoint() throws Exception {

		String expectedBody = "body";
		result.expectedBodiesReceived(expectedBody);
		template.sendBody("direct:start",expectedBody);
		result.assertIsSatisfied();

	}
    
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

				from("direct:start")
				.to("sql:select id from mytable where 1 = 2?outputHeader=myHeader&outputType=SelectOne")
				.log("${body}").to("mock:result");
            }
        };
    }
}
