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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.SynchronizationAdapter;
import org.hibernate.KeyType;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.SelectionQuery;

public class HibernateProducer extends DefaultProducer {

    private final HibernateEndpoint endpoint;
    private final Map<String, Object> naturalIdParameters = new LinkedHashMap<>();

    public HibernateProducer(HibernateEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        Language simple = endpoint.getCamelContext().resolveLanguage("simple");

        naturalIdParameters.clear();
        if (endpoint.getNaturalIdParameters() != null) {
            endpoint.getNaturalIdParameters().forEach((key, value) -> {
                if (value instanceof String str) {
                    naturalIdParameters.put(key, simple.createExpression(str));
                } else {
                    naturalIdParameters.put(key, value);
                }
            });
        }
    }

    private Map<String, Object> resolveNaturalIdParameters(Exchange exchange, Map<String, Object> headerParameters) {
        Map<String, Object> resolved = new LinkedHashMap<>();

        naturalIdParameters.forEach((key, value) -> {
            if (value instanceof Expression expression) {
                resolved.put(key, expression.evaluate(exchange, Object.class));
            } else {
                resolved.put(key, value);
            }
        });

        if (headerParameters != null) {
            // Header values are message data and must never be evaluated as Simple expressions.
            resolved.putAll(headerParameters);
        }

        return resolved;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (endpoint.isStreaming() && exchange.isTransacted()) {
            throw new IllegalArgumentException(
                    "streaming=true is not supported inside a transacted exchange because streams can outlive route transactions.");
        }

        if (exchange.isTransacted() && !endpoint.isJta()) {
            throw new IllegalStateException(
                    "Hibernate producer does not support transacted() exchanges with a resource-local SessionFactory.");
        }

        if (endpoint.getStatelessOperation() != null) {
            processStateless(exchange);
            return;
        }

        HibernateSessionContext sessionContext = exchange.getExchangeExtension()
                .getSafeCopyProperty(HibernateConstants.HIBERNATE_SESSION_CONTEXT, HibernateSessionContext.class);

        Session session = sessionContext == null
                ? null
                : sessionContext.getSession(
                        endpoint.getSessionFactory(),
                        endpoint.getTenantIdentifier(),
                        endpoint.getFilters());

        boolean sessionOwned = session == null;

        if (sessionOwned) {
            session = endpoint.getTenantIdentifier() == null
                    ? endpoint.getSessionFactory().openSession()
                    : endpoint.getSessionFactory().withOptions()
                            .tenantIdentifier(endpoint.getTenantIdentifier())
                            .openSession();
        }

        final Session activeSession = session;
        final Transaction transaction = sessionOwned
                ? activeSession.beginTransaction()
                : activeSession.getTransaction();

        try {
            if (sessionOwned && endpoint.getFilters() != null) {
                endpoint.getFilters().forEach((filterName, parameters) -> {
                    var filter = activeSession.enableFilter(filterName);
                    if (parameters != null) {
                        parameters.forEach(filter::setParameter);
                    }
                });
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = exchange.getMessage().getHeader(
                    HibernateConstants.HIBERNATE_PARAMETERS, Map.class);

            if (endpoint.getNaturalIdParameters() != null) {
                Object entity = activeSession.find(
                        endpoint.getEntityType(),
                        resolveNaturalIdParameters(exchange, parameters),
                        KeyType.NATURAL);

                exchange.getMessage().setBody(entity);

                if (sessionOwned) {
                    transaction.commit();
                    activeSession.close();
                }
            } else if (endpoint.getSelectionQuery() != null) {
                SelectionQuery<?> query = activeSession.createSelectionQuery(
                        endpoint.getSelectionQuery(), endpoint.getEntityType());

                if (sessionOwned) {
                    activeSession.setDefaultReadOnly(endpoint.isReadOnly());
                }
                query.setReadOnly(endpoint.isReadOnly());

                if (parameters != null) {
                    parameters.forEach(query::setParameter);
                }

                if (endpoint.isStreaming()) {
                    if (sessionOwned) {
                        AtomicBoolean streamingSessionClosed = new AtomicBoolean();

                        exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                            @Override
                            public void onComplete(Exchange exchange) {
                                closeStreamingSession(
                                        activeSession, transaction, false, streamingSessionClosed);
                            }

                            @Override
                            public void onFailure(Exchange exchange) {
                                closeStreamingSession(
                                        activeSession, transaction, true, streamingSessionClosed);
                            }
                        });

                        Stream<?> stream = query.getResultStream();
                        exchange.getMessage().setBody(stream.onClose(
                                () -> closeStreamingSession(
                                        activeSession, transaction, false, streamingSessionClosed)));
                    } else {
                        exchange.getMessage().setBody(query.getResultStream());
                    }

                    return;
                }

                exchange.getMessage().setBody(query.getResultList());

                if (sessionOwned) {
                    transaction.commit();
                    activeSession.close();
                }
            } else {
                MutationQuery query = activeSession.createMutationQuery(endpoint.getMutationQuery());

                if (parameters != null) {
                    parameters.forEach(query::setParameter);
                }

                exchange.getMessage().setBody(query.execute());

                if (sessionOwned) {
                    transaction.commit();
                    activeSession.close();
                }
            }
        } catch (Exception e) {
            if (sessionOwned && transaction.isActive()) {
                transaction.rollback();
            }
            if (sessionOwned) {
                activeSession.close();
            }
            throw e;
        }
    }

    private void closeStreamingSession(
            Session session, Transaction transaction, boolean rollback, AtomicBoolean closed) {

        if (!closed.compareAndSet(false, true)) {
            return;
        }

        try {
            if (transaction.isActive()) {
                if (rollback) {
                    transaction.rollback();
                } else {
                    transaction.commit();
                }
            }
        } finally {
            session.close();
        }
    }

    private void processStateless(Exchange exchange) {
        StatelessSession session = endpoint.getTenantIdentifier() == null
                ? endpoint.getSessionFactory().openStatelessSession()
                : endpoint.getSessionFactory()
                        .withStatelessOptions()
                        .tenantIdentifier(endpoint.getTenantIdentifier())
                        .openStatelessSession();

        try (session) {
            Transaction transaction = session.beginTransaction();

            try {
                Object body = exchange.getMessage().getBody();

                switch (endpoint.getStatelessOperation()) {
                    case INSERT:
                        session.insert(body);
                        break;
                    case UPSERT:
                        session.upsert(body);
                        break;
                }

                transaction.commit();
            } catch (Exception e) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw e;
            }
        }
    }
}
