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

import org.apache.camel.CamelContext;
import org.apache.camel.component.sql.stored.SqlStoredComponent;
import org.apache.camel.component.sql.stored.SqlStoredEndpoint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootApplication
@DirtiesContext
@ContextConfiguration(classes = DataSourceAutoConfigurationTest.class)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:dummy://localhost/test",
        "spring.datasource.username=dbuser",
        "spring.datasource.password=dbpass",
        "spring.datasource.driver-class-name=org.apache.camel.component.sql.support.DummyJDBCDriver"
})
public class DataSourceAutoConfigurationTest {

    @Autowired
    private DataSource datasource;

    @Autowired
    private CamelContext context;

    @Test
    public void testInjectionWorks() {
        assertNotNull(datasource);
    }

    @Test
    public void testSqlComponentUsesTheConfiguredDatasource() throws Exception {
        SqlComponent component = (SqlComponent) context.getComponent("sql");
        SqlEndpoint endpoint = (SqlEndpoint) component.createEndpoint("sql:select * from table where id=#");
        assertEquals(datasource, endpoint.getJdbcTemplate().getDataSource());
    }

    @Test
    public void testSqlStoredComponentUsesTheConfiguredDatasource() throws Exception {
        SqlStoredComponent component = (SqlStoredComponent) context.getComponent("sql-stored");
        SqlStoredEndpoint endpoint = (SqlStoredEndpoint) component.createEndpoint("sql:select * from table where id=#");
        assertEquals(datasource, endpoint.getJdbcTemplate().getDataSource());
    }

}
