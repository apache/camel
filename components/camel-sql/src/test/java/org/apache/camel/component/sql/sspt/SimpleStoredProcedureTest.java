package org.apache.camel.component.sql.sspt;


import org.apache.camel.Exchange;
import org.apache.camel.component.sql.sspt.ast.ParseException;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

public class SimpleStoredProcedureTest extends CamelTestSupport {

    SimpleStoredProcedureFactory parser = new SimpleStoredProcedureFactory();

    private EmbeddedDatabase db;
    private JdbcTemplate jdbcTemplate;

    @Before
    public void setUp() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.DERBY).addScript("sql/storedProcedureTest.sql").build();

        jdbcTemplate = new JdbcTemplate(db);

        super.setUp();
    }


    @Test
    public void shouldExecuteStoredProcedure() throws ParseException {
        SimpleStoredProcedure sp = new SimpleStoredProcedure(db, parser.parseTemplate("ADDNUMBERS" +
                "(INTEGER ${header.v1},INTEGER ${header.v2},OUT INTEGER resultofsum)"));

        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader("v1", 1);
        exchange.getIn().setHeader("v2", 2);


        sp.execute(exchange);

        Assert.assertEquals(Integer.valueOf(3), exchange.getOut().getHeader("resultofsum"));


    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        db.shutdown();
    }
}
