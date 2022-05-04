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
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertSame;

public class TemplateCacheTest extends CamelTestSupport {

    private EmbeddedDatabase db;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setName(getClass().getSimpleName())
                .setType(EmbeddedDatabaseType.DERBY)
                .addScript("sql/storedProcedureTest.sql").build();
        super.setUp();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    public void shouldCacheTemplateFunctions() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(db);
        CallableStatementWrapperFactory fac
                = new CallableStatementWrapperFactory(jdbcTemplate, new TemplateParser(context.getClassResolver()), false);

        BatchCallableStatementCreatorFactory batchFactory1 = fac.getTemplateForBatch("FOO()");
        BatchCallableStatementCreatorFactory batchFactory2 = fac.getTemplateForBatch("FOO()");
        assertSame(batchFactory1, batchFactory2);

        TemplateStoredProcedure templateStoredProcedure1 = fac.getTemplateStoredProcedure("FOO()");
        TemplateStoredProcedure templateStoredProcedure2 = fac.getTemplateStoredProcedure("FOO()");
        assertSame(templateStoredProcedure1, templateStoredProcedure2);
    }
}
