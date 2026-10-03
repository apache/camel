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
import java.util.Locale;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.jpa.JpaHelper;
import org.apache.camel.support.DefaultProducer;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

public class HibernateProducer extends DefaultProducer {

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

        endpoint.getTransactionStrategy().executeInTransaction(() -> {
            try {
                doProcess(exchange, entityManager);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        });
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

    private void doProcess(Exchange exchange, Session session) {
        if (endpoint.getQuery() != null
                || endpoint.getNamedQuery() != null
                || endpoint.getNativeQuery() != null
                || exchange.getIn().getHeader(HibernateConstants.HIBERNATE_QUERY) != null) {
            executeQuery(exchange, session);
        } else {
            executeEntityOperation(exchange, session);
        }
    }

    private void executeEntityOperation(Exchange exchange, EntityManager entityManager) {
        Object body = exchange.getIn().getBody();
        if (body == null) {
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

    private void executeEntityOperation(Exchange exchange, Session session) {
        Object body = exchange.getIn().getBody();
        if (body == null) {
            return;
        }

        Object result;
        if (body instanceof Collection<?> collection) {
            List<Object> list = new ArrayList<>(collection.size());
            for (Object item : collection) {
                if (endpoint.isUsePersist()) {
                    session.persist(item);
                    list.add(item);
                } else {
                    list.add(session.merge(item));
                }
            }
            result = list;
        } else {
            if (endpoint.isUsePersist()) {
                session.persist(body);
                result = body;
            } else {
                result = session.merge(body);
            }
        }

        session.flush();
        exchange.getMessage().setBody(result);
    }

    private void executeQuery(Exchange exchange, EntityManager entityManager) {
        String hql = exchange.getIn().getHeader(
                HibernateConstants.HIBERNATE_QUERY,
                String.class);

        Query query;

        if (hql != null) {
            query = entityManager.createQuery(hql);
        } else if (endpoint.getNamedQuery() != null) {
            query = entityManager.createNamedQuery(endpoint.getNamedQuery());
        } else if (endpoint.getNativeQuery() != null) {
            query = entityManager.createNativeQuery(endpoint.getNativeQuery());
        } else {
            query = entityManager.createQuery(endpoint.getQuery());
        }

        configureQuery(exchange, query);

        if (isExecuteUpdateQuery(hql, endpoint.getQuery())) {
            int updated = query.executeUpdate();
            exchange.getMessage().setBody(updated);
        } else {
            exchange.getMessage().setBody(query.getResultList());
        }
    }

    private void executeQuery(Exchange exchange, Session session) {
        String hql = exchange.getIn().getHeader(
                HibernateConstants.HIBERNATE_QUERY,
                String.class);

        org.hibernate.query.Query<?> query;

        if (hql != null) {
            query = session.createQuery(hql, Object.class);
        } else if (endpoint.getNamedQuery() != null) {
            query = session.createNamedQuery(endpoint.getNamedQuery(), Object.class);
        } else if (endpoint.getNativeQuery() != null) {
            query = session.createNativeQuery(endpoint.getNativeQuery(), Object.class);
        } else {
            query = session.createQuery(endpoint.getQuery(), Object.class);
        }

        configureQuery(exchange, query);

        if (isExecuteUpdateQuery(hql, endpoint.getQuery())) {
            int updated = query.executeUpdate();
            exchange.getMessage().setBody(updated);
        } else {
            exchange.getMessage().setBody(query.getResultList());
        }
    }

    private boolean isExecuteUpdateQuery(String queryHeader, String endpointQuery) {
        String q = queryHeader != null ? queryHeader : endpointQuery;
        if (q == null) {
            return false;
        }
        String normalized = q.trim().toUpperCase(Locale.ENGLISH);
        return normalized.startsWith("DELETE") || normalized.startsWith("UPDATE");
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
