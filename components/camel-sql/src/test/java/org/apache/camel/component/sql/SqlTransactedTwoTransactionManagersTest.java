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

import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.NoSuchBeanTypeException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-27021: transacted without a policy uses the transaction manager of the registry when there is exactly one, and
 * makes a local one for the DataSource only when there is none (CAMEL-26851). With two or more, the route does not
 * start.
 */
public class SqlTransactedTwoTransactionManagersTest extends CamelTestSupport {

    private static final String INSERT = "sql:insert into customer values('cust1','cmueller')?dataSource=#testdb";

    private EmbeddedDatabase db;
    private JdbcTemplate jdbc;

    /**
     * Counts the transactions it begins.
     */
    static class CountingTransactionManager extends DataSourceTransactionManager {
        final AtomicInteger begins = new AtomicInteger();

        CountingTransactionManager(DataSource dataSource) {
            super(dataSource);
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            begins.incrementAndGet();
            super.doBegin(transaction, definition);
        }
    }

    @Override
    protected void bindToRegistry(Registry registry) {
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

    private void addInsertThenFailRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("insert")
                        .transacted()
                        .to(INSERT)
                        .throwException(new IllegalStateException("Forced"));
            }
        });
    }

    private void assertInsertRolledBack() {
        Exchange out = template.send("direct:start", e -> e.getIn().setBody("x"));
        assertTrue(out.isFailed());
        assertEquals(0, jdbc.queryForObject("select count(*) from customer", Long.class));
    }

    private void assertFailsWithTwoTransactionManagers() {
        FailedToCreateRouteException e = assertThrows(FailedToCreateRouteException.class, this::addInsertThenFailRoute);
        NoSuchBeanTypeException cause = assertInstanceOf(NoSuchBeanTypeException.class, e.getCause());
        assertTrue(cause.getMessage().startsWith(
                "Found 2 beans for: " + PlatformTransactionManager.class.getName() + " in the registry"),
                cause.getMessage());
        // no local transaction manager is made
        assertNull(context.getRegistry().lookupByName("transactionManager"));
        assertEquals(2, context.getRegistry().findByType(PlatformTransactionManager.class).size());
    }

    @Test
    public void twoTransactionManagersFailTheRoute() {
        context.getRegistry().bind("txA", new CountingTransactionManager(db));
        context.getRegistry().bind("txB", new CountingTransactionManager(db));

        assertFailsWithTwoTransactionManagers();
    }

    @Test
    public void twoTransactionManagersAndTwoDataSourcesFailTheRoute() {
        EmbeddedDatabase db2 = new EmbeddedDatabaseBuilder()
                .setName(getClass().getSimpleName() + "2")
                .setType(EmbeddedDatabaseType.H2).build();
        try {
            context.getRegistry().bind("testdb2", db2);
            context.getRegistry().bind("txA", new CountingTransactionManager(db));
            context.getRegistry().bind("txB", new CountingTransactionManager(db2));

            assertFailsWithTwoTransactionManagers();
        } finally {
            db2.shutdown();
        }
    }

    @Test
    public void twoTransactionManagersAndAPolicyUseThePolicy() throws Exception {
        CountingTransactionManager txA = new CountingTransactionManager(db);
        CountingTransactionManager txB = new CountingTransactionManager(db);
        context.getRegistry().bind("txA", txA);
        context.getRegistry().bind("txB", txB);
        context.getRegistry().bind("PROPAGATION_REQUIRED", new SpringTransactionPolicy(txB));

        addInsertThenFailRoute();

        assertInsertRolledBack();
        assertEquals(0, txA.begins.get());
        assertEquals(1, txB.begins.get());
        assertNull(context.getRegistry().lookupByName("transactionManager"));
    }

    @Test
    public void oneTransactionManagerIsUsed() throws Exception {
        CountingTransactionManager txA = new CountingTransactionManager(db);
        context.getRegistry().bind("txA", txA);

        addInsertThenFailRoute();

        assertInsertRolledBack();
        assertEquals(1, txA.begins.get());
        assertNull(context.getRegistry().lookupByName("transactionManager"));
    }
}
