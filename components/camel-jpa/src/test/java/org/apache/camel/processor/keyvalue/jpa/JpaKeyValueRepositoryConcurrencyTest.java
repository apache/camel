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
package org.apache.camel.processor.keyvalue.jpa;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;

import org.apache.camel.component.jpa.DefaultTransactionStrategy;
import org.apache.camel.support.KeyValueRepositoryHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two repositories (two nodes) on one database. The entity manager operations of node A can be paused, so that the
 * calls of both nodes interleave in a given order.
 */
class JpaKeyValueRepositoryConcurrencyTest {

    private EntityManagerFactory entityManagerFactory;
    private PausingEntityManagerFactory pausingA;
    private JpaKeyValueRepository nodeA;
    private JpaKeyValueRepository nodeB;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @BeforeEach
    void startNodes() throws Exception {
        entityManagerFactory = Persistence.createEntityManagerFactory("keyvalueDb",
                Map.of("openjpa.ConnectionURL", "jdbc:h2:mem:kv-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1"));
        pausingA = new PausingEntityManagerFactory(entityManagerFactory);
        nodeA = createNode(pausingA.proxy());
        nodeB = createNode(entityManagerFactory);
    }

    private static JpaKeyValueRepository createNode(EntityManagerFactory emf) throws Exception {
        JpaKeyValueRepository node = new JpaKeyValueRepository(emf);
        node.setTransactionStrategy(new DefaultTransactionStrategy(null, emf));
        node.setJoinTransaction(false);
        node.setSharedEntityManager(true);
        node.init();
        node.start();
        return node;
    }

    @AfterEach
    void tearDown() throws Exception {
        if (pausingA != null) {
            pausingA.releaseAll();
        }
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        if (nodeA != null) {
            nodeA.stop();
            nodeB.stop();
        }
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
    }

    @Test
    void putIfAbsentReturnsTheValueOfTheWinner() throws Exception {
        Gate beforeInsert = pausingA.pause(2);

        Future<Object> a = executor.submit(() -> nodeA.putIfAbsent("order-1", "A", null));
        // A has read the key (absent) and is about to insert it
        beforeInsert.awaitReached();
        assertNull(nodeB.putIfAbsent("order-1", "B", null), "B stores the key first");
        // the insert of A fails with a constraint violation
        beforeInsert.release();

        assertEquals("B", a.get(10, TimeUnit.SECONDS), "A lost the race and must get the value of B");
        assertEquals("B", nodeA.get("order-1"));
    }

    @Test
    void putIfAbsentReturnsTheValueOfTheWinnerWhenTheDriverReportsOnlyTheSqlState() throws Exception {
        // as the PostgreSQL and SQL Server drivers do: a plain SQLException with the SQL state of a unique violation
        pausingA.reportConstraintViolationsBySqlState();
        Gate beforeInsert = pausingA.pause(2);

        Future<Object> a = executor.submit(() -> nodeA.putIfAbsent("order-2", "A", null));
        beforeInsert.awaitReached();
        assertNull(nodeB.putIfAbsent("order-2", "B", null), "B stores the key first");
        beforeInsert.release();

        assertEquals("B", a.get(10, TimeUnit.SECONDS), "A lost the race and must get the value of B");
    }

    @Test
    void putIfAbsentInsertsAgainWhenTheOtherEntryIsDeletedMeanwhile() throws Exception {
        Gate beforeInsert = pausingA.pause(2);
        Gate beforeSecondRead = pausingA.pause(4);

        Future<Object> a = executor.submit(() -> nodeA.putIfAbsent("lease-1", "A", null));
        beforeInsert.awaitReached();
        assertNull(nodeB.putIfAbsent("lease-1", "B", null), "B stores the key first");
        // the insert of A fails with a constraint violation, then A reads the key again
        beforeInsert.release();
        beforeSecondRead.awaitReached();
        assertEquals("B", nodeB.delete("lease-1"), "B releases the key");
        beforeSecondRead.release();

        // the key was free when A read it again, so A may get it, but then its value must be stored
        assertNull(a.get(10, TimeUnit.SECONDS));
        assertEquals("A", nodeB.get("lease-1"), "A was told that its put succeeded");
    }

    @Test
    void putIfAbsentOnAnExpiredEntryHasOneWinner() throws Exception {
        storeExpiredEntry("job-7");
        Gate afterRead = pausingA.pause(2);

        Future<Object> a = executor.submit(() -> nodeA.putIfAbsent("job-7", "A", null));
        // A has read the entry as expired
        afterRead.awaitReached();
        assertNull(nodeB.putIfAbsent("job-7", "B", null), "the expired entry is absent, so B gets the key");
        afterRead.release();

        assertEquals("B", a.get(10, TimeUnit.SECONDS), "A lost the race and must get the value of B");
        assertEquals("B", nodeB.get("job-7"));
    }

    @Test
    void readingAnExpiredEntryDoesNotDeleteTheEntryStoredMeanwhile() throws Exception {
        storeExpiredEntry("job-42");
        Gate afterRead = pausingA.pause(2);

        Future<Object> a = executor.submit(() -> nodeA.get("job-42"));
        // A has read the entry as expired and is about to delete it
        afterRead.awaitReached();
        assertNull(nodeB.putIfAbsent("job-42", "B", null), "the expired entry is absent, so B gets the key");
        afterRead.release();

        assertNull(a.get(10, TimeUnit.SECONDS), "A read the entry while it was expired");
        assertEquals("B", nodeB.get("job-42"), "the entry stored by B must be kept");
        assertEquals("B", nodeA.putIfAbsent("job-42", "A", null), "B still holds the key");
    }

    @Test
    void putIfAbsentGivesUpAfterThreeConstraintViolations() {
        assertNull(nodeB.putIfAbsent("job-1", "B", null));
        // node A does not find the stored entry, so each of its inserts fails with a constraint violation
        pausingA.hideEntries();

        assertThrows(PersistenceException.class, () -> nodeA.putIfAbsent("job-1", "A", null));
        assertEquals(3, pausingA.operations("persist"));
        assertEquals("B", nodeB.get("job-1"));
    }

    private void storeExpiredEntry(String key) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            // an entry that expired long ago
            em.persist(new KeyValueEntry(key, KeyValueRepositoryHelper.serialize("old"), 1L));
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    /**
     * Pauses before the n-th entity manager operation of a {@link PausingEntityManagerFactory}, until released.
     */
    private static final class Gate {
        private final int occurrence;
        private final CountDownLatch reached = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        private Gate(int occurrence) {
            this.occurrence = occurrence;
        }

        private void beforeOperation(int number) {
            if (number == occurrence) {
                reached.countDown();
                await(released);
            }
        }

        void awaitReached() {
            await(reached);
        }

        void release() {
            released.countDown();
        }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timeout waiting for the other node");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Wraps the entity managers of a factory, counts their operations that read or write the database (query
     * executions, persist, merge, remove, flush) and pauses them at the given gates.
     */
    private static final class PausingEntityManagerFactory {
        private final EntityManagerFactory delegate;
        private final List<Gate> gates = new CopyOnWriteArrayList<>();
        private final List<String> executed = new CopyOnWriteArrayList<>();
        private final AtomicInteger count = new AtomicInteger();
        private volatile boolean hideEntries;
        private volatile boolean bySqlState;

        private PausingEntityManagerFactory(EntityManagerFactory delegate) {
            this.delegate = delegate;
        }

        Gate pause(int occurrence) {
            Gate gate = new Gate(occurrence);
            gates.add(gate);
            return gate;
        }

        void hideEntries() {
            hideEntries = true;
        }

        void reportConstraintViolationsBySqlState() {
            bySqlState = true;
        }

        long operations(String name) {
            return executed.stream().filter(name::equals).count();
        }

        void releaseAll() {
            gates.forEach(Gate::release);
        }

        private void beforeOperation(String name) {
            executed.add(name);
            int number = count.incrementAndGet();
            gates.forEach(gate -> gate.beforeOperation(number));
        }

        EntityManagerFactory proxy() {
            return proxy(EntityManagerFactory.class, delegate, (method, args) -> {
                Object result = invoke(delegate, method, args);
                if (result instanceof EntityManager em) {
                    return proxy(EntityManager.class, em, (m, a) -> onEntityManager(em, m, a));
                }
                return result;
            });
        }

        private Object onEntityManager(EntityManager em, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("persist".equals(name) || "merge".equals(name) || "remove".equals(name) || "flush".equals(name)) {
                beforeOperation(name);
            }
            Object result;
            try {
                result = invoke(em, method, args);
            } catch (PersistenceException e) {
                if (bySqlState && "flush".equals(name)) {
                    throw new PersistenceException(
                            "Simulated driver error",
                            new SQLException("duplicate key value violates unique constraint", "23505"));
                }
                throw e;
            }
            if (result instanceof Query query && "createQuery".equals(name) && args[0] instanceof String jpql) {
                return proxy(Query.class, query, (m, a) -> onQuery(query, jpql, m, a));
            }
            return result;
        }

        private Object onQuery(Query query, String jpql, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("getResultList".equals(name) || "getSingleResult".equals(name) || "executeUpdate".equals(name)) {
                beforeOperation(jpql.substring(0, jpql.indexOf(' ')));
                if (hideEntries && "getResultList".equals(name) && jpql.startsWith("select e from")) {
                    return Collections.emptyList();
                }
            }
            return invoke(query, method, args);
        }

        private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        @FunctionalInterface
        private interface Handler {
            Object handle(Method method, Object[] args) throws Throwable;
        }

        private static <T> T proxy(Class<T> type, Object target, Handler handler) {
            InvocationHandler invocationHandler = (p, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    // equals, hashCode and toString of the proxy are those of the target
                    if ("equals".equals(method.getName())) {
                        return p == args[0];
                    }
                    return invoke(target, method, args);
                }
                return handler.handle(method, args);
            };
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, invocationHandler));
        }
    }
}
