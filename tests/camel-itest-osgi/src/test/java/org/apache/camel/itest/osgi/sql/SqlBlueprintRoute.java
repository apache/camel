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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sql.SqlConstants;
import org.apache.camel.itest.osgi.blueprint.OSGiBlueprintTestSupport;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Customizer;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Constants;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class SqlBlueprintRoute extends OSGiBlueprintTestSupport {


    @Test
    public void testListBody() throws Exception {
        getInstalledBundle("CamelBlueprintSqlTestBundle").start();
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintSqlTestBundle)", 10000);

        MockEndpoint mock = ctx.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);
        List<Object> body = new ArrayList<Object>();
        body.add("ASF");
        body.add("Camel");
        ProducerTemplate template = ctx.createProducerTemplate();
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
        getInstalledBundle("CamelBlueprintSqlTestBundle").start();
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintSqlTestBundle)", 10000);

        try {
            ProducerTemplate template = ctx.createProducerTemplate();
            template.sendBody("direct:list", "ASF");
            fail();
        } catch (RuntimeCamelException e) {
            // should have DataAccessException thrown
            assertTrue("Exception thrown is wrong", e.getCause() instanceof DataAccessException);
        }
    }


    @Test
    public void testInsert() throws Exception {
        getInstalledBundle("CamelBlueprintSqlTestBundle").start();
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintSqlTestBundle)", 10000);
        DataSource ds = getOsgiService(DataSource.class, 10000);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
        ProducerTemplate template = ctx.createProducerTemplate();
        MockEndpoint mock = ctx.getEndpoint("mock:result", MockEndpoint.class);
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
                new Customizer() {
                    @Override
                    public InputStream customizeTestProbe(InputStream testProbe) {
                        return TinyBundles.bundle().read(testProbe)
                                .add("OSGI-INF/blueprint/test.xml", SqlBlueprintRoute.class.getResource("blueprintSqlCamelContext.xml"))
                                .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintSqlTestBundle")
                                .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                                .build();
                    }
                },
                scanFeatures(getKarafFeatureUrl(), "spring"),
                mavenBundle("org.apache.derby", "derby", "10.4.2.0"),
                // using the features to install the camel components
                scanFeatures(getCamelKarafFeatureUrl(),
                        "camel-blueprint", "camel-sql"),
                felix(), equinox());

        return options;
    }
}
