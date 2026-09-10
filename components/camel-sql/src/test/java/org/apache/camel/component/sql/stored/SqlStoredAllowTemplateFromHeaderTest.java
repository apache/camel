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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class SqlStoredAllowTemplateFromHeaderTest extends CamelTestSupport {

    private static final String PROC = "SUBNUMBERS(INTEGER :#num1,INTEGER :#num2,OUT INTEGER resultofsum)";

    private EmbeddedDatabase db;

    @Override
    public void doPreSetup() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setName(getClass().getSimpleName())
                .setType(EmbeddedDatabaseType.HSQL)
                .addScript("sql/storedProcedureTest.sql").build();
    }

    @Override
    public void doPostTearDown() throws Exception {
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    public void headerTemplateIgnoredByDefault() {
        // allowTemplateFromHeader defaults to false, so the CamelSqlStoredTemplate header must not override the
        // endpoint-configured template; the (placeholder) endpoint template is used instead and fails to parse,
        // which is what confirms the header was ignored rather than executed.
        Map<String, Object> params = new HashMap<>();
        params.put("num1", 3);
        params.put("num2", 1);
        Map<String, Object> headers = new HashMap<>();
        headers.put(SqlStoredConstants.SQL_STORED_TEMPLATE, PROC);
        headers.put(SqlStoredConstants.SQL_STORED_PARAMETERS, params);

        assertThrows(CamelExecutionException.class,
                () -> template.requestBodyAndHeaders("direct:gated", "unused", headers));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getComponent("sql-stored", SqlStoredComponent.class).setDataSource(db);

                from("direct:gated").to("sql-stored:query").to("mock:result");
            }
        };
    }
}
