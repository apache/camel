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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Query;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.jpa.JpaConstants;
import org.apache.camel.component.jpa.JpaHelper;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Hibernate consumer for polling database records via JPA or Native Hibernate Sessions.
 */
public class HibernateConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(HibernateConsumer.class);

    private final HibernateEndpoint endpoint;

    public HibernateConsumer(HibernateEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected int poll() throws Exception {
        shutdownRunningTask = null;
        pendingExchanges = 0;

        try {
            if (endpoint.isJpaBacked()) {
                return pollJpa();
            } else {
                return pollNativeHibernate();
            }
        } catch (Exception e) {
            LOG.error("Polling failed on endpoint {}: {}", endpoint.getEndpointUri(), e.getMessage(), e);
            throw e;
        }
    }

    private int pollJpa() throws Exception {
        EntityManager entityManager = JpaHelper.getTargetEntityManager(
                null,
                endpoint.getEntityManagerFactory(),
                false,
                true,
                false);

        final int[] messagePolled = { 0 };

        try {
            endpoint.getTransactionStrategy().executeInTransaction(() -> {
                try {
                    Queue<DataHolder> exchanges = createExchanges(entityManager);
                    messagePolled[0] = processBatch(CastUtils.cast(exchanges));

                    if (endpoint.isConsumeDelete()) {
                        entityManager.flush();
                    }
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw RuntimeCamelException.wrapRuntimeCamelException(e);
                }
            });
        } catch (Exception e) {
            LOG.error("JPA transaction processing failed: {}", e.getMessage(), e);
            throw e;
        }

        return messagePolled[0];
    }

    private int pollNativeHibernate() throws Exception {
        SessionFactory sessionFactory = endpoint.getResolvedSessionFactory();

        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();

            try {
                Queue<DataHolder> exchanges = createExchanges(session);
                int messagePolled = processBatch(CastUtils.cast(exchanges));

                if (endpoint.isConsumeDelete()) {
                    session.flush();
                }

                transaction.commit();
                return messagePolled;
            } catch (Exception e) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw e;
            }
        }
    }

    private Queue<DataHolder> createExchanges(EntityManager entityManager) {
        Query query = createQuery(entityManager);
        configureParameters(query);

        List<?> results = query.getResultList();
        forceConsumerAsReady();

        Queue<DataHolder> exchanges = new LinkedList<>();
        for (Object result : results) {
            Exchange exchange = createExchange(result, entityManager);

            DataHolder holder = new DataHolder();
            holder.exchange = exchange;
            holder.entity = result;
            holder.entityManager = entityManager;

            exchanges.add(holder);
        }

        return exchanges;
    }

    private Queue<DataHolder> createExchanges(Session session) {
        org.hibernate.query.Query<?> query = createQuery(session);
        configureParameters(query);

        List<?> results = query.getResultList();
        forceConsumerAsReady();

        Queue<DataHolder> exchanges = new LinkedList<>();
        for (Object result : results) {
            Exchange exchange = createExchange(result, null);

            DataHolder holder = new DataHolder();
            holder.exchange = exchange;
            holder.entity = result;
            holder.session = session;

            exchanges.add(holder);
        }

        return exchanges;
    }

    private Query createQuery(EntityManager entityManager) {
        if (endpoint.getNamedQuery() != null) {
            return entityManager.createNamedQuery(endpoint.getNamedQuery());
        }

        if (endpoint.getNativeQuery() != null) {
            return entityManager.createNativeQuery(endpoint.getNativeQuery());
        }

        if (endpoint.getQuery() != null) {
            return entityManager.createQuery(endpoint.getQuery());
        }

        if (endpoint.getEntityType() != null) {
            return entityManager.createQuery("FROM " + endpoint.getEntityType().getName());
        }

        throw new IllegalArgumentException(
                "No query or entityType configured for HibernateConsumer");
    }

    private org.hibernate.query.Query<?> createQuery(Session session) {
        if (endpoint.getNamedQuery() != null) {
            return session.createNamedQuery(endpoint.getNamedQuery(), Object.class);
        }

        if (endpoint.getNativeQuery() != null) {
            return session.createNativeQuery(endpoint.getNativeQuery(), Object.class);
        }

        if (endpoint.getQuery() != null) {
            return session.createQuery(endpoint.getQuery(), Object.class);
        }

        if (endpoint.getEntityType() != null) {
            return session.createQuery("FROM " + endpoint.getEntityType().getName(), Object.class);
        }

        throw new IllegalArgumentException(
                "No query or entityType configured for HibernateConsumer");
    }

    private void configureParameters(Query query) {
        applyParametersToQuery(query::setParameter);

        if (endpoint.getMaximumResults() > 0) {
            query.setMaxResults(endpoint.getMaximumResults());
        }
    }

    private void configureParameters(org.hibernate.query.Query<?> query) {
        applyParametersToQuery(query::setParameter);

        if (endpoint.getMaximumResults() > 0) {
            query.setMaxResults(endpoint.getMaximumResults());
        }
    }

    @FunctionalInterface
    private interface ParameterBinder {
        void bind(String name, Object value);
    }

    private void applyParametersToQuery(ParameterBinder binder) {
        Map<String, Object> params = endpoint.getParameters();
        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                binder.bind(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        for (Object exchangeObject : exchanges) {
            DataHolder holder = (DataHolder) exchangeObject;
            Exchange exchange = holder.exchange;

            try {
                getProcessor().process(exchange);

                if (exchange.getException() != null) {
                    throw exchange.getException();
                }

                if (endpoint.isConsumeDelete()) {
                    deleteEntity(holder);
                }
            } finally {
                releaseExchange(exchange, false);
            }
        }

        return total;
    }

    private void deleteEntity(DataHolder holder) {
        if (holder.entity == null) {
            return;
        }

        if (holder.entityManager != null) {
            EntityManager entityManager = holder.entityManager;
            Object entityToDelete = holder.entity;

            if (!entityManager.contains(entityToDelete)) {
                entityToDelete = entityManager.merge(entityToDelete);
            }

            detachRelationshipsGenerically(entityToDelete);
            entityManager.remove(entityToDelete);

        } else if (holder.session != null) {
            Session session = holder.session;
            Object entityToDelete = holder.entity;

            if (!session.contains(entityToDelete)) {
                entityToDelete = session.merge(entityToDelete);
            }

            detachRelationshipsGenerically(entityToDelete);
            session.remove(entityToDelete);
        }
    }

    /**
     * Inspects entity fields dynamically via reflection for standard JPA relationship annotations
     * (@ManyToOne, @OneToOne) and detaches the entity from parent collections to prevent re-persisting during flush.
     */
    private void detachRelationshipsGenerically(Object entity) {
        if (entity == null) {
            return;
        }

        Class<?> clazz = entity.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(OneToOne.class)) {
                    try {
                        field.setAccessible(true);
                        Object parent = field.get(entity);
                        if (parent != null) {
                            removeFromParentCollections(parent, entity);
                            field.set(entity, null);
                        }
                    } catch (Exception e) {
                        LOG.debug("Could not automatically unbind relationship field '{}' on entity {}: {}",
                                field.getName(), entity.getClass().getName(), e.getMessage());
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * Inspects fields and getter methods on the parent entity to find any collection holding the child entity and
     * removes it.
     */
    private void removeFromParentCollections(Object parent, Object child) {
        Class<?> parentClazz = parent.getClass();

        // 1. Check parent fields directly
        while (parentClazz != null && parentClazz != Object.class) {
            for (Field parentField : parentClazz.getDeclaredFields()) {
                if (Collection.class.isAssignableFrom(parentField.getType())) {
                    try {
                        parentField.setAccessible(true);
                        Collection<?> collection = (Collection<?>) parentField.get(parent);
                        if (collection != null) {
                            collection.remove(child);
                        }
                    } catch (Exception ignored) {
                        // Ignore reflection accessibility restrictions
                    }
                }
            }
            parentClazz = parentClazz.getSuperclass();
        }

        // 2. Check getter methods on parent
        for (Method method : parent.getClass().getMethods()) {
            if (method.getName().startsWith("get") && method.getParameterCount() == 0
                    && Collection.class.isAssignableFrom(method.getReturnType())) {
                try {
                    Collection<?> collection = (Collection<?>) method.invoke(parent);
                    if (collection != null) {
                        collection.remove(child);
                    }
                } catch (Exception ignored) {
                    // Ignore invocation failures
                }
            }
        }
    }

    private Exchange createExchange(Object result, EntityManager entityManager) {
        Exchange exchange = createExchange(false);
        exchange.getIn().setBody(result);

        if (entityManager != null) {
            exchange.getIn().setHeader(JpaConstants.ENTITY_MANAGER, entityManager);
        }

        return exchange;
    }

    private static final class DataHolder {
        private Exchange exchange;
        private Object entity;
        private EntityManager entityManager;
        private Session session;
    }
}
