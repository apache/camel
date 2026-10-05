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
import java.util.Map;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.hibernate.entity.HibernateTestEntity;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.h2.jdbcx.JdbcDataSource;
import org.hibernate.LockMode;
import org.hibernate.Session;
import org.hibernate.SessionBuilder;
import org.hibernate.SessionFactory;
import org.hibernate.Timeouts;
import org.hibernate.Transaction;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.SelectionQuery;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HibernateBootstrapTest extends CamelTestSupport {

    @Test
    public void testExplicitSessionFactoryReuseAndNoClose() throws Exception {
        SessionFactory mockSf = Mockito.mock(SessionFactory.class);

        HibernateComponent comp = new HibernateComponent();
        comp.setCamelContext(context);
        comp.setSessionFactory(mockSf);
        comp.start();

        assertSame(mockSf, comp.getSessionFactory());
        comp.stop();

        Mockito.verify(mockSf, Mockito.never()).close();
    }

    @Test
    public void testRegistrySessionFactoryReuseAndNoClose() throws Exception {
        SessionFactory mockSf = Mockito.mock(SessionFactory.class);
        context.getRegistry().bind("registrySf", mockSf);

        HibernateComponent comp = new HibernateComponent();
        comp.setCamelContext(context);
        comp.start();

        assertSame(mockSf, comp.getSessionFactory());
        comp.stop();

        Mockito.verify(mockSf, Mockito.never()).close();
    }

    @Test
    public void testComponentCreatedSessionFactoryClosesOnStop() throws Exception {
        HibernateComponent comp = createComponent("testDs", "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");

        SessionFactory sf = comp.getSessionFactory();
        assertNotNull(sf);
        assertFalse(sf.isClosed());

        comp.stop();
        assertTrue(sf.isClosed());
    }

    @Test
    public void testInvalidDataSourceRegistryNameFails() {
        HibernateComponent comp = new HibernateComponent();
        comp.setCamelContext(context);
        comp.setDataSource("nonExistentDs");

        Exception ex = assertThrows(Exception.class, comp::start);
        assertTrue(ex.getMessage().contains("DataSource bean with name 'nonExistentDs' could not be found"));
    }

    @Test
    public void testInvalidSchemaActionFails() {
        HibernateComponent comp = new HibernateComponent();
        comp.setCamelContext(context);
        comp.setSchemaAction("invalid-action");

        Exception ex = assertThrows(Exception.class, comp::start);
        assertTrue(ex.getMessage().contains("Invalid schemaAction"));
    }

    @Test
    public void testSelectionQueryProducer() throws Exception {
        HibernateComponent comp = createComponent(
                "selectionDs",
                "jdbc:h2:mem:selectiondb;DB_CLOSE_DELAY=-1",
                HibernateTestEntity.class);

        HibernateEndpoint endpoint = new HibernateEndpoint();
        endpoint.setCamelContext(context);
        endpoint.setSessionFactory(comp.getSessionFactory());
        endpoint.setEntityClassName(HibernateTestEntity.class.getName());
        endpoint.setSelectionQuery("from HibernateTestEntity");

        endpoint.start();

        Exchange exchange = context.getEndpoint("direct:test").createExchange();
        exchange.getMessage().setHeader(HibernateConstants.HIBERNATE_PARAMETERS, Map.of());

        try (HibernateProducer producer = new HibernateProducer(endpoint)) {
            producer.process(exchange);
        }

        assertNotNull(exchange.getMessage().getBody());
        assertTrue(exchange.getMessage().getBody() instanceof java.util.List);
        assertTrue(((java.util.List<?>) exchange.getMessage().getBody()).isEmpty());

        endpoint.stop();
        comp.stop();
    }

    @Test
    public void testMutationQueryProducer() throws Exception {
        HibernateComponent comp = createComponent(
                "mutationDs",
                "jdbc:h2:mem:mutationdb;DB_CLOSE_DELAY=-1",
                HibernateTestEntity.class);

        HibernateEndpoint endpoint = new HibernateEndpoint();
        endpoint.setCamelContext(context);
        endpoint.setSessionFactory(comp.getSessionFactory());
        endpoint.setEntityClassName(HibernateTestEntity.class.getName());
        endpoint.setMutationQuery("delete from HibernateTestEntity");

        endpoint.start();

        Exchange exchange = context.getEndpoint("direct:test").createExchange();
        exchange.getMessage().setHeader(HibernateConstants.HIBERNATE_PARAMETERS, Map.of());

        try (HibernateProducer producer = new HibernateProducer(endpoint)) {
            producer.process(exchange);
        }

        assertEquals(0, exchange.getMessage().getBody());

        endpoint.stop();
        comp.stop();
    }

    @Test
    public void testSchemaActionsAcceptedValues() {
        String[] actions = { "none", "validate", "update", "create" };
        for (String action : actions) {
            HibernateComponent comp = new HibernateComponent();
            comp.setSchemaAction(action);
            assertEquals(action, comp.getSchemaAction());
        }
    }

    @Test
    public void testExplicitEntityClassesBootstrap() throws Exception {
        HibernateComponent comp = createComponent(
                "entityDs",
                "jdbc:h2:mem:entitydb;DB_CLOSE_DELAY=-1",
                HibernateTestEntity.class);

        SessionFactory sf = comp.getSessionFactory();
        assertNotNull(sf);
        assertNotNull(sf.getMetamodel().entity(HibernateTestEntity.class));

        comp.stop();
    }

    @Test
    public void testSelectionQueryProducerReadOnly() throws Exception {
        HibernateComponent comp = createComponent(
                "readOnlyDs",
                "jdbc:h2:mem:readonlydb;DB_CLOSE_DELAY=-1",
                HibernateTestEntity.class);

        HibernateEndpoint endpoint = new HibernateEndpoint();
        endpoint.setCamelContext(context);
        endpoint.setSessionFactory(comp.getSessionFactory());
        endpoint.setEntityClassName(HibernateTestEntity.class.getName());
        endpoint.setSelectionQuery("from HibernateTestEntity");
        endpoint.setReadOnly(true);

        endpoint.start();

        assertTrue(endpoint.isReadOnly());

        Exchange exchange = context.getEndpoint("direct:test").createExchange();
        exchange.getMessage().setHeader(HibernateConstants.HIBERNATE_PARAMETERS, Map.of());

        try (HibernateProducer producer = new HibernateProducer(endpoint)) {
            producer.process(exchange);
        }

        assertNotNull(exchange.getMessage().getBody());
        assertTrue(exchange.getMessage().getBody() instanceof java.util.List);

        endpoint.stop();
        comp.stop();
    }

    @Test
    public void testNaturalIdLookupProducer() throws Exception {
        HibernateComponent comp = createComponent(
                "naturalIdDs",
                "jdbc:h2:mem:naturaliddb;DB_CLOSE_DELAY=-1",
                HibernateTestEntity.class);

        Session session = comp.getSessionFactory().openSession();
        Transaction transaction = session.beginTransaction();

        HibernateTestEntity entity = new HibernateTestEntity();
        entity.setId(1L);
        entity.setName("test");
        session.persist(entity);

        transaction.commit();
        session.close();

        HibernateEndpoint endpoint = new HibernateEndpoint();
        endpoint.setCamelContext(context);
        endpoint.setSessionFactory(comp.getSessionFactory());
        endpoint.setEntityClassName(HibernateTestEntity.class.getName());
        endpoint.setNaturalIdParameters(Map.of("name", "test"));

        endpoint.start();

        Exchange exchange = context.getEndpoint("direct:test").createExchange();

        try (HibernateProducer producer = new HibernateProducer(endpoint)) {
            producer.process(exchange);
        }

        HibernateTestEntity result = exchange.getMessage().getBody(HibernateTestEntity.class);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("test", result.getName());

        endpoint.stop();
        comp.stop();
    }

    @Test
    public void testSelectionQueryWithFilter() throws Exception {
        HibernateComponent comp = createComponent(
                "filterDs",
                "jdbc:h2:mem:filterdb;DB_CLOSE_DELAY=-1",
                HibernateTestEntity.class);

        Session session = comp.getSessionFactory().openSession();
        Transaction transaction = session.beginTransaction();

        HibernateTestEntity first = new HibernateTestEntity();
        first.setId(1L);
        first.setName("test");

        HibernateTestEntity second = new HibernateTestEntity();
        second.setId(2L);
        second.setName("other");

        session.persist(first);
        session.persist(second);

        transaction.commit();
        session.close();

        HibernateEndpoint endpoint = new HibernateEndpoint();
        endpoint.setCamelContext(context);
        endpoint.setSessionFactory(comp.getSessionFactory());
        endpoint.setEntityClassName(HibernateTestEntity.class.getName());
        endpoint.setSelectionQuery("from HibernateTestEntity");
        endpoint.setFilters(Map.of("nameFilter", Map.of("name", "test")));

        endpoint.start();

        Exchange exchange = context.getEndpoint("direct:test").createExchange();

        try (HibernateProducer producer = new HibernateProducer(endpoint)) {
            producer.process(exchange);
        }

        java.util.List<?> results = exchange.getMessage().getBody(java.util.List.class);

        assertEquals(1, results.size());
        HibernateTestEntity result = (HibernateTestEntity) results.get(0);
        assertEquals("test", result.getName());

        endpoint.stop();
        comp.stop();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTenantIdentifierUsedForSession() throws Exception {
        SessionFactory sessionFactory = Mockito.mock(SessionFactory.class);
        SessionBuilder sessionBuilder = Mockito.mock(SessionBuilder.class);
        Session session = Mockito.mock(Session.class);
        Transaction transaction = Mockito.mock(Transaction.class);
        SelectionQuery<HibernateTestEntity> query = Mockito.mock(SelectionQuery.class);

        Mockito.when(sessionFactory.withOptions()).thenReturn(sessionBuilder);
        Mockito.when(sessionBuilder.tenantIdentifier("tenant1")).thenReturn(sessionBuilder);
        Mockito.when(sessionBuilder.openSession()).thenReturn(session);
        Mockito.when(session.beginTransaction()).thenReturn(transaction);
        Mockito.when(session.createSelectionQuery(
                "from HibernateTestEntity", HibernateTestEntity.class)).thenReturn(query);
        Mockito.when(query.getResultList()).thenReturn(java.util.List.of());

        HibernateEndpoint endpoint = new HibernateEndpoint();
        endpoint.setCamelContext(context);
        endpoint.setSessionFactory(sessionFactory);
        endpoint.setEntityType(HibernateTestEntity.class);
        endpoint.setSelectionQuery("from HibernateTestEntity");
        endpoint.setTenantIdentifier("tenant1");

        endpoint.start();

        Exchange exchange = context.getEndpoint("direct:test").createExchange();

        try (HibernateProducer producer = new HibernateProducer(endpoint)) {
            producer.process(exchange);
        }

        Mockito.verify(sessionBuilder).tenantIdentifier("tenant1");
        Mockito.verify(sessionBuilder).openSession();

        endpoint.stop();
    }

    @Test
    public void testStatelessInsertProducer() throws Exception {
        HibernateComponent comp = createComponent(
                "statelessInsertDs",
                "jdbc:h2:mem:statelessinsertdb;DB_CLOSE_DELAY=-1",
                HibernateTestEntity.class);

        HibernateEndpoint endpoint = new HibernateEndpoint();
        endpoint.setCamelContext(context);
        endpoint.setSessionFactory(comp.getSessionFactory());
        endpoint.setEntityClassName(HibernateTestEntity.class.getName());
        endpoint.setStatelessOperation(HibernateEndpoint.StatelessOperation.INSERT);

        endpoint.start();

        HibernateTestEntity entity = new HibernateTestEntity();
        entity.setId(1L);
        entity.setName("stateless");

        Exchange exchange = context.getEndpoint("direct:test").createExchange();
        exchange.getMessage().setBody(entity);

        try (HibernateProducer producer = new HibernateProducer(endpoint)) {
            producer.process(exchange);
        }

        Session session = comp.getSessionFactory().openSession();
        HibernateTestEntity result = session.find(HibernateTestEntity.class, 1L);
        session.close();

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("stateless", result.getName());

        endpoint.stop();
        comp.stop();
    }

    @Test
    public void testStatelessUpsertProducer() throws Exception {
        HibernateComponent comp = createComponent(
                "statelessUpsertDs",
                "jdbc:h2:mem:statelessupsertdb;DB_CLOSE_DELAY=-1",
                HibernateTestEntity.class);

        HibernateEndpoint endpoint = new HibernateEndpoint();
        endpoint.setCamelContext(context);
        endpoint.setSessionFactory(comp.getSessionFactory());
        endpoint.setEntityClassName(HibernateTestEntity.class.getName());
        endpoint.setStatelessOperation(HibernateEndpoint.StatelessOperation.UPSERT);

        endpoint.start();

        HibernateTestEntity entity = new HibernateTestEntity();
        entity.setId(1L);
        entity.setName("upsert");

        Exchange exchange = context.getEndpoint("direct:test").createExchange();
        exchange.getMessage().setBody(entity);

        try (HibernateProducer producer = new HibernateProducer(endpoint)) {
            producer.process(exchange);
        }

        Session session = comp.getSessionFactory().openSession();
        HibernateTestEntity result = session.find(HibernateTestEntity.class, 1L);
        session.close();

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("upsert", result.getName());

        endpoint.stop();
        comp.stop();
    }

    @Test
    void shouldConfigureSkipLockedQuery() throws Exception {
        SessionFactory sessionFactory = Mockito.mock(SessionFactory.class);
        Session session = Mockito.mock(Session.class);
        Transaction transaction = Mockito.mock(Transaction.class);

        @SuppressWarnings("unchecked")
        SelectionQuery<HibernateTestEntity> query = Mockito.mock(SelectionQuery.class);

        Mockito.when(sessionFactory.openSession()).thenReturn(session);
        Mockito.when(session.beginTransaction()).thenReturn(transaction);
        Mockito.when(session.createSelectionQuery(
                "from HibernateTestEntity", HibernateTestEntity.class)).thenReturn(query);
        Mockito.when(query.getResultList()).thenReturn(List.of());

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            HibernateEndpoint endpoint = new HibernateEndpoint();
            endpoint.setCamelContext(context);
            endpoint.setSessionFactory(sessionFactory);
            endpoint.setEntityType(HibernateTestEntity.class);
            endpoint.setSelectionQuery("from HibernateTestEntity");
            endpoint.setSkipLocked(true);

            HibernateConsumer consumer = new HibernateConsumer(endpoint, exchange -> {
            });

            consumer.poll();

            Mockito.verify(query).setHibernateLockMode(LockMode.PESSIMISTIC_WRITE);
            Mockito.verify(query).setLockTimeout(Timeouts.SKIP_LOCKED);
            Mockito.verify(transaction).commit();
        }
    }

    @Test
    void shouldSkipLockedRowsWithH2() throws Exception {
        HibernateComponent component = createComponent(
                "skipLockedDs",
                "jdbc:h2:mem:skiplockeddb;DB_CLOSE_DELAY=-1",
                HibernateTestEntity.class);

        SessionFactory sessionFactory = component.getSessionFactory();

        Session lockingSession = sessionFactory.openSession();
        Transaction lockingTransaction = lockingSession.beginTransaction();

        HibernateTestEntity entity = new HibernateTestEntity();
        entity.setId(1L);
        lockingSession.persist(entity);
        lockingTransaction.commit();

        lockingTransaction = lockingSession.beginTransaction();
        lockingSession.find(
                HibernateTestEntity.class,
                entity.getId(),
                LockMode.PESSIMISTIC_WRITE);

        try {
            HibernateEndpoint endpoint = new HibernateEndpoint();
            endpoint.setCamelContext(context);
            endpoint.setSessionFactory(sessionFactory);
            endpoint.setEntityType(HibernateTestEntity.class);
            endpoint.setSelectionQuery("from HibernateTestEntity");
            endpoint.setSkipLocked(true);
            endpoint.setConsumeDelete(false);

            HibernateConsumer consumer = new HibernateConsumer(endpoint, exchange -> {
            });

            assertEquals(0, consumer.poll());
        } finally {
            lockingTransaction.rollback();
            lockingSession.close();
            component.stop();
        }
    }

    @Test
    void shouldStreamSelectionQueryResults() throws Exception {
        SessionFactory sessionFactory = Mockito.mock(SessionFactory.class);
        Session session = Mockito.mock(Session.class);
        Transaction transaction = Mockito.mock(Transaction.class);

        @SuppressWarnings("unchecked")
        SelectionQuery<HibernateTestEntity> query = Mockito.mock(SelectionQuery.class);

        HibernateTestEntity entity1 = new HibernateTestEntity();
        HibernateTestEntity entity2 = new HibernateTestEntity();

        Stream<HibernateTestEntity> stream = Stream.of(entity1, entity2);

        Mockito.when(sessionFactory.openSession()).thenReturn(session);
        Mockito.when(session.beginTransaction()).thenReturn(transaction);
        Mockito.when(transaction.isActive()).thenReturn(true);
        Mockito.when(session.createSelectionQuery(
                "from HibernateTestEntity", HibernateTestEntity.class)).thenReturn(query);
        Mockito.when(query.getResultStream()).thenReturn(stream);

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            HibernateEndpoint endpoint = new HibernateEndpoint();
            endpoint.setCamelContext(context);
            endpoint.setSessionFactory(sessionFactory);
            endpoint.setEntityType(HibernateTestEntity.class);
            endpoint.setSelectionQuery("from HibernateTestEntity");
            endpoint.setStreaming(true);

            try (HibernateProducer producer = new HibernateProducer(endpoint)) {
                Exchange exchange = endpoint.createExchange();

                producer.process(exchange);

                Object body = exchange.getMessage().getBody();

                assertInstanceOf(Stream.class, body);

                @SuppressWarnings("unchecked")
                Stream<HibernateTestEntity> result = (Stream<HibernateTestEntity>) body;

                assertEquals(List.of(entity1, entity2), result.toList());

                result.close();
            }
        }

        Mockito.verify(query).getResultStream();
        Mockito.verify(transaction).commit();
        Mockito.verify(session).close();
    }

    @Test
    void shouldCloseSessionWhenStreamingTransactionIsInactive() throws Exception {
        SessionFactory sessionFactory = Mockito.mock(SessionFactory.class);
        Session session = Mockito.mock(Session.class);
        Transaction transaction = Mockito.mock(Transaction.class);

        @SuppressWarnings("unchecked")
        SelectionQuery<HibernateTestEntity> query = Mockito.mock(SelectionQuery.class);

        HibernateTestEntity entity = new HibernateTestEntity();
        Stream<HibernateTestEntity> stream = Stream.of(entity);

        Mockito.when(sessionFactory.openSession()).thenReturn(session);
        Mockito.when(session.beginTransaction()).thenReturn(transaction);
        Mockito.when(transaction.isActive()).thenReturn(false);
        Mockito.when(session.createSelectionQuery(
                "from HibernateTestEntity", HibernateTestEntity.class)).thenReturn(query);
        Mockito.when(query.getResultStream()).thenReturn(stream);

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            HibernateEndpoint endpoint = new HibernateEndpoint();
            endpoint.setCamelContext(context);
            endpoint.setSessionFactory(sessionFactory);
            endpoint.setEntityType(HibernateTestEntity.class);
            endpoint.setSelectionQuery("from HibernateTestEntity");
            endpoint.setStreaming(true);

            try (HibernateProducer producer = new HibernateProducer(endpoint)) {
                Exchange exchange = endpoint.createExchange();

                producer.process(exchange);

                @SuppressWarnings("unchecked")
                Stream<HibernateTestEntity> result = (Stream<HibernateTestEntity>) exchange.getMessage().getBody();

                result.close();
            }
        }

        Mockito.verify(transaction, Mockito.never()).commit();
        Mockito.verify(session).close();
    }

    @Test
    void shouldContinueAfterProcessorFailure() throws Exception {
        SessionFactory sessionFactory = Mockito.mock(SessionFactory.class);
        Session session = Mockito.mock(Session.class);
        Transaction transaction = Mockito.mock(Transaction.class);

        @SuppressWarnings("unchecked")
        SelectionQuery<HibernateTestEntity> query = Mockito.mock(SelectionQuery.class);

        HibernateTestEntity entity1 = new HibernateTestEntity();
        HibernateTestEntity entity2 = new HibernateTestEntity();
        HibernateTestEntity entity3 = new HibernateTestEntity();

        Mockito.when(sessionFactory.openSession()).thenReturn(session);
        Mockito.when(session.beginTransaction()).thenReturn(transaction);
        Mockito.when(session.createSelectionQuery(
                "from HibernateTestEntity", HibernateTestEntity.class)).thenReturn(query);
        Mockito.when(query.getResultList()).thenReturn(List.of(entity1, entity2, entity3));

        List<HibernateTestEntity> processed = new java.util.ArrayList<>();

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            HibernateEndpoint endpoint = new HibernateEndpoint();
            endpoint.setCamelContext(context);
            endpoint.setSessionFactory(sessionFactory);
            endpoint.setEntityType(HibernateTestEntity.class);
            endpoint.setSelectionQuery("from HibernateTestEntity");

            HibernateConsumer consumer = new HibernateConsumer(endpoint, exchange -> {
                HibernateTestEntity entity = exchange.getMessage().getBody(HibernateTestEntity.class);
                if (entity == entity2) {
                    throw new IllegalStateException("poison row");
                }
                processed.add(entity);
            });

            assertEquals(3, consumer.poll());
        }

        assertEquals(List.of(entity1, entity3), processed);
        Mockito.verify(transaction).commit();
    }

    @Test
    void shouldReuseConsumerSessionInProducer() throws Exception {
        SessionFactory sessionFactory = Mockito.mock(SessionFactory.class);
        Session session = Mockito.mock(Session.class);
        Transaction transaction = Mockito.mock(Transaction.class);
        SelectionQuery<HibernateTestEntity> selectionQuery = Mockito.mock(SelectionQuery.class);
        MutationQuery mutationQuery = Mockito.mock(MutationQuery.class);

        HibernateTestEntity entity = new HibernateTestEntity();

        Mockito.when(sessionFactory.openSession()).thenReturn(session);
        Mockito.when(session.beginTransaction()).thenReturn(transaction);
        Mockito.when(session.getTransaction()).thenReturn(transaction);
        Mockito.when(session.createSelectionQuery(
                Mockito.eq("from HibernateTestEntity"),
                Mockito.eq(HibernateTestEntity.class))).thenReturn(selectionQuery);
        Mockito.when(selectionQuery.getResultList()).thenReturn(List.of(entity));
        Mockito.when(session.createMutationQuery(
                "delete from HibernateTestEntity")).thenReturn(mutationQuery);
        Mockito.when(mutationQuery.execute()).thenReturn(1);

        HibernateEndpoint consumerEndpoint = new HibernateEndpoint();
        consumerEndpoint.setCamelContext(context);
        consumerEndpoint.setSessionFactory(sessionFactory);
        consumerEndpoint.setEntityType(HibernateTestEntity.class);
        consumerEndpoint.setSelectionQuery("from HibernateTestEntity");

        HibernateConsumer consumer = new HibernateConsumer(
                consumerEndpoint,
                exchange -> {
                    HibernateSessionContext sessionContext = exchange.getExchangeExtension()
                            .getSafeCopyProperty(
                                    HibernateConstants.HIBERNATE_SESSION_CONTEXT,
                                    HibernateSessionContext.class);
                    assertNotNull(sessionContext);
                    assertSame(session, sessionContext.getSession(sessionFactory, null));

                    HibernateEndpoint producerEndpoint = new HibernateEndpoint();
                    producerEndpoint.setCamelContext(context);
                    producerEndpoint.setSessionFactory(sessionFactory);
                    producerEndpoint.setEntityType(HibernateTestEntity.class);
                    producerEndpoint.setMutationQuery("delete from HibernateTestEntity");

                    new HibernateProducer(producerEndpoint).process(exchange);
                });

        consumerEndpoint.start();
        try {
            consumer.poll();
        } finally {
            consumerEndpoint.stop();
        }

        Mockito.verify(sessionFactory, Mockito.times(1)).openSession();
        Mockito.verify(session, Mockito.times(1)).beginTransaction();
        Mockito.verify(session, Mockito.times(1)).getTransaction();
        Mockito.verify(session, Mockito.times(1)).createMutationQuery(
                "delete from HibernateTestEntity");
        Mockito.verify(transaction, Mockito.times(1)).commit();
        Mockito.verify(session, Mockito.times(1)).close();
    }

    @Test
    void shouldDeleteConsumedEntityByDefault() throws Exception {
        SessionFactory sessionFactory = Mockito.mock(SessionFactory.class);
        Session session = Mockito.mock(Session.class);
        Transaction transaction = Mockito.mock(Transaction.class);
        SelectionQuery<HibernateTestEntity> query = Mockito.mock(SelectionQuery.class);
        HibernateTestEntity entity = new HibernateTestEntity();

        Mockito.when(sessionFactory.openSession()).thenReturn(session);
        Mockito.when(session.beginTransaction()).thenReturn(transaction);
        Mockito.when(query.getResultList()).thenReturn(List.of(entity));
        Mockito.when(session.createSelectionQuery(
                Mockito.eq("from HibernateTestEntity"),
                Mockito.eq(HibernateTestEntity.class))).thenReturn(query);

        HibernateEndpoint endpoint = new HibernateEndpoint();
        endpoint.setCamelContext(context);
        endpoint.setSessionFactory(sessionFactory);
        endpoint.setEntityType(HibernateTestEntity.class);
        endpoint.setSelectionQuery("from HibernateTestEntity");

        Processor processor = exchange -> {
        };
        HibernateConsumer consumer = new HibernateConsumer(endpoint, processor);

        assertEquals(1, consumer.poll());

        Mockito.verify(session).remove(entity);
        Mockito.verify(transaction).commit();
        Mockito.verify(session).close();
    }

    @Test
    void shouldNotDeleteConsumedEntityWhenConsumeDeleteDisabled() throws Exception {
        SessionFactory sessionFactory = Mockito.mock(SessionFactory.class);
        Session session = Mockito.mock(Session.class);
        Transaction transaction = Mockito.mock(Transaction.class);
        SelectionQuery<HibernateTestEntity> query = Mockito.mock(SelectionQuery.class);
        HibernateTestEntity entity = new HibernateTestEntity();

        Mockito.when(sessionFactory.openSession()).thenReturn(session);
        Mockito.when(session.beginTransaction()).thenReturn(transaction);
        Mockito.when(query.getResultList()).thenReturn(List.of(entity));
        Mockito.when(session.createSelectionQuery(
                Mockito.eq("from HibernateTestEntity"),
                Mockito.eq(HibernateTestEntity.class))).thenReturn(query);

        HibernateEndpoint endpoint = new HibernateEndpoint();
        endpoint.setCamelContext(context);
        endpoint.setSessionFactory(sessionFactory);
        endpoint.setEntityType(HibernateTestEntity.class);
        endpoint.setSelectionQuery("from HibernateTestEntity");
        endpoint.setConsumeDelete(false);

        Processor processor = exchange -> {
        };
        HibernateConsumer consumer = new HibernateConsumer(endpoint, processor);

        assertEquals(1, consumer.poll());

        Mockito.verify(session, Mockito.never()).remove(entity);
        Mockito.verify(transaction).commit();
        Mockito.verify(session).close();
    }

    @Test
    void shouldNotReuseSessionContextForDifferentScope() {
        SessionFactory sessionFactory = Mockito.mock(SessionFactory.class);
        Session session = Mockito.mock(Session.class);

        HibernateSessionContext context = new HibernateSessionContext(session, sessionFactory, "tenant-a");

        assertSame(session, context.getSession(sessionFactory, "tenant-a"));
        assertNull(context.getSession(Mockito.mock(SessionFactory.class), "tenant-a"));
        assertNull(context.getSession(sessionFactory, "tenant-b"));
        assertNull(context.safeCopy().getSession(sessionFactory, "tenant-a"));

        context.invalidate();
        assertNull(context.getSession(sessionFactory, "tenant-a"));
    }

    @Test
    void shouldNotMutateReusedConsumerSession() throws Exception {
        SessionFactory sessionFactory = Mockito.mock(SessionFactory.class);
        Session session = Mockito.mock(Session.class);
        Transaction transaction = Mockito.mock(Transaction.class);
        SelectionQuery<HibernateTestEntity> query = Mockito.mock(SelectionQuery.class);

        Mockito.when(session.getTransaction()).thenReturn(transaction);
        Mockito.when(session.createSelectionQuery(
                Mockito.eq("from HibernateTestEntity"),
                Mockito.eq(HibernateTestEntity.class))).thenReturn(query);
        Mockito.when(query.getResultList()).thenReturn(List.of());

        HibernateEndpoint endpoint = new HibernateEndpoint();
        endpoint.setCamelContext(context);
        endpoint.setSessionFactory(sessionFactory);
        endpoint.setEntityType(HibernateTestEntity.class);
        endpoint.setSelectionQuery("from HibernateTestEntity");
        endpoint.setReadOnly(true);
        endpoint.setFilters(Map.of("testFilter", Map.of("tenant", "tenant-a")));

        Exchange exchange = context.getEndpoint("direct:test").createExchange();
        exchange.getExchangeExtension().setSafeCopyProperty(
                HibernateConstants.HIBERNATE_SESSION_CONTEXT,
                new HibernateSessionContext(session, sessionFactory, null));

        new HibernateProducer(endpoint).process(exchange);

        Mockito.verify(session, Mockito.never()).setDefaultReadOnly(true);
        Mockito.verify(session, Mockito.never()).enableFilter("testFilter");
        Mockito.verify(query).setReadOnly(true);
        Mockito.verify(transaction, Mockito.never()).commit();
        Mockito.verify(session, Mockito.never()).close();
    }

    private HibernateComponent createComponent(String dataSourceName, String url, Class<?>... entityClasses)
            throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL(url);
        context.getRegistry().bind(dataSourceName, ds);

        HibernateComponent comp = new HibernateComponent();
        comp.setCamelContext(context);
        comp.setDataSource(dataSourceName);
        comp.setEntityClasses(entityClasses);
        comp.setSchemaAction("create");
        comp.start();

        return comp;
    }
}
