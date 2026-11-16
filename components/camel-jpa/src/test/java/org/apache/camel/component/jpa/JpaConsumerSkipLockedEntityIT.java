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
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;

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

/**
 * With skipLockedEntity, competing consumers skip the entities that the others hold. On PostgreSQL a failed lock aborts
 * the transaction, so a NOWAIT lock cannot skip an entity: the poll would be rolled back, and the entities it routed
 * before the locked one routed again by the next poll. With Hibernate the consumer locks with SKIP LOCKED instead.
 */
class JpaConsumerSkipLockedEntityIT extends CamelTestSupport {

    @RegisterExtension
    static PostgresService service = PostgresServiceFactory.createSingletonService();

    private LocalContainerEntityManagerFactoryBean entityManagerFactoryBean;
    private EntityManagerFactory entityManagerFactory;
    private final List<Long> routed = new ArrayList<>();
    private LongConsumer whenRouted = id -> {
    };

    @Override
    protected CamelContext createCamelContext() throws Exception {
        // Hibernate, set up the way Spring Boot does it
        entityManagerFactoryBean = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactoryBean.setDataSource(
                new DriverManagerDataSource(service.jdbcUrl(), service.userName(), service.password()));
        entityManagerFactoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        entityManagerFactoryBean.setManagedTypes(PersistenceManagedTypes.of(SkipLockedItem.class.getName()));
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
                        })
                        .to("mock:result");
                from("jpa://" + SkipLockedItem.class.getName()
                     + "?query=select x from SkipLockedItem x order by x.id&skipLockedEntity=true&startScheduler=false"
                     + "&lockModeType=PESSIMISTIC_READ")
                        .routeId("consumerRead")
                        .process(exchange -> routed.add(exchange.getMessage().getBody(SkipLockedItem.class).getId()));
            }
        };
    }

    @Test
    void lockedEntityIsSkipped() throws Exception {
        insertItems(1L, 2L, 3L);
        JpaConsumer consumer = (JpaConsumer) context.getRoute("consumer").getConsumer();

        try (Connection other = openConnection()) {
            // another consumer holds the lock of the second entity
            other.setAutoCommit(false);
            try (Statement st = other.createStatement()) {
                st.execute("select id from skip_locked_item where id = 2 for update");
            }

            // the poll skips the second entity, and consumes the others
            consumer.poll();
            assertEquals(List.of(1L, 3L), routed);
            assertEquals(List.of(2L), itemIds());

            // the next poll skips it again, and routes nothing again
            consumer.poll();
            assertEquals(List.of(1L, 3L), routed);
            assertEquals(List.of(2L), itemIds());

            other.rollback();
        }

        // once its lock is released, the next poll consumes the second entity
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);
        consumer.poll();
        mock.assertIsSatisfied();
        assertEquals(List.of(1L, 3L, 2L), routed);
        assertEquals(List.of(), itemIds());
    }

    @Test
    void entityDeletedByAnotherConsumerIsSkipped() throws Exception {
        insertItems(1L, 2L, 3L);
        JpaConsumer consumer = (JpaConsumer) context.getRoute("consumer").getConsumer();
        // while the poll routes the first entity, another consumer deletes the second one, which the poll has listed
        whenRouted = id -> {
            if (id == 1L) {
                deleteItem(2L);
            }
        };

        consumer.poll();
        assertEquals(List.of(), itemIds());
        assertEquals(List.of(1L, 3L), routed);
    }

    @Test
    void lockedEntityIsSkippedWithPessimisticRead() throws Exception {
        insertItems(1L, 2L, 3L);
        JpaConsumer consumer = (JpaConsumer) context.getRoute("consumerRead").getConsumer();

        try (Connection other = openConnection()) {
            other.setAutoCommit(false);
            try (Statement st = other.createStatement()) {
                st.execute("select id from skip_locked_item where id = 2 for update");
            }

            consumer.poll();
            assertEquals(List.of(2L), itemIds());
            assertEquals(List.of(1L, 3L), routed);

            other.rollback();
        }
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

    private static void deleteItem(long id) {
        try (Connection connection = openConnection();
             Statement st = connection.createStatement()) {
            st.execute("delete from skip_locked_item where id = " + id);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(service.jdbcUrl(), service.userName(), service.password());
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
