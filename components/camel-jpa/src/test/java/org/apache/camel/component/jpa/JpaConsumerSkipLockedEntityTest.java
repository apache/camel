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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * skipLockedEntity with Hibernate locks with SKIP LOCKED, on H2 here. JpaConsumerSkipLockedEntityIT shows the case of
 * PostgreSQL, where a failed lock aborts the transaction.
 */
class JpaConsumerSkipLockedEntityTest extends CamelTestSupport {

    private static final String URL = "jdbc:h2:mem:skipLockedEntity;DB_CLOSE_DELAY=-1";
    private static final Map<String, Object> NOWAIT = Map.of("jakarta.persistence.lock.timeout", 0);

    private LocalContainerEntityManagerFactoryBean entityManagerFactoryBean;
    private EntityManagerFactory entityManagerFactory;
    private final List<Long> routed = new ArrayList<>();
    private LongConsumer whenRouted = id -> {
    };

    @Override
    protected CamelContext createCamelContext() throws Exception {
        entityManagerFactoryBean = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactoryBean.setDataSource(new DriverManagerDataSource(URL, "sa", ""));
        entityManagerFactoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        entityManagerFactoryBean.setManagedTypes(
                PersistenceManagedTypes.of(SkipLockedItem.class.getName(), SkipLockedVersionedItem.class.getName()));
        entityManagerFactoryBean.setMappingResources("org/apache/camel/component/jpa/skip-locked-item-orm.xml");
        entityManagerFactoryBean.setPersistenceUnitName("skipLockedEntity");
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
                from("jpa://" + SkipLockedItem.class.getName()
                     + "?query=select x from SkipLockedItem x order by x.id&skipLockedEntity=true&startScheduler=false")
                        .routeId("consumer")
                        .process(exchange -> {
                            long id = exchange.getMessage().getBody(SkipLockedItem.class).getId();
                            routed.add(id);
                            whenRouted.accept(id);
                        });

                from("jpa://" + SkipLockedVersionedItem.class.getName()
                     + "?query=select x from SkipLockedVersionedItem x order by x.id&skipLockedEntity=true"
                     + "&lockModeType=PESSIMISTIC_FORCE_INCREMENT&consumeDelete=false&startScheduler=false")
                        .routeId("versionedConsumer")
                        .process(exchange -> routed
                                .add(exchange.getMessage().getBody(SkipLockedVersionedItem.class).getId()));
            }
        };
    }

    @Test
    void lockedEntityIsSkipped() throws Exception {
        insertItems(1L, 2L, 3L);
        JpaConsumer consumer = jpaConsumer();

        try (Connection other = openConnection()) {
            // another consumer holds the lock of the second entity
            other.setAutoCommit(false);
            try (Statement st = other.createStatement()) {
                st.execute("select id from skip_locked_item where id = 2 for update");
            }

            consumer.poll();
            assertEquals(List.of(1L, 3L), routed);
            assertEquals(List.of(2L), itemIds());

            other.rollback();
        }

        consumer.poll();
        assertEquals(List.of(1L, 3L, 2L), routed);
        assertEquals(List.of(), itemIds());
    }

    @Test
    void forceIncrementSkipsLockedEntityAndIncrementsVersion() throws Exception {
        // PESSIMISTIC_FORCE_INCREMENT selects the row with SKIP LOCKED as PESSIMISTIC_WRITE does, and the lock that
        // follows increments the version
        insertVersionedItems(1L, 2L, 3L);
        JpaConsumer consumer = (JpaConsumer) context.getRoute("versionedConsumer").getConsumer();

        try (Connection other = openConnection()) {
            // another consumer holds the lock of the second entity
            other.setAutoCommit(false);
            try (Statement st = other.createStatement()) {
                st.execute("select id from skip_locked_versioned_item where id = 2 for update");
            }

            consumer.poll();
            other.rollback();
        }

        assertEquals(List.of(1L, 3L), routed);
        // consumeDelete=false keeps the entities: the routed ones are at version 1, the skipped one is still at 0
        assertEquals(Map.of(1L, 1L, 2L, 0L, 3L, 1L), versions());
    }

    @Test
    void entityDeletedByAnotherConsumerIsSkipped() throws Exception {
        insertItems(1L, 2L, 3L);
        // while the poll routes the first entity, another consumer deletes the second one, which the poll has listed
        whenRouted = id -> {
            if (id == 1L) {
                deleteItem(2L);
            }
        };

        jpaConsumer().poll();
        assertEquals(List.of(), itemIds());
        assertEquals(List.of(1L, 3L), routed);
    }

    @Test
    void optimisticLockModeLocksWithNowait() throws Exception {
        // an optimistic lock mode locks no row, so there is no row to skip
        SkipLockedItem item = new SkipLockedItem(1L);
        EntityManager em = entityManager(mock(Session.class));
        JpaConsumer consumer = jpaConsumer();
        consumer.setLockModeType(LockModeType.OPTIMISTIC);

        assertTrue(consumer.lockEntity(item, em));
        verify(em).lock(item, LockModeType.OPTIMISTIC, NOWAIT);
        verify(em, never()).getCriteriaBuilder();
    }

    @Test
    void otherJpaProvidersLockWithNowait() throws Exception {
        // the JPA specification has no SKIP LOCKED: other providers reject the lock timeout of Hibernate for it (OpenJPA),
        // or would wait for the lock
        SkipLockedItem item = new SkipLockedItem(1L);
        EntityManager em = entityManager(new Object());

        assertTrue(jpaConsumer().lockEntity(item, em));
        verify(em).lock(item, LockModeType.PESSIMISTIC_WRITE, NOWAIT);
        verify(em, never()).getCriteriaBuilder();
    }

    @Test
    void providerCheckThatFailsIsCheckedAgain() throws Exception {
        // the check of the JPA provider can fail, such as on a closed EntityManager: the lock is NOWAIT that time, and
        // the next lock checks again, instead of keeping NOWAIT for the life of the consumer
        insertItems(1L);
        EntityManager real = entityManagerFactory.createEntityManager();
        try {
            real.getTransaction().begin();
            SkipLockedItem item = real.find(SkipLockedItem.class, 1L);
            EntityManager em = mock(EntityManager.class, delegatesTo(real));
            doThrow(new IllegalStateException("EntityManager is closed")).doAnswer(invocation -> real.getDelegate())
                    .when(em).getDelegate();
            JpaConsumer consumer = jpaConsumer();

            assertTrue(consumer.lockEntity(item, em));
            verify(em).lock(item, LockModeType.PESSIMISTIC_WRITE, NOWAIT);
            verify(em, never()).getCriteriaBuilder();

            // Hibernate this time: SKIP LOCKED, and the answer is kept
            assertTrue(consumer.lockEntity(item, em));
            assertTrue(consumer.lockEntity(item, em));
            verify(em, times(2)).getCriteriaBuilder();
            verify(em, times(2)).getDelegate();
        } finally {
            if (real.getTransaction().isActive()) {
                real.getTransaction().rollback();
            }
            real.close();
        }
    }

    private JpaConsumer jpaConsumer() {
        return (JpaConsumer) context.getRoute("consumer").getConsumer();
    }

    private EntityManager entityManager(Object delegate) {
        EntityManager em = mock(EntityManager.class);
        when(em.getMetamodel()).thenReturn(entityManagerFactory.getMetamodel());
        when(em.getDelegate()).thenReturn(delegate);
        return em;
    }

    private void insertItems(Long... ids) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            for (Long id : ids) {
                em.persist(new SkipLockedItem(id));
            }
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    private void insertVersionedItems(Long... ids) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            for (Long id : ids) {
                em.persist(new SkipLockedVersionedItem(id));
            }
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    private static Map<Long, Long> versions() throws SQLException {
        Map<Long, Long> versions = new HashMap<>();
        try (Connection connection = openConnection();
             Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("select id, version from skip_locked_versioned_item")) {
            while (rs.next()) {
                versions.put(rs.getLong(1), rs.getLong(2));
            }
        }
        return versions;
    }

    private static void deleteItem(long id) {
        try (Connection connection = openConnection();
             Statement st = connection.createStatement()) {
            st.execute("delete from skip_locked_item where id = " + id);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(URL, "sa", "");
    }

    private static List<Long> itemIds() throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Connection connection = openConnection();
             Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("select id from skip_locked_item order by id")) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        }
        return ids;
    }
}
