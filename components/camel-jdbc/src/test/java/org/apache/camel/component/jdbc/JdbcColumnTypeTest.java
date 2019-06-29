package org.apache.camel.component.jdbc;

import java.io.InputStream;
import java.io.StringWriter;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.logging.log4j.core.util.IOUtils;
import org.junit.Test;

public class JdbcColumnTypeTest extends AbstractJdbcTestSupport {

    @Test
    @SuppressWarnings("unchecked")
    public void testClobColumnType() throws SQLException {
        Endpoint directHelloEndpoint = context.getEndpoint("direct:hello");
        Exchange directHelloExchange = directHelloEndpoint.createExchange();

        directHelloExchange.getIn().setBody("select * from tableWithClob");

        Exchange out = template.send(directHelloEndpoint, directHelloExchange);
        assertNotNull(out);
        assertNotNull(out.getOut());

        List<Map<String, Object>> returnValues = out.getOut().getBody(List.class);
        assertNotNull(returnValues);
        assertEquals(1, returnValues.size());
        Map<String, Object> row = returnValues.get(0);
        assertEquals("id1", row.get("ID"));
        assertNotNull(row.get("PICTURE"));

        Set<String> columnNames = (Set<String>) out.getOut().getHeader(JdbcConstants.JDBC_COLUMN_NAMES);
        assertNotNull(columnNames);
        assertEquals(2, columnNames.size());
        assertTrue(columnNames.contains("ID"));
        assertTrue(columnNames.contains("PICTURE"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:hello").to("jdbc:testdb?readSize=100");
            }
        };
    }
}
