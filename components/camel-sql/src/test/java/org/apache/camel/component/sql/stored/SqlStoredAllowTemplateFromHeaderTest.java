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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies the {@code allowTemplateFromHeader} gate on {@code sql-stored}.
 * <p>
 * Both templates call the same {@code SUBNUMBERS} procedure but declare a different OUT-parameter alias, so the key
 * present in the result map tells us unambiguously which template text was executed. The endpoint template aliases the
 * OUT parameter {@code resultfromendpoint}; the {@code CamelSqlStoredTemplate} header aliases it
 * {@code resultfromheader}. This makes both assertions mutation-resistant: with the gate off the header alias must be
 * absent, and with the gate on the endpoint alias must be absent.
 */
public class SqlStoredAllowTemplateFromHeaderTest extends CamelTestSupport {

    private static final String HEADER_TEMPLATE
            = "SUBNUMBERS(INTEGER ${headers.num1},INTEGER ${headers.num2},OUT INTEGER resultfromheader)";

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
        // allowTemplateFromHeader defaults to false: the CamelSqlStoredTemplate header must be ignored and the
        // endpoint-configured template executed instead.
        Map<String, Object> result = execute("direct:gated");

        assertEquals(Integer.valueOf(2), result.get("resultfromendpoint"));
        assertFalse(result.containsKey("resultfromheader"), "the header-supplied template must not have been executed");
    }

    @Test
    public void headerTemplateHonouredWhenAllowed() {
        // With allowTemplateFromHeader=true the CamelSqlStoredTemplate header overrides the endpoint template.
        Map<String, Object> result = execute("direct:allowed");

        assertEquals(Integer.valueOf(2), result.get("resultfromheader"));
        assertFalse(result.containsKey("resultfromendpoint"), "the endpoint-configured template must not have been executed");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> execute(String uri) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("num1", 3);
        headers.put("num2", 1);
        headers.put(SqlStoredConstants.SQL_STORED_TEMPLATE, HEADER_TEMPLATE);
        return template.requestBodyAndHeaders(uri, null, headers, Map.class);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getComponent("sql-stored", SqlStoredComponent.class).setDataSource(db);

                String endpointTemplate
                        = "SUBNUMBERS(INTEGER ${headers.num1},INTEGER ${headers.num2},OUT INTEGER resultfromendpoint)";

                from("direct:gated").to("sql-stored:" + endpointTemplate).to("mock:result");
                from("direct:allowed").to("sql-stored:" + endpointTemplate + "?allowTemplateFromHeader=true")
                        .to("mock:result");
            }
        };
    }
}
