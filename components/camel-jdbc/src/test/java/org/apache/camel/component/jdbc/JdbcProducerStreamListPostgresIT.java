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
package org.apache.camel.component.jdbc;

import java.sql.Connection;
import java.sql.Statement;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.infra.postgres.services.PostgresService;
import org.apache.camel.test.infra.postgres.services.PostgresServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.postgresql.ds.PGSimpleDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code outputType=StreamList} with a fetch size on PostgreSQL: the driver reads the rows through a cursor, a batch of
 * {@code fetchSize} rows at a time, and the cursor lives until the end of the transaction. With
 * {@code resetAutoCommit=true} (the default) the producer must not commit before the route has read the rows.
 */
class JdbcProducerStreamListPostgresIT extends CamelTestSupport {

    private static final int ROWS = 30;

    @RegisterExtension
    static PostgresService postgres = PostgresServiceFactory.createSingletonService();

    @EndpointInject("mock:row")
    private MockEndpoint row;

    private PGSimpleDataSource dataSource;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        // no pool, so autocommit is on (the JDBC default) and the producer turns it off for the statement
        dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.jdbcUrl());
        dataSource.setUser(postgres.userName());
        dataSource.setPassword(postgres.password());
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("drop table if exists stream_items");
            stmt.execute("create table stream_items (id int primary key)");
            stmt.execute("insert into stream_items select generate_series(1, " + ROWS + ")");
        }
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("postgres", dataSource);
    }

    @Test
    void streamListWithFetchSizeReadsEveryRow() throws Exception {
        row.expectedMessageCount(ROWS);

        Exchange out = template.send("direct:stream", e -> e.getMessage().setBody("select id from stream_items order by id"));

        assertNull(out.getException());
        MockEndpoint.assertIsSatisfied(context);
        assertEquals(ROWS, row.getReceivedExchanges().get(ROWS - 1).getMessage().getBody(Integer.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:stream").to("jdbc:postgres?outputType=StreamList&statement.fetchSize=5")
                        .split(body()).streaming()
                            .setBody(simple("${body[id]}"))
                            .to("mock:row")
                        .end();
            }
        };
    }
}
