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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * @version $Revision$
 */
public class SqlPreserveHeaderTest extends ContextTestSupport {
    protected String driverClass = "org.hsqldb.jdbcDriver";
    protected String url = "jdbc:hsqldb:mem:camel_jdbc";
    protected String user = "sa";
    protected String password = "";
    private DataSource ds;
    private JdbcTemplate jdbcTemplate;

    public void testPreserveHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("foo", "bar");

        template.sendBody("direct:start", "");

        assertMockEndpointsSatisfied();
    }

    protected void setUp() throws Exception {
        Class.forName(driverClass);
        super.setUp();

        jdbcTemplate = new JdbcTemplate(ds);
        jdbcTemplate.execute("create table projects (id integer primary key,"
                             + "project varchar(10), license varchar(5))");
        jdbcTemplate.execute("insert into projects values (1, 'Camel', 'ASF')");
        jdbcTemplate.execute("insert into projects values (2, 'AMQ', 'ASF')");
        jdbcTemplate.execute("insert into projects values (3, 'Linux', 'GPL')");
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
        jdbcTemplate.execute("drop table projects");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                ds = new SingleConnectionDataSource(url, user, password, true);

                getContext().getComponent("sql", SqlComponent.class).setDataSource(ds);

                errorHandler(noErrorHandler());

                from("direct:start")
                    .setHeader("foo", constant("bar"))
                    .to("sql:select * from projects")
                    .to("mock:result");
            }
        };
    }

}