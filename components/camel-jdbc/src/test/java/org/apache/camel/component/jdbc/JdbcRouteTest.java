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
package org.apache.camel.component.jdbc;

import java.util.ArrayList;
import java.util.HashMap;
import javax.sql.DataSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * @version $Revision$
 */
public class JdbcRouteTest extends ContextTestSupport {
    private String driverClass = "org.hsqldb.jdbcDriver";
    private String url = "jdbc:hsqldb:mem:camel_jdbc";
    private String user = "sa";
    private String password = "";
    private DataSource ds;
    private JdbcTemplate jdbc;

    public void testPojoRoutes() throws Exception {
        // START SNIPPET: invoke
        // first we create our exchange using the endpoint
        Endpoint endpoint = context.getEndpoint("direct:hello");
        Exchange exchange = endpoint.createExchange();
        // then we set the SQL on the in body
        exchange.getIn().setBody("select * from customer order by ID");

        // now we send the exchange to the endpoint, and receives the response from Camel
        Exchange out = template.send(endpoint, exchange);

        // assertions of the response
        assertNotNull(out);
        assertNotNull(out.getOut());
        ArrayList<HashMap<String, Object>> data = out.getOut().getBody(ArrayList.class);
        assertNotNull("out body could not be converted to an ArrayList - was: "
            + out.getOut().getBody(), data);
        assertEquals(2, data.size());
        HashMap<String, Object> row = data.get(0);
        assertEquals("cust1", row.get("ID"));
        assertEquals("jstrachan", row.get("NAME"));
        row = data.get(1);
        assertEquals("cust2", row.get("ID"));
        assertEquals("nsandhu", row.get("NAME"));
        // END SNIPPET: invoke
    }


    protected JndiRegistry createRegistry() throws Exception {
        // START SNIPPET: register
        JndiRegistry reg = super.createRegistry();
        reg.bind("testdb", ds);
        return reg;
        // END SNIPPET: register
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            // START SNIPPET: route
            // lets add simple route
            public void configure() throws Exception {
                from("direct:hello").to("jdbc:testdb?readSize=100");
            }
            // END SNIPPET: route
        };
    }

    protected void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, user, password);
        dataSource.setDriverClassName(driverClass);
        ds = dataSource;

        JdbcTemplate jdbc = new JdbcTemplate(ds);
        // START SNIPPET: setup
        jdbc.execute("create table customer (id varchar(15), name varchar(10))");
        jdbc.execute("insert into customer values('cust1','jstrachan')");
        jdbc.execute("insert into customer values('cust2','nsandhu')");
        // END SNIPPET: setup
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("drop table customer");
    }

}
