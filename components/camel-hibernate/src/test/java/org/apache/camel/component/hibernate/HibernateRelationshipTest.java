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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Persistence;
import jakarta.persistence.TypedQuery;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HibernateRelationshipTest extends CamelTestSupport {

    private EntityManagerFactory entityManagerFactory;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        entityManagerFactory = Persistence.createEntityManagerFactory("camel-hibernate-tests");

        CamelContext context = super.createCamelContext();

        HibernateComponent component = new HibernateComponent();
        component.setEntityManagerFactory(entityManagerFactory);
        context.addComponent("hibernate", component);

        return context;
    }

    @BeforeEach
    public void setupTestDatabase() {
        cleanDatabase();
    }

    @AfterEach
    public void teardownTestDatabase() {
        if (context != null) {
            context.stop();
        }
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }

    /**
     * Executes a transactional work unit with centralized safety checks, transaction handling, and auto-cleanup.
     */
    private void executeInTransaction(Consumer<EntityManager> work) {
        executeInEntityManager(em -> {
            try {
                em.getTransaction().begin();
                work.accept(em);
                em.getTransaction().commit();
            } catch (Exception e) {
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }
                throw e;
            }
            return null;
        });
    }

    /**
     * Executes a read/query work unit with centralized safety checks and auto-cleanup.
     */
    private <R> R executeInEntityManager(Function<EntityManager, R> work) {
        if (entityManagerFactory == null || !entityManagerFactory.isOpen()) {
            throw new IllegalStateException("EntityManagerFactory is not initialized or is closed.");
        }
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            return work.apply(em);
        } finally {
            em.close();
        }
    }

    /**
     * Clean database using transactional bulk HQL deletes to prevent detached entity leftover state across tests.
     */
    private void cleanDatabase() {
        if (entityManagerFactory == null || !entityManagerFactory.isOpen()) {
            return;
        }
        executeInTransaction(em -> {
            em.createNativeQuery("DELETE FROM product_tags").executeUpdate();
            em.createQuery("DELETE FROM Tag").executeUpdate();
            em.createQuery("DELETE FROM Product").executeUpdate();
            em.createQuery("DELETE FROM Category").executeUpdate();
            em.createQuery("DELETE FROM Item").executeUpdate();
        });
    }

    /**
     * Helper method to persist test entities inside an isolated transaction.
     */
    private void persistEntities(Object... entities) {
        executeInTransaction(em -> {
            for (Object entity : entities) {
                em.persist(entity);
            }
        });
    }

    /**
     * Helper method to run COUNT queries bypassing 1st and 2nd level caches.
     */
    private Long countQuery(String hql, Map<String, Object> params) {
        return executeInEntityManager(em -> {
            TypedQuery<Long> query = em.createQuery(hql, Long.class)
                    .setHint("jakarta.persistence.cache.retrieveMode", "BYPASS");
            if (params != null) {
                params.forEach(query::setParameter);
            }
            return query.getSingleResult();
        });
    }

    // =========================================================================
    // BRANCH 1: Camel JPA Standards, Consumer/Producer Patterns & Edge Cases
    // =========================================================================

    @Test
    public void testConsumerConsumeDeleteRemovesProductButKeepsCategory() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:consumerDelete");
        mock.expectedMinimumMessageCount(1);

        Category category = new Category("Electronics");
        Product product = new Product("Laptop", 1200.0, category);
        category.addProduct(product);

        persistEntities(category, product);

        mock.assertIsSatisfied();

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            entityManagerFactory.getCache().evictAll();

            Long productCount = countQuery("SELECT COUNT(p) FROM Product p", null);
            Long categoryCount = countQuery("SELECT COUNT(c) FROM Category c", null);

            assertEquals(0L, productCount, "Product should be deleted by consumeDelete");
            assertEquals(1L, categoryCount, "Category should remain intact");
        });
    }

    @Test
    public void testConsumerConsumeDeleteClearsManyToManyAssociation() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:manyToManyDelete");
        mock.expectedMinimumMessageCount(1);

        Tag tag = new Tag("gadget");
        Product product = new Product("TaggedLaptop", 999.0);
        product.addTag(tag);

        persistEntities(tag, product);

        mock.assertIsSatisfied();

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            entityManagerFactory.getCache().evictAll();

            Long productCount = countQuery("SELECT COUNT(p) FROM Product p WHERE p.name = :name",
                    Map.of("name", "TaggedLaptop"));
            Long tagCount = countQuery("SELECT COUNT(t) FROM Tag t WHERE t.name = :name",
                    Map.of("name", "gadget"));

            assertEquals(0L, productCount, "Product should be deleted by consumeDelete");
            assertEquals(1L, tagCount, "Many-to-many Tag peer should remain intact");
        });
    }

    @Test
    public void testTransactionRollbackOnConsumerExceptionDoesNotConsumeEntity() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:rollbackResult");
        mock.whenAnyExchangeReceived(exchange -> {
            throw new RuntimeException("Simulated processing error during consumption");
        });

        Product product = new Product();
        product.setName("RollbackTestLaptop");
        persistEntities(product);

        Thread.sleep(500);

        Long count = countQuery("SELECT COUNT(p) FROM Product p WHERE p.name = :name",
                Map.of("name", "RollbackTestLaptop"));
        assertEquals(1L, count, "Product should NOT be deleted when consumer route throws an exception");
    }

    @Test
    public void testNamedQueryConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:namedQueryResult");
        mock.expectedMessageCount(1);

        persistEntities(
                new Item("TargetItem", 99.99),
                new Item("OtherItem", 49.99));

        mock.assertIsSatisfied();

        Item received = mock.getExchanges().get(0).getIn().getBody(Item.class);
        assertEquals("TargetItem", received.getName());
        assertEquals(99.99, received.getPrice());

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            entityManagerFactory.getCache().evictAll();

            Long targetCount = countQuery("SELECT COUNT(i) FROM Item i WHERE i.name = :name", Map.of("name", "TargetItem"));
            Long otherCount = countQuery("SELECT COUNT(i) FROM Item i WHERE i.name = :name", Map.of("name", "OtherItem"));

            assertEquals(0L, targetCount, "TargetItem should be removed by consumeDelete");
            assertEquals(1L, otherCount, "OtherItem should remain untouched in database");
        });
    }

    @Test
    public void testNativeSqlQueryConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:nativeQueryResult");
        mock.expectedMessageCount(1);

        persistEntities(
                new Item("NativeItem", 150.00),
                new Item("IgnoredItem", 20.00));

        mock.assertIsSatisfied();

        Item received = mock.getExchanges().get(0).getIn().getBody(Item.class);
        assertEquals("NativeItem", received.getName());
        assertEquals(150.00, received.getPrice());

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            entityManagerFactory.getCache().evictAll();

            Long nativeCount = countQuery("SELECT COUNT(i) FROM Item i WHERE i.name = :name", Map.of("name", "NativeItem"));
            Long ignoredCount = countQuery("SELECT COUNT(i) FROM Item i WHERE i.name = :name", Map.of("name", "IgnoredItem"));

            assertEquals(0L, nativeCount, "NativeItem should be removed by consumeDelete");
            assertEquals(1L, ignoredCount, "IgnoredItem should remain in database");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testProducerPersistAndQueryWithParameters() {
        Category category = new Category("ParamTestCategory");
        Product p1 = new Product("Laptop", 1200.0, category);
        Product p2 = new Product("Smartphone", 800.0, category);
        category.addProduct(p1);
        category.addProduct(p2);

        persistEntities(category, p1, p2);

        List<Product> products = template.requestBodyAndHeader("hibernate:org.apache.camel.component.hibernate.Product", null,
                HibernateConstants.HIBERNATE_QUERY,
                "SELECT p FROM Product p WHERE p.category.name = 'ParamTestCategory' AND p.price >= 1000.0", List.class);

        assertNotNull(products);
        assertEquals(1, products.size());
        assertEquals("Laptop", products.get(0).getName());
    }

    @Test
    public void testJpaProducerPersistAndMergeEntity() {
        Item item = new Item("JpaItem", 49.99);

        // Store the managed copy returned by merge()
        Item persistedItem = template.requestBody("direct:jpaPersist", item, Item.class);

        Long initialCount = countQuery("SELECT COUNT(i) FROM Item i WHERE i.name = 'JpaItem'", null);
        assertEquals(1L, initialCount);

        persistedItem.setPrice(59.99);
        template.sendBody("direct:jpaUpdate", persistedItem);

        executeInEntityManager(em -> {
            Item found = em.createQuery("SELECT i FROM Item i WHERE i.name = 'JpaItem'", Item.class).getSingleResult();
            assertEquals(59.99, found.getPrice(), "Merged entity price should update in database");
            return null;
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testJpaProducerNamedQueryParameterOverride() {
        persistEntities(
                new Item("OverriddenItem", 10.0),
                new Item("IgnoredItem", 20.0));

        Map<String, Object> params = Map.of("name", "OverriddenItem");
        List<Item> results = template.requestBodyAndHeader("direct:jpaNamedQuery", null,
                HibernateConstants.HIBERNATE_PARAMETERS, params, List.class);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("OverriddenItem", results.get(0).getName());
    }

    @Test
    public void testDetachedEntityPersistThrowsException() {
        Item item = new Item("DetachedItem", 100.0);
        persistEntities(item);

        assertThrows(Exception.class, () -> template.sendBody("direct:jpaStrictPersist", item));
    }

    @Test
    public void testCascadeDeleteCategoryRemovesProducts() {
        Category category = new Category("Books");
        Product p1 = new Product("Java Guide", 45.0, category);
        Product p2 = new Product("Camel Guide", 50.0, category);
        category.addProduct(p1);
        category.addProduct(p2);

        persistEntities(category, p1, p2);

        executeInTransaction(em -> {
            Category managedCategory = em.find(Category.class, category.getId());
            em.remove(managedCategory);
        });

        Long remainingProducts = countQuery("SELECT COUNT(p) FROM Product p WHERE p.category.id = :catId",
                Map.of("catId", category.getId()));
        assertEquals(0L, remainingProducts, "Cascaded products should be removed when parent category is deleted");
    }

    @Test
    public void testDatabaseConstraintViolationTriggersRollback() {
        assertThrows(Exception.class, () -> {
            executeInTransaction(em -> {
                em.createNativeQuery("INSERT INTO category (id, name) VALUES (NULL, NULL)").executeUpdate();
            });
        });

        Long count = countQuery("SELECT COUNT(c) FROM Category c WHERE c.name IS NULL", null);
        assertEquals(0L, count, "Invalid state should not be committed after rollback");
    }

    // =========================================================================
    // BRANCH 2: Hibernate 7.x Native APIs, Advanced Features & Transaction Edge Cases
    // =========================================================================

    @Test
    public void testHibernate7UnwrapSessionAndSessionFactory() {
        org.hibernate.SessionFactory sessionFactory = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class);
        assertNotNull(sessionFactory, "Hibernate 7 SessionFactory unwrap should succeed");

        try (org.hibernate.Session session = sessionFactory.openSession()) {
            assertNotNull(session, "Native Hibernate 7 Session should be active");
            assertTrue(session.isOpen());

            session.beginTransaction();
            Item nativeItem = new Item("H7NativeItem", 199.99);
            session.persist(nativeItem);
            session.getTransaction().commit();
        }

        Long count = countQuery("SELECT COUNT(i) FROM Item i WHERE i.name = 'H7NativeItem'", null);
        assertEquals(1L, count, "Native Hibernate 7 Session persist should save entity");
    }

    @Test
    public void testHibernate7StatelessSessionBulkInsert() {
        org.hibernate.SessionFactory sessionFactory = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class);

        try (org.hibernate.StatelessSession statelessSession = sessionFactory.openStatelessSession()) {
            statelessSession.beginTransaction();

            for (int i = 1; i <= 5; i++) {
                statelessSession.insert(new Item("BatchItem_" + i, 10.0 * i));
            }

            statelessSession.getTransaction().commit();
        }

        Long totalCount = countQuery("SELECT COUNT(i) FROM Item i WHERE i.name LIKE 'BatchItem_%'", null);
        assertEquals(5L, totalCount, "StatelessSession bulk inserts should persist all 5 items");
    }

    @Test
    public void testHibernate7StatelessSessionRollbackOnError() {
        org.hibernate.SessionFactory sessionFactory = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class);

        assertThrows(Exception.class, () -> {
            try (org.hibernate.StatelessSession statelessSession = sessionFactory.openStatelessSession()) {
                org.hibernate.Transaction tx = statelessSession.beginTransaction();
                try {
                    statelessSession.insert(new Item("ValidItem_1", 10.0));
                    statelessSession.insert(new Item("ValidItem_2", 20.0));

                    throw new RuntimeException("Simulated failure during batch insertion");
                } catch (Exception e) {
                    tx.rollback();
                    throw e;
                }
            }
        });

        Long count = countQuery("SELECT COUNT(i) FROM Item i WHERE i.name LIKE 'ValidItem_%'", null);
        assertEquals(0L, count, "StatelessSession transaction rollback should discard all batch items");
    }

    @Test
    public void testHibernate7OptimisticLockingRollback() {
        Item item = new Item("VersionedItem", 100.0);
        persistEntities(item);

        assertThrows(OptimisticLockException.class, () -> {
            executeInTransaction(em1 -> {
                Item i1 = em1.find(Item.class, item.getId());

                executeInTransaction(em2 -> {
                    Item i2 = em2.find(Item.class, item.getId());
                    i2.setPrice(150.0);
                });

                i1.setPrice(200.0);
                em1.flush();
            });
        });

        executeInEntityManager(em -> {
            Item reloaded = em.find(Item.class, item.getId());
            assertEquals(150.0, reloaded.getPrice(),
                    "Price should hold the value committed by the successful concurrent transaction");
            return null;
        });
    }

    @Test
    public void testHibernate7MutationQueryExecution() {
        persistEntities(
                new Item("OldName_1", 15.0),
                new Item("OldName_2", 25.0));

        executeInTransaction(em -> {
            org.hibernate.Session session = em.unwrap(org.hibernate.Session.class);
            int updatedRows
                    = session.createMutationQuery("UPDATE Item i SET i.name = 'RenamedItem' WHERE i.name LIKE 'OldName_%'")
                            .executeUpdate();
            assertEquals(2, updatedRows, "Hibernate 7 MutationQuery should affect 2 rows");
        });

        Long renamedCount = countQuery("SELECT COUNT(i) FROM Item i WHERE i.name = 'RenamedItem'", null);
        assertEquals(2L, renamedCount);
    }

    @Test
    public void testHibernate7CriteriaBuilderQuerying() {
        persistEntities(
                new Item("CriteriaAlpha", 300.0),
                new Item("CriteriaBeta", 100.0));

        executeInEntityManager(em -> {
            org.hibernate.Session session = em.unwrap(org.hibernate.Session.class);
            jakarta.persistence.criteria.CriteriaBuilder cb = session.getCriteriaBuilder();
            jakarta.persistence.criteria.CriteriaQuery<Item> query = cb.createQuery(Item.class);
            jakarta.persistence.criteria.Root<Item> root = query.from(Item.class);

            query.select(root).where(cb.greaterThan(root.get("price"), 200.0));

            List<Item> items = session.createQuery(query).getResultList();
            assertEquals(1, items.size());
            assertEquals("CriteriaAlpha", items.get(0).getName());
            return null;
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                HibernateTestSupport.configureTestRoutes(this);
            }
        };
    }
}
