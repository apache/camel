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
package org.apache.camel.processor.jpa;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import jakarta.persistence.EntityManager;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jpa.JpaConstants;
import org.apache.camel.examples.SendEmail;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JPA producer keeps the EntityManager it creates for an exchange until the exchange completes, so a redelivery of
 * the exchange used the same one. When the JDBC connection of that EntityManager breaks during a send (the database
 * restarts, the network drops), the rollback fails too, and Hibernate keeps the broken connection in the EntityManager:
 * every redelivery then failed although the database was back. The producer now closes the EntityManager it created
 * when a send fails, so that a redelivery gets a new one.
 * <p>
 * Runs with the JPA provider of the maven profile in use. OpenJPA releases the broken connection by itself, so the
 * redelivery only failed with Hibernate ({@code -Dhibernate}); with both, the redelivery must not get the EntityManager
 * of the failed send.
 */
public class JpaProducerConnectionLossTest extends CamelTestSupport {

    private static final String ENDPOINT_URI
            = "jpa://" + SendEmail.class.getName() + "?entityManagerFactory=#entityManagerFactory";

    private final List<Boolean> entityManagerOpenOnRedelivery = new CopyOnWriteArrayList<>();
    private ConnectionBreakingDataSource dataSource;
    private LocalContainerEntityManagerFactoryBean entityManagerFactory;

    @Override
    protected void doPreSetup() {
        dataSource = new ConnectionBreakingDataSource(
                "jdbc:h2:mem:jpa-connection-loss-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        // the camel persistence unit of META-INF/persistence.xml, with the JPA provider of the maven profile in use
        entityManagerFactory = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactory.setPersistenceUnitName("camel");
        entityManagerFactory.setDataSource(dataSource);
        entityManagerFactory.afterPropertiesSet();
    }

    @Override
    protected void doPostTearDown() throws Exception {
        entityManagerFactory.destroy();
        dataSource.execute("shutdown");
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("entityManagerFactory", entityManagerFactory.getObject());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the database is back right after the connections broke, so redeliveries without delay are enough
                errorHandler(defaultErrorHandler().maximumRedeliveries(3).redeliveryDelay(0)
                        .onRedelivery(JpaProducerConnectionLossTest.this::recordEntityManagerOnRedelivery));

                from("direct:start")
                        .to(ENDPOINT_URI);

                from("direct:passedIn")
                        .errorHandler(noErrorHandler())
                        .to(ENDPOINT_URI + "&usePassedInEntityManager=true");
            }
        };
    }

    @Test
    public void testRedeliveryAfterConnectionLoss() throws Exception {
        dataSource.breakConnectionsOnNextInsert();

        Exchange out = template.send("direct:start", e -> e.getIn().setBody(new SendEmail("foo@beer.org")));

        assertEquals(1, dataSource.getBreaks(), "The connections should break while the entity is inserted");
        assertNull(out.getException(), "A redelivery should persist the entity once the database is back");
        assertEquals(List.of("foo@beer.org"), dataSource.selectAddresses());
        assertEquals(List.of(false), entityManagerOpenOnRedelivery,
                "The EntityManager of the failed send should be closed before the only redelivery, which then gets a new one");
    }

    @Test
    public void testPassedInEntityManagerIsNotClosedOnFailure() {
        EntityManager entityManager = entityManagerFactory.getObject().createEntityManager();
        try {
            dataSource.breakConnectionsOnNextInsert();

            Exchange out = template.send("direct:passedIn", e -> {
                e.getIn().setBody(new SendEmail("foo@beer.org"));
                e.getIn().setHeader(JpaConstants.ENTITY_MANAGER, entityManager);
            });

            assertEquals(1, dataSource.getBreaks(), "The connections should break while the entity is inserted");
            assertNotNull(out.getException(), "The exchange should fail");
            assertTrue(entityManager.isOpen(), "The EntityManager passed in the header should be left to its owner");
        } finally {
            entityManager.close();
        }
    }

    @SuppressWarnings("unchecked")
    private void recordEntityManagerOnRedelivery(Exchange exchange) {
        Map<String, EntityManager> entityManagers = exchange.getProperty(JpaConstants.ENTITY_MANAGER, Map.class);
        for (EntityManager entityManager : entityManagers.values()) {
            entityManagerOpenOnRedelivery.add(entityManager.isOpen());
        }
    }

    /**
     * An H2 database whose open connections all break while an entity is inserted, as when the database restarts or the
     * network drops. New connections work again right away.
     */
    private static final class ConnectionBreakingDataSource implements DataSource {

        private final JdbcDataSource h2 = new JdbcDataSource();
        private final Set<Connection> open = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean breakOnNextInsert = new AtomicBoolean();
        private final AtomicInteger breaks = new AtomicInteger();

        ConnectionBreakingDataSource(String url) {
            h2.setURL(url);
        }

        void breakConnectionsOnNextInsert() {
            breakOnNextInsert.set(true);
        }

        int getBreaks() {
            return breaks.get();
        }

        void execute(String sql) throws SQLException {
            try (Connection connection = h2.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }

        List<String> selectAddresses() throws SQLException {
            List<String> addresses = new ArrayList<>();
            try (Connection connection = h2.getConnection(); Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("select address from SendEmail order by id")) {
                while (rs.next()) {
                    addresses.add(rs.getString(1));
                }
            }
            return addresses;
        }

        private void breakOpenConnections() {
            breaks.incrementAndGet();
            for (Connection connection : open) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    // already closed
                }
            }
            open.clear();
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection physical = h2.getConnection();
            open.add(physical);
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Connection.class },
                    (proxy, method, args) -> {
                        if ("prepareStatement".equals(method.getName()) && args[0] instanceof String sql
                                && sql.toLowerCase(Locale.ROOT).startsWith("insert into sendemail")
                                && breakOnNextInsert.compareAndSet(true, false)) {
                            breakOpenConnections();
                        } else if ("close".equals(method.getName())) {
                            open.remove(physical);
                        }
                        try {
                            return method.invoke(physical, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            // not used
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // not used
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
