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
package org.apache.camel.component.sql;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.support.JdbcTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-26851: transacted without a transaction manager or a policy in the registry, as with Camel Main and camel run,
 * uses a local transaction manager for the one DataSource of the application.
 */
public class SqlTransactedLocalTransactionManagerTest extends CamelTestSupport {

    private static final String SQL = "sql:overriddenByTheHeader?dataSource=#testdb&allowQueryFromHeader=true";

    private EmbeddedDatabase db;
    private JdbcTemplate jdbc;

    @Override
    protected void bindToRegistry(Registry registry) {
        // only the DataSource: no transaction manager and no transacted policy
        db = new EmbeddedDatabaseBuilder()
                .setName(getClass().getSimpleName())
                .setType(EmbeddedDatabaseType.H2).build();
        registry.bind("testdb", db);
    }

    @Override
    public void doPostSetup() {
        jdbc = new JdbcTemplate(db);
        jdbc.execute("CREATE TABLE CUSTOMER (ID VARCHAR(15) NOT NULL PRIMARY KEY, NAME VARCHAR(100))");
    }

    @Override
    public void doPostTearDown() {
        if (db != null) {
            db.shutdown();
        }
    }

    private void insertTwice(String routeId, String second) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:" + routeId).routeId(routeId)
                        .transacted()
                        .to(SQL)
                        .setHeader(SqlConstants.SQL_QUERY, constant(second))
                        .to(SQL);
            }
        });
    }

    private Exchange send(String routeId) {
        return template.send("direct:" + routeId,
                e -> e.getIn().setHeader(SqlConstants.SQL_QUERY, "insert into customer values('cust1','cmueller')"));
    }

    @Test
    public void bothInsertsCommit() throws Exception {
        insertTwice("commit", "insert into customer values('cust2','muellerc')");

        assertFalse(send("commit").isFailed());
        assertEquals(2, jdbc.queryForObject("select count(*) from customer", Long.class));
        // the local transaction manager is bound, so other transacted routes use the same one
        Object tm = context.getRegistry().lookupByName("transactionManager");
        JdbcTransactionManager local = assertInstanceOf(JdbcTransactionManager.class, tm);
        assertSame(db, local.getDataSource());
    }

    @Test
    public void aFailingSecondInsertRollsBackTheFirst() throws Exception {
        // primary key violation
        insertTwice("rollback", "insert into customer values('cust1','muellerc')");

        assertTrue(send("rollback").isFailed());
        assertEquals(0, jdbc.queryForObject("select count(*) from customer", Long.class));
    }
}
