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
package org.apache.camel.component.sql.stored;

import org.apache.camel.Exchange;
import org.apache.camel.component.sql.stored.template.TemplateStoredProcedure;
import org.apache.camel.component.sql.stored.template.TemplateStoredProcedureFactory;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

public class TemplateStoredProcedureTest extends CamelTestSupport {

    private TemplateStoredProcedureFactory parser;
    private EmbeddedDatabase db;
    private JdbcTemplate jdbcTemplate;

    @Before
    public void setUp() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.DERBY).addScript("sql/storedProcedureTest.sql").build();
        jdbcTemplate = new JdbcTemplate(db);
        parser = new TemplateStoredProcedureFactory(jdbcTemplate);
        super.setUp();
    }


    @Test
    public void shouldExecuteStoredProcedure() {
        TemplateStoredProcedure sp = new TemplateStoredProcedure(jdbcTemplate, parser.parseTemplate("ADDNUMBERS" +
                "(INTEGER ${header.v1},INTEGER ${header.v2},OUT INTEGER resultofsum)"));

        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader("v1", 1);
        exchange.getIn().setHeader("v2", 2);

        sp.execute(exchange);

        Assert.assertEquals(Integer.valueOf(3), exchange.getOut().getHeader("resultofsum"));
    }

    @Test
    public void shouldExecuteNilacidProcedure() {
        TemplateStoredProcedure sp = new TemplateStoredProcedure(jdbcTemplate, parser.parseTemplate("NILADIC()"));

        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader("v1", 1);
        exchange.getIn().setHeader("v2", 2);

        sp.execute(exchange);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        db.shutdown();
    }

}
