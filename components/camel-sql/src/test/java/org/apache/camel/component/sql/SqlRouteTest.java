/*
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

public class SqlRouteTest extends CamelTestSupport {

    private EmbeddedDatabase db;
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testSimpleBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        template.sendBody("direct:simple", "XXX");
        mock.assertIsSatisfied();
        List<?> received = assertIsInstanceOf(List.class, mock.getReceivedExchanges().get(0).getIn().getBody());
        Map<?, ?> row = assertIsInstanceOf(Map.class, received.get(0));
        assertEquals("Linux", row.get("PROJECT"));
    }

    @Test
    public void testQueryAsHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:simple", "Camel", SqlConstants.SQL_QUERY, "select * from projects where project = ? order by id");
        mock.assertIsSatisfied();
        List<?> received = assertIsInstanceOf(List.class, mock.getReceivedExchanges().get(0).getIn().getBody());
        Map<?, ?> row = assertIsInstanceOf(Map.class, received.get(0));
        assertEquals(1, row.get("id"));
        assertEquals("ASF", row.get("license"));
        mock.reset();

        mock.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:simple", 3, SqlConstants.SQL_QUERY, "select * from projects where id = ? order by id");
        mock.assertIsSatisfied();
        received = assertIsInstanceOf(List.class, mock.getReceivedExchanges().get(0).getIn().getBody());
        row = assertIsInstanceOf(Map.class, received.get(0));
        assertEquals("Linux", row.get("PROJECT"));
        assertEquals("XXX", row.get("license"));
    }

    @Test
    public void testListBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        List<Object> body = new ArrayList<>();
        body.add("ASF");
        body.add("Camel");
        template.sendBody("direct:list", body);
        mock.assertIsSatisfied();
        List<?> received = assertIsInstanceOf(List.class, mock.getReceivedExchanges().get(0).getIn().getBody());
        Map<?, ?> firstRow = assertIsInstanceOf(Map.class, received.get(0));
        assertEquals(1, firstRow.get("ID"));
        
        // unlikely to have accidental ordering with 3 rows x 3 columns
        for (Object obj : received) {
            Map<?, ?> row = assertIsInstanceOf(Map.class, obj);
            assertTrue("not preserving key ordering for a given row keys: " + row.keySet(), isOrdered(row.keySet()));
        }
    }

    @Test
    public void testLowNumberOfParameter() throws Exception {
        try {
            template.sendBody("direct:list", "ASF");
            fail();
        } catch (RuntimeCamelException e) {
            // should have DataAccessException thrown
            assertTrue("Exception thrown is wrong", e.getCause() instanceof DataAccessException);
        }
    }

    @Test
    public void testHighNumberOfParameter() throws Exception {
        try {
            template.sendBody("direct:simple", new Object[] {"ASF", "Foo"});
            fail();
        } catch (RuntimeCamelException e) {
            // should have DataAccessException thrown
            assertTrue("Exception thrown is wrong", e.getCause() instanceof DataAccessException);
        }
    }

    @Test
    public void testListResult() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedHeaderReceived(SqlConstants.SQL_ROW_COUNT, "2");
        mock.expectedMessageCount(1);
        List<Object> body = new ArrayList<>();
        body.add("ASF");
        template.sendBody("direct:simple", body);
        mock.assertIsSatisfied();
        List<?> received = assertIsInstanceOf(List.class, mock.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals(2, received.size());
        Map<?, ?> row1 = assertIsInstanceOf(Map.class, received.get(0));
        assertEquals("Camel", row1.get("PROJECT"));
        Map<?, ?> row2 = assertIsInstanceOf(Map.class, received.get(1));
        assertEquals("AMQ", row2.get("PROJECT"));
    }

    @Test
    public void testListLimitedResult() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        List<Object> body = new ArrayList<>();
        body.add("ASF");
        template.sendBody("direct:simpleLimited", body);
        mock.assertIsSatisfied();
        List<?> received = assertIsInstanceOf(List.class, mock.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals(1, received.size());
        Map<?, ?> row1 = assertIsInstanceOf(Map.class, received.get(0));
        assertEquals("Camel", row1.get("PROJECT"));
    }

    @Test
    public void testInsert() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:insert", new Object[] {10, "test", "test"});
        mock.assertIsSatisfied();
        try {
            String projectName = jdbcTemplate.queryForObject("select project from projects where id = 10", String.class);
            assertEquals("test", projectName);
        } catch (EmptyResultDataAccessException e) {
            fail("no row inserted");
        }

        Integer actualUpdateCount = mock.getExchanges().get(0).getIn().getHeader(SqlConstants.SQL_UPDATE_COUNT, Integer.class);
        assertEquals((Integer) 1, actualUpdateCount);
    }

    @Test
    public void testNoBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        template.sendBody("direct:no-param", null);
        mock.assertIsSatisfied();
        List<?> received = assertIsInstanceOf(List.class, mock.getReceivedExchanges().get(0).getIn().getBody());
        Map<?, ?> row = assertIsInstanceOf(Map.class, received.get(0));
        assertEquals("Camel", row.get("PROJECT"));
    }
    
    @Test
    public void testHashesInQuery() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        template.sendBody("direct:no-param-insert", "XGPL");
        mock.assertIsSatisfied();
        Number received = assertIsInstanceOf(Number.class, mock.getReceivedExchanges().get(0).getIn().getHeader(SqlConstants.SQL_UPDATE_COUNT));
        assertEquals(1, received.intValue());
        Map<?, ?> projectNameInserted = jdbcTemplate.queryForMap("select project, license from projects where id = 5");
        assertEquals("#", projectNameInserted.get("PROJECT"));
        assertEquals("XGPL", projectNameInserted.get("LICENSE"));
    }
    
    @Test
    public void testBodyButNoParams() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        template.sendBody("direct:no-param", "Mock body");
        mock.assertIsSatisfied();
        List<?> received = assertIsInstanceOf(List.class, mock.getReceivedExchanges().get(0).getIn().getBody());
        Map<?, ?> row = assertIsInstanceOf(Map.class, received.get(0));
        assertEquals("Camel", row.get("PROJECT"));
    }

    @Test
    public void testBatch() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        List<?> data = Arrays.asList(Arrays.asList(6, "abc", "def"), Arrays.asList(7, "ghi", "jkl"), Arrays.asList(8, "mno", "pqr"));
        template.sendBody("direct:batch", data);
        mock.assertIsSatisfied();
        Number received = assertIsInstanceOf(Number.class, mock.getReceivedExchanges().get(0).getIn().getHeader(SqlConstants.SQL_UPDATE_COUNT));
        assertEquals(3, received.intValue());
        assertEquals("abc", jdbcTemplate.queryForObject("select project from projects where id = 6", String.class));
        assertEquals("def", jdbcTemplate.queryForObject("select license from projects where id = 6", String.class));
        assertEquals("ghi", jdbcTemplate.queryForObject("select project from projects where id = 7", String.class));
        assertEquals("jkl", jdbcTemplate.queryForObject("select license from projects where id = 7", String.class));
        assertEquals("mno", jdbcTemplate.queryForObject("select project from projects where id = 8", String.class));
        assertEquals("pqr", jdbcTemplate.queryForObject("select license from projects where id = 8", String.class));
    }
    
    @Test
    public void testBatchMissingParamAtEnd() throws Exception {
        try {
            List<?> data = Arrays.asList(Arrays.asList(9, "stu", "vwx"), Arrays.asList(10, "yza"));
            template.sendBody("direct:batch", data);
            fail();
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof UncategorizedSQLException);
        }
        assertEquals(new Integer(0), jdbcTemplate.queryForObject("select count(*) from projects where id = 9", Integer.class));
        assertEquals(new Integer(0), jdbcTemplate.queryForObject("select count(*) from projects where id = 10", Integer.class));
    }
    
    @Test
    public void testBatchMissingParamAtBeginning() throws Exception {
        try {
            List<?> data = Arrays.asList(Arrays.asList(9, "stu"), Arrays.asList(10, "vwx", "yza"));
            template.sendBody("direct:batch", data);
            fail();
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof UncategorizedSQLException);
        }
        assertEquals(new Integer(0), jdbcTemplate.queryForObject("select count(*) from projects where id = 9", Integer.class));
        assertEquals(new Integer(0), jdbcTemplate.queryForObject("select count(*) from projects where id = 10", Integer.class));
    }
    
    @Override
    @Before
    public void setUp() throws Exception {
        db = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.DERBY).addScript("sql/createAndPopulateDatabase.sql").build();
        
        jdbcTemplate = new JdbcTemplate(db);
        
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        
        db.shutdown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                errorHandler(noErrorHandler());
                
                from("direct:simple").to("sql:select * from projects where license = # order by id")
                    .to("mock:result");

                from("direct:list")
                    .to("sql:select * from projects where license = # and project = # order by id")
                    .to("mock:result");

                from("direct:simpleLimited")
                    .to("sql:select * from projects where license = # order by id?template.maxRows=1")
                    .to("mock:result");

                from("direct:insert").to("sql:insert into projects values (#, #, #)").to("mock:result");
                
                from("direct:no-param").to("sql:select * from projects order by id").to("mock:result");
                
                from("direct:no-param-insert").to("sql:insert into projects values (5, '#', param)?placeholder=param").to("mock:result");
                
                from("direct:batch")
                    .to("sql:insert into projects values (#, #, #)?batch=true")
                    .to("mock:result");
            }
        };
    }
    
    private boolean isOrdered(Set<?> keySet) {
        assertTrue("isOrdered() requires the following keys: id, project, license", keySet.contains("id"));
        assertTrue("isOrdered() requires the following keys: id, project, license", keySet.contains("project"));
        assertTrue("isOrdered() requires the following keys: id, project, license", keySet.contains("license"));
        
        // the implementation uses a case insensitive Map
        final Iterator<?> it = keySet.iterator();
        return "id".equalsIgnoreCase(assertIsInstanceOf(String.class, it.next()))
            && "project".equalsIgnoreCase(assertIsInstanceOf(String.class, it.next()))
            && "license".equalsIgnoreCase(assertIsInstanceOf(String.class, it.next()));
    }

}
