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
package org.apache.camel.component.sql.stored;

import org.apache.camel.component.sql.stored.template.TemplateParser;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

public class TemplateCacheTest extends CamelTestSupport {

    private EmbeddedDatabase db;

    @Override
    @Before
    public void setUp() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.DERBY).addScript("sql/storedProcedureTest.sql").build();
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        db.shutdown();
    }

    @Test
    public void shouldCacheTemplateFunctions() throws InterruptedException {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(db);
        CallableStatementWrapperFactory fac = new CallableStatementWrapperFactory(jdbcTemplate, new TemplateParser(context.getClassResolver()), false);

        BatchCallableStatementCreatorFactory batchFactory1 = fac.getTemplateForBatch("FOO()");
        BatchCallableStatementCreatorFactory batchFactory2 = fac.getTemplateForBatch("FOO()");
        assertSame(batchFactory1, batchFactory2);

        TemplateStoredProcedure templateStoredProcedure1 = fac.getTemplateStoredProcedure("FOO()");
        TemplateStoredProcedure templateStoredProcedure2 = fac.getTemplateStoredProcedure("FOO()");
        assertSame(templateStoredProcedure1, templateStoredProcedure2);
    }
}
