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

import java.util.Map;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * @version 
 */
public class SqlTransactedRouteTest extends CamelTestSupport {
    
    protected DataSource ds;
    protected JdbcTemplate jdbc;
    
    private String driverClass = "org.hsqldb.jdbcDriver";
    private String url = "jdbc:hsqldb:mem:camel_jdbc";
    private String user = "sa";
    private String password = "";
    
    private String startEndpoint = "direct:start";
    private String sqlEndpoint = "sql:overriddenByTheHeader?dataSourceRef=testdb";
    
    @Before
    public void setUp() throws Exception {
        super.setUp();
        
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE CUSTOMER (ID VARCHAR(15) NOT NULL PRIMARY KEY, NAME VARCHAR(100))");
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry reg = super.createRegistry();
        
        ds = new SingleConnectionDataSource(url, user, password, true);
        ((DriverManagerDataSource) ds).setDriverClassName(driverClass);
        reg.bind("testdb", ds);
        
        DataSourceTransactionManager txMgr = new DataSourceTransactionManager();
        txMgr.setDataSource(ds);
        reg.bind("txManager", txMgr);
        
        SpringTransactionPolicy txPolicy = new SpringTransactionPolicy();
        txPolicy.setTransactionManager(txMgr);
        txPolicy.setPropagationBehaviorName("PROPAGATION_REQUIRED");
        reg.bind("required", txPolicy);
        
        return reg;
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("drop table customer");
    }
    
    @Test
    public void testCommit() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start")
                    .transacted("required")
                    .to(sqlEndpoint)
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            exchange.getIn().setHeader(SqlConstants.SQL_QUERY, "insert into customer values('cust2','muellerc')");
                        }
                    })
                    .to(sqlEndpoint);
            }
        });
        
        Exchange exchange = template.send(startEndpoint, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SqlConstants.SQL_QUERY, "insert into customer values('cust1','cmueller')");
            }
        });
        
        assertFalse(exchange.isFailed());
        
        long count = jdbc.queryForLong("select count(*) from customer");
        assertEquals(2, count);

        Map<String, Object> map = jdbc.queryForMap("select * from customer where id = 'cust1'");
        assertEquals(2, map.size());
        assertEquals("cust1", map.get("ID"));
        assertEquals("cmueller", map.get("NAME"));
        
        map = jdbc.queryForMap("select * from customer where id = 'cust2'");
        assertEquals(2, map.size());
        assertEquals("cust2", map.get("ID"));
        assertEquals("muellerc", map.get("NAME"));
    }
    
    @Test
    public void testRollbackAfterExceptionInSecondStatement() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start")
                    .transacted("required")
                    .to(sqlEndpoint)
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            // primary key violation
                            exchange.getIn().setHeader(SqlConstants.SQL_QUERY, "insert into customer values('cust1','muellerc')");
                        }
                    })
                    .to(sqlEndpoint);
            }
        });
        
        Exchange exchange = template.send(startEndpoint, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SqlConstants.SQL_QUERY, "insert into customer values('cust1','cmueller')");
            }
        });
        
        assertTrue(exchange.isFailed());
        
        long count = jdbc.queryForLong("select count(*) from customer");
        assertEquals(0, count);
    }

    @Test
    public void testRollbackAfterAnException() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start")
                    .transacted("required")
                    .to(sqlEndpoint)
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            throw new Exception("forced Exception");
                        }
                    });
            }
        });
        
        Exchange exchange = template.send(startEndpoint, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SqlConstants.SQL_QUERY, "insert into customer values('cust1','cmueller')");
            }
        });
        
        assertTrue(exchange.isFailed());

        long count = jdbc.queryForLong("select count(*) from customer");
        assertEquals(0, count);
    }
}