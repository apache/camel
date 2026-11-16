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
package org.apache.camel.component.jpa;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.postgres.services.PostgresService;
import org.apache.camel.test.infra.postgres.services.PostgresServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * A row lock held by a transaction that does not end, such as the transaction of a connection lost without the database
 * noticing, makes the lock of the consumer wait: PostgreSQL has no lock timeout by default. With lockTimeout the
 * database cancels the wait, so the poll gives up instead of waiting, and leaves no session waiting for the lock. A
 * socket timeout of the JDBC driver would end the poll too, but its session would go on waiting for the lock.
 */
class JpaConsumerLockTimeoutIT extends CamelTestSupport {

    @RegisterExtension
    static PostgresService service = PostgresServiceFactory.createSingletonService();

    private LocalContainerEntityManagerFactoryBean entityManagerFactoryBean;
    private EntityManagerFactory entityManagerFactory;

    /**
     * Mapped in lock-timeout-item-orm.xml: an annotated entity would be picked up by the OpenJPA persistence units of
     * the other tests, which scan the test classes.
     */
    public static class LockTimeoutItem {
        private Long id;

        public LockTimeoutItem() {
        }

        public LockTimeoutItem(Long id) {
            this.id = id;
        }

        public Long getId() {
            return id;
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        // Hibernate, set up the way Spring Boot does it
        entityManagerFactoryBean = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactoryBean.setDataSource(
                new DriverManagerDataSource(service.jdbcUrl(), service.userName(), service.password()));
        entityManagerFactoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        entityManagerFactoryBean.setManagedTypes(PersistenceManagedTypes.of(LockTimeoutItem.class.getName()));
        entityManagerFactoryBean.setMappingResources("org/apache/camel/component/jpa/lock-timeout-item-orm.xml");
        entityManagerFactoryBean.setPersistenceUnitName("lockTimeout");
        entityManagerFactoryBean.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
        entityManagerFactoryBean.afterPropertiesSet();
        entityManagerFactory = entityManagerFactoryBean.getObject();

        CamelContext context = super.createCamelContext();
        JpaComponent jpa = new JpaComponent();
        jpa.setEntityManagerFactory(entityManagerFactory);
        context.addComponent("jpa", jpa);
        return context;
    }

    @Override
    protected void doPostTearDown() throws Exception {
        if (entityManagerFactoryBean != null) {
            entityManagerFactoryBean.destroy();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jpa://" + LockTimeoutItem.class.getName()
                     + "?query=select x from LockTimeoutItem x order by x.id&lockTimeout=1000&startScheduler=false")
                        .routeId("consumer")
                        .to("mock:result");
            }
        };
    }

    @Test
    void pollGivesUpOnALockedEntityInsteadOfWaiting() throws Exception {
        insertItems(1L, 2L, 3L);
        MockEndpoint mock = getMockEndpoint("mock:result");
        JpaConsumer consumer = (JpaConsumer) context.getRoute("consumer").getConsumer();

        try (Connection other = openConnection()) {
            // a transaction that locks the first entity and does not end
            other.setAutoCommit(false);
            try (Statement st = other.createStatement()) {
                st.execute("select id from lock_timeout_item where id = 1 for update");
            }

            // without a lock timeout, the poll would wait until that transaction ends
            assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
                consumer.poll();
            }, "the poll waited for the lock");
            assertEquals(0, sessionsWaitingForALock(), "the database cancelled the wait of the poll");
            // the failed lock rolled back the transaction of the poll (PostgreSQL), so the poll consumed nothing
            assertEquals(0, mock.getReceivedCounter());
            assertEquals(3, countItems());

            // the transaction ends, as idle_in_transaction_session_timeout of PostgreSQL would end it
            other.rollback();
        }

        // the next poll consumes every entity
        mock.expectedMessageCount(3);
        assertEquals(3, consumer.poll());
        mock.assertIsSatisfied();
        assertEquals(0, countItems());
    }

    private void insertItems(Long... ids) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            for (Long id : ids) {
                em.persist(new LockTimeoutItem(id));
            }
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(service.jdbcUrl(), service.userName(), service.password());
    }

    private static int countItems() throws SQLException {
        return queryInt("select count(*) from lock_timeout_item");
    }

    private static int sessionsWaitingForALock() throws SQLException {
        return queryInt("select count(*) from pg_stat_activity where wait_event_type = 'Lock'");
    }

    private static int queryInt(String sql) throws SQLException {
        try (Connection connection = openConnection();
             Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
