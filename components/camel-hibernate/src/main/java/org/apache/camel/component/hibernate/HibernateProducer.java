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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.jpa.JpaHelper;
import org.apache.camel.support.DefaultProducer;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HibernateProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(HibernateProducer.class);

    private final HibernateEndpoint endpoint;

    public HibernateProducer(HibernateEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (endpoint.isJpaBacked()) {
            processWithJpaTransaction(exchange);
        } else {
            processWithHibernateTransaction(exchange);
        }
    }

    private void processWithJpaTransaction(Exchange exchange) {
        EntityManager entityManager = JpaHelper.getTargetEntityManager(
                exchange,
                endpoint.getEntityManagerFactory(),
                false,
                true,
                false);

        try {
            endpoint.getTransactionStrategy().executeInTransaction(() -> {
                try {
                    doProcess(exchange, entityManager);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw RuntimeCamelException.wrapRuntimeCamelException(e);
                }
            });
        } finally {
            if (entityManager.isOpen()) {
                entityManager.close();
            }
        }
    }

    private void processWithHibernateTransaction(Exchange exchange) throws Exception {
        SessionFactory sessionFactory = endpoint.getResolvedSessionFactory();

        try (Session session = sessionFactory.openSession()) {
            org.hibernate.Transaction transaction = session.beginTransaction();

            try {
                doProcess(exchange, session);
                transaction.commit();
            } catch (Exception e) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw e;
            }
        }
    }

    private void doProcess(Exchange exchange, EntityManager entityManager) {
        if (endpoint.getQuery() != null
                || endpoint.getNamedQuery() != null
                || endpoint.getNativeQuery() != null
                || exchange.getIn().getHeader(HibernateConstants.HIBERNATE_QUERY) != null) {
            executeQuery(exchange, entityManager);
        } else {
            executeEntityOperation(exchange, entityManager);
        }
    }

    private void executeEntityOperation(Exchange exchange, EntityManager entityManager) {
        Object body = exchange.getIn().getBody();
        if (body == null) {
            LOG.warn("No entity body to persist or merge on endpoint {}", endpoint);
            exchange.getMessage().setBody(null);
            return;
        }

        Object result;
        if (body instanceof Collection<?> collection) {
            List<Object> list = new ArrayList<>(collection.size());
            for (Object item : collection) {
                if (endpoint.isUsePersist()) {
                    entityManager.persist(item);
                    list.add(item);
                } else {
                    list.add(entityManager.merge(item));
                }
            }
            result = list;
        } else {
            if (endpoint.isUsePersist()) {
                entityManager.persist(body);
                result = body;
            } else {
                result = entityManager.merge(body);
            }
        }

        entityManager.flush();
        exchange.getMessage().setBody(result);
    }

    private void executeQuery(Exchange exchange, EntityManager entityManager) {
        String queryHeader = exchange.getIn().getHeader(
                HibernateConstants.HIBERNATE_QUERY,
                String.class);

        Query query;
        if (queryHeader != null) {
            query = entityManager.createQuery(queryHeader);
        } else if (endpoint.getNamedQuery() != null) {
            query = entityManager.createNamedQuery(endpoint.getNamedQuery());
        } else if (endpoint.getNativeQuery() != null) {
            query = entityManager.createNativeQuery(endpoint.getNativeQuery());
        } else {
            query = entityManager.createQuery(endpoint.getQuery());
        }

        configureQuery(exchange, query);

        if (isExecuteUpdateQuery(queryHeader)) {
            int updated = query.executeUpdate();
            exchange.getMessage().setBody(updated);
        } else {
            exchange.getMessage().setBody(query.getResultList());
        }
    }

    /**
     * Detects mutation queries from inspectable JPQL/SQL text only. Named query names are never treated as query text;
     * named mutation queries require {@code useExecuteUpdate=true}.
     */
    private boolean isExecuteUpdateQuery(String queryHeader) {
        Boolean configured = endpoint.getUseExecuteUpdate();
        if (configured != null) {
            return configured;
        }
        if (queryHeader != null) {
            return isUpdateQuery(queryHeader);
        }
        if (endpoint.getQuery() != null) {
            return isUpdateQuery(endpoint.getQuery());
        }
        if (endpoint.getNativeQuery() != null) {
            return isUpdateQuery(endpoint.getNativeQuery());
        }
        return false;
    }

    private static boolean isUpdateQuery(String queryText) {
        String trimmed = queryText.stripLeading();
        return trimmed.regionMatches(true, 0, "insert", 0, 6)
                || trimmed.regionMatches(true, 0, "update", 0, 6)
                || trimmed.regionMatches(true, 0, "delete", 0, 6);
    }

    @SuppressWarnings("unchecked")
    private void configureQuery(Exchange exchange, Query query) {
        Map<String, Object> params = exchange.getIn().getHeader(
                HibernateConstants.HIBERNATE_PARAMETERS,
                Map.class);

        if (params == null) {
            params = endpoint.getParameters();
        }

        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
            }
        }

        if (endpoint.getMaximumResults() > 0) {
            query.setMaxResults(endpoint.getMaximumResults());
        }
    }
}
