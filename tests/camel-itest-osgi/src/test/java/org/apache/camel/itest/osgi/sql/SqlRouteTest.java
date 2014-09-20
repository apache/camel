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

package org.apache.camel.itest.osgi.sql;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sql.SqlConstants;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.util.IOHelper;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;


import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class SqlRouteTest extends OSGiIntegrationTestSupport {

    String driverClass = "org.apache.derby.jdbc.EmbeddedDriver";
    OsgiBundleXmlApplicationContext applicationContext;

    private DataSource ds;
    private JdbcTemplate jdbcTemplate;

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        IOHelper.close(applicationContext);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        setThreadContextClassLoader();
        applicationContext = new OsgiBundleXmlApplicationContext(
                new String[]{"org/apache/camel/itest/osgi/sql/springSqlRouteContext.xml"});
        if (bundleContext != null) {
            applicationContext.setBundleContext(bundleContext);
            applicationContext.refresh();
        }
        ds = applicationContext.getBean("dataSource", DataSource.class);
        jdbcTemplate = new JdbcTemplate(ds);
        return SpringCamelContext.springCamelContext(applicationContext);
    }

    @Test
    public void testListBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        List<Object> body = new ArrayList<Object>();
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
    public void testInsert() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:insert", new Object[]{10, "test", "test"});
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

    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(
                getDefaultCamelKarafOptions(),
                // using the features to install the camel components
                loadCamelFeatures("camel-sql"),

                // and use derby as the database
                mavenBundle().groupId("org.apache.derby").artifactId("derby").version("10.4.2.0"));

        return options;
    }
}
