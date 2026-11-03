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
package org.apache.camel.component.hibernate;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.RollbackExchangeException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hibernate.entity.HibernateTestEntity;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.h2.jdbcx.JdbcDataSource;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.junit.jupiter.api.Test;

import static org.apache.camel.builder.Builder.simple;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HibernateConsumerRollbackTest extends CamelTestSupport {

    private static final String ENTITY = HibernateTestEntity.class.getName();

    private SessionFactory setup(String db, long... ids) throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + db + ";DB_CLOSE_DELAY=-1");
        context.getRegistry().bind(db + "Ds", ds);

        HibernateComponent component = new HibernateComponent();
        component.setCamelContext(context);
        component.setDataSource(db + "Ds");
        component.setEntityClasses(new Class<?>[] { HibernateTestEntity.class });
        component.setSchemaAction("create");
        component.start();
        context.addComponent("hibernate", component);

        SessionFactory sessionFactory = component.getSessionFactory();
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            for (long id : ids) {
                HibernateTestEntity entity = new HibernateTestEntity();
                entity.setId(id);
                entity.setName("n" + id);
                session.persist(entity);
            }
            transaction.commit();
        }
        return sessionFactory;
    }

    private List<Long> ids(SessionFactory sessionFactory) {
        try (Session session = sessionFactory.openSession()) {
            return session.createSelectionQuery("select id from HibernateTestEntity order by id", Long.class)
                    .getResultList();
        }
    }

    private HibernateConsumer hibernateConsumer() {
        return (HibernateConsumer) context.getRoute("r").getConsumer();
    }

    @Test
    void rollbackOnlyExchangeMustNotConsumeEntity() throws Exception {
        SessionFactory sessionFactory = setup("hibernateRollbackOnly", 1L);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("hibernate:" + ENTITY + "?selectionQuery=from%20HibernateTestEntity&initialDelay=600000")
                        .routeId("r")
                        .markRollbackOnly();
            }
        });
        context.start();

        assertThrows(RollbackExchangeException.class, () -> hibernateConsumer().poll());
        assertEquals(List.of(1L), ids(sessionFactory), "A rollback-only exchange must not consume its entity");
    }

    @Test
    void handledProducerFailureOnSharedSessionRollsBackPoll() throws Exception {
        SessionFactory sessionFactory = setup("hibernateHandledFailure", 1L, 2L);
        List<String> handled = new CopyOnWriteArrayList<>();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class).handled(true).process(exchange -> {
                    Object caught = exchange.getProperty(Exchange.EXCEPTION_CAUGHT);
                    handled.add(caught == null ? "unknown" : caught.getClass().getSimpleName());
                });
                from("hibernate:" + ENTITY + "?selectionQuery=from%20HibernateTestEntity%20order%20by%20id"
                     + "&initialDelay=600000")
                        .routeId("r")
                        .filter(simple("${body.id} == 1"))
                        .to("hibernate:" + ENTITY
                            + "?mutationQuery=insert%20into%20HibernateTestEntity%20(id,%20name)%20values%20(2,%20'dup')");
            }
        });
        context.start();

        assertThrows(IllegalStateException.class, () -> hibernateConsumer().poll());
        assertEquals(1, handled.size());
        assertEquals(List.of(1L, 2L), ids(sessionFactory),
                "A rollback-only shared session must roll back the complete poll transaction");
    }
}
