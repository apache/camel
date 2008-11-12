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
package org.apache.camel.component.sql;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * @version $Revision$
 */
public class SqlDataSourceRefTest extends ContextTestSupport {
    protected String driverClass = "org.hsqldb.jdbcDriver";
    protected String url = "jdbc:hsqldb:mem:camel_jdbc";
    protected String user = "sa";
    protected String password = "";
    private JdbcTemplate jdbcTemplate;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("jdbc/myDataSource", createDataSource());
        return jndi;
    }

    public void testSimpleBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        template.sendBody("direct:simple", "GPL");
        mock.assertIsSatisfied();
        List received = assertIsInstanceOf(List.class, mock.getReceivedExchanges().get(0).getIn().getBody());
        Map row = assertIsInstanceOf(Map.class, received.get(0));
        assertEquals("Linux", row.get("PROJECT"));
    }

    protected void setUp() throws Exception {
        Class.forName(driverClass);
        super.setUp();

        jdbcTemplate = new JdbcTemplate(createDataSource());
        jdbcTemplate.execute("create table projects (id integer primary key,"
                             + "project varchar(10), license varchar(5))");
        jdbcTemplate.execute("insert into projects values (1, 'Camel', 'ASF')");
        jdbcTemplate.execute("insert into projects values (2, 'AMQ', 'ASF')");
        jdbcTemplate.execute("insert into projects values (3, 'Linux', 'GPL')");
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(createDataSource());
        jdbcTemplate.execute("drop table projects");
    }

    private DataSource createDataSource() {
        return new SingleConnectionDataSource(url, user, password, true);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e1
                from("direct:simple")
                    .to("sql:select * from projects where license = # order by id?dataSourceRef=jdbc/myDataSource")
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

}