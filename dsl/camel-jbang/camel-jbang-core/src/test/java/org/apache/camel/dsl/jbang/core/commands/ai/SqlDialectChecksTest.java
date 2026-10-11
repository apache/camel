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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25557: an insert-or-update in the dialect of another database than the datasource, and the row being inserted
 * written as a named parameter, as the local model wrote them for the service-sql example (a PostgreSQL datasource).
 */
class SqlDialectChecksTest {

    private static final String CREATE_TABLE
            = """
                    - route:
                        id: create-table
                        from:
                          uri: timer:setup?repeatCount=1
                          steps:
                            - to:
                                uri: sql
                                parameters:
                                  query: "CREATE TABLE IF NOT EXISTS customers (id varchar(10) PRIMARY KEY, country varchar(2), orders integer)"
                    """;

    private static List<String> validate(Path dir, String yaml) {
        return SourceValidator.validate("sql.camel.yaml", yaml, new DefaultCamelCatalog(), null, dir);
    }

    @Test
    void mysqlUpsertOnPostgres(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("application.properties"),
                "spring.datasource.url=jdbc:postgresql://localhost:5432/test\n");
        List<String> msgs = validate(dir,
                CREATE_TABLE + """
                        - route:
                            id: register-customers
                            from:
                              uri: timer:orders?repeatCount=1
                              steps:
                                - to:
                                    uri: "sql:INSERT INTO customers (id, country, orders) VALUES (:#customer, :#country, 1) ON DUPLICATE KEY UPDATE orders = orders + 1, country = VALUES(country)"
                        """);
        assertThat(msgs).singleElement().satisfies(m -> assertThat(m)
                .startsWith("Line 16: sql: ON DUPLICATE KEY UPDATE is MySQL, and the datasource of application.properties"
                            + " is PostgreSQL")
                .contains("write INSERT INTO customers (id, country, orders) VALUES (:#customer, :#country, 1) ON CONFLICT"
                          + " (id) DO UPDATE SET orders = customers.orders + 1, country = EXCLUDED.country"));
    }

    @Test
    void postgresUpsertOnMysql(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("application.properties"), "spring.datasource.url=jdbc:mysql://localhost:3306/test\n");
        List<String> msgs = validate(dir,
                """
                        - route:
                            from:
                              uri: timer:orders?repeatCount=1
                              steps:
                                - to:
                                    uri: sql
                                    parameters:
                                      query: "INSERT INTO customers (id, orders) VALUES (:#customer, 1) ON CONFLICT (id) DO UPDATE SET orders = customers.orders + 1"
                        """);
        assertThat(msgs).singleElement().satisfies(m -> assertThat(m)
                .contains("ON CONFLICT ... DO UPDATE is PostgreSQL, and the datasource of application.properties is MySQL")
                .contains("write INSERT INTO customers (id, orders) VALUES (:#customer, 1) ON DUPLICATE KEY UPDATE orders ="
                          + " orders + 1"));
    }

    @Test
    void theRightDialectOrNoDatasourceSaysNothing(@TempDir Path dir) throws IOException {
        String upsert
                = """
                        - route:
                            from:
                              uri: timer:orders?repeatCount=1
                              steps:
                                - to:
                                    uri: "sql:INSERT INTO customers (id, orders) VALUES (:#customer, 1) ON CONFLICT (id) DO UPDATE SET orders = customers.orders + 1"
                        """;
        assertThat(validate(dir, upsert)).isEmpty();
        Files.writeString(dir.resolve("application.properties"), "spring.datasource.url=jdbc:postgresql://db/test\n");
        assertThat(validate(dir, upsert)).isEmpty();
    }

    @Test
    void theRowBeingInsertedIsNotANamedParameter() {
        List<String> msgs = SourceValidator.validate("sql.camel.yaml",
                """
                        - route:
                            from:
                              uri: timer:orders?repeatCount=1
                              steps:
                                - to:
                                    uri: "sql:INSERT INTO customers (id, country) VALUES (:#customer, :#country) ON CONFLICT (id) DO UPDATE SET country = :#excluded.country"
                        """,
                new DefaultCamelCatalog(), null);
        assertThat(msgs).anySatisfy(m -> assertThat(m)
                .contains("sql: :#excluded.country is a named parameter")
                .contains("write EXCLUDED.country without :#"));
    }
}
