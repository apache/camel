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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24869: a :name in a sql query that is not the camel-sql :#name form goes to the JDBC driver as written and
 * fails at runtime; the validator says how to write it.
 */
class SourceValidatorSqlParametersTest {

    private static final CamelCatalog catalog = new DefaultCamelCatalog();

    @Test
    void aBareNamedParameterUnderParametersSaysToWriteTheCamelForm() {
        String yaml = """
                - route:
                    from:
                      uri: file:orders
                      steps:
                        - to:
                            uri: sql
                            parameters:
                              query: "INSERT INTO customers (id, country) VALUES (:customer, :country)"
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).anyMatch(e -> e.startsWith("Line 8: sql: :customer is not a camel-sql named parameter")
                && e.contains("write :#customer for a header or a key of a Map body, or :#${body[customer]}")
                && e.endsWith("(also :country)"));
    }

    @Test
    void aSimpleLookingNameInTheUriPathGetsTheSimpleForm() {
        String yaml = """
                - route:
                    from:
                      uri: file:orders
                      steps:
                        - to:
                            uri: "sql:INSERT INTO customers (id) VALUES (:body[customer])"
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).anyMatch(e -> e.startsWith("Line 6: sql: :body[customer] is not a camel-sql named parameter")
                && e.contains("write :#${body[customer]} (a Simple expression)"));
    }

    @Test
    void theCamelFormsCastsTimesAndOtherComponentsAreFine() {
        String yaml
                = """
                        - route:
                            from:
                              uri: file:orders
                              steps:
                                - to:
                                    uri: sql
                                    parameters:
                                      query: "MERGE INTO customers USING (VALUES (:#${body[customer]}, :#country)) AS s(id, country) ON customers.id = s.id WHEN MATCHED THEN UPDATE SET seen = '10:30', n = id::int"
                                - to:
                                    uri: sql-stored
                                    parameters:
                                      template: "ADDNUMBERS(INTEGER ${header.a}, OUT INTEGER :#result)"
                                - to:
                                    uri: "log:done?showHeaders=true"
                        """;
        assertThat(SourceValidator.validateYamlEndpoints(yaml, catalog)).noneMatch(e -> e.contains("named parameter"));
    }
}
