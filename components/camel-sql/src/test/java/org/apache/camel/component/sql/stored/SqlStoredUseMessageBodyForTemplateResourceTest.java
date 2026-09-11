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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * When {@code useMessageBodyForTemplate=true} the message body is the stored-procedure template text. Because the body
 * is untrusted per-exchange input, it must be used verbatim and never dereferenced as a {@code file:} / {@code http:} /
 * {@code classpath:} resource - only the endpoint-configured template is resolved as a resource (at route start).
 */
public class SqlStoredUseMessageBodyForTemplateResourceTest extends CamelTestSupport {

    private static final String INLINE_TEMPLATE = "SUBNUMBERS(INTEGER :#num1,INTEGER :#num2,OUT INTEGER resultofsum)";

    // Points at a real classpath resource holding a valid template. Before the fix this body would have been loaded
    // and executed; after the fix it is treated as literal (invalid) template text.
    private static final String RESOURCE_BODY = "classpath:sql/bodyTemplateResource.sql";

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
    public void inlineBodyTemplateStillWorks() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:query");
        mock.expectedMessageCount(1);

        Map<String, Object> params = new HashMap<>();
        params.put("num1", 3);
        params.put("num2", 1);

        template.requestBodyAndHeader("direct:query", INLINE_TEMPLATE, SqlStoredConstants.SQL_STORED_PARAMETERS, params);

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(Integer.valueOf(2), mock.getExchanges().get(0).getIn().getBody(Map.class).get("resultofsum"));
    }

    @Test
    public void schemePrefixedBodyIsNotResolvedAsResource() {
        Map<String, Object> params = new HashMap<>();
        params.put("num1", 3);
        params.put("num2", 1);

        // The body is a classpath: URI pointing at a valid template. It must NOT be fetched and executed; instead the
        // literal string is used as the template and fails to parse - which is what proves the resource was not loaded.
        assertThrows(CamelExecutionException.class,
                () -> template.requestBodyAndHeader("direct:query", RESOURCE_BODY, SqlStoredConstants.SQL_STORED_PARAMETERS,
                        params));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getComponent("sql-stored", SqlStoredComponent.class).setDataSource(db);

                from("direct:query").to("sql-stored:query?useMessageBodyForTemplate=true").to("mock:query");
            }
        };
    }
}
