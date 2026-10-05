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

import java.util.Map;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
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

    public HibernateProducer(HibernateEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (endpoint.getStatelessOperation() != null) {
            processStateless(exchange);
            return;
        }

        Session session = exchange.getProperty(HibernateConstants.HIBERNATE_SESSION, Session.class);
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
            if (endpoint.getFilters() != null) {
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
                        endpoint.getNaturalIdParameters(),
                        KeyType.NATURAL);

                exchange.getMessage().setBody(entity);

                if (sessionOwned) {
                    transaction.commit();
                    activeSession.close();
                }
            } else if (endpoint.getSelectionQuery() != null) {
                SelectionQuery<?> query = activeSession.createSelectionQuery(
                        endpoint.getSelectionQuery(), endpoint.getEntityType());

                activeSession.setDefaultReadOnly(endpoint.isReadOnly());
                query.setReadOnly(endpoint.isReadOnly());

                if (parameters != null) {
                    parameters.forEach(query::setParameter);
                }

                if (endpoint.isStreaming()) {
                    if (sessionOwned) {
                        exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                            @Override
                            public void onComplete(Exchange exchange) {
                                closeStreamingSession(activeSession, transaction, false);
                            }

                            @Override
                            public void onFailure(Exchange exchange) {
                                closeStreamingSession(activeSession, transaction, true);
                            }
                        });
                    }

                    Stream<?> stream = query.getResultStream();

                    if (sessionOwned) {
                        exchange.getMessage().setBody(stream.onClose(
                                () -> closeStreamingSession(activeSession, transaction, false)));
                    } else {
                        exchange.getMessage().setBody(stream);
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

    private void closeStreamingSession(Session session, Transaction transaction, boolean rollback) {
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

                if ("insert".equals(endpoint.getStatelessOperation())) {
                    session.insert(body);
                } else if ("upsert".equals(endpoint.getStatelessOperation())) {
                    session.upsert(body);
                } else {
                    throw new IllegalArgumentException(
                            "Invalid statelessOperation: " + endpoint.getStatelessOperation());
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
