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

        Session session = endpoint.getTenantIdentifier() == null
                ? endpoint.getSessionFactory().openSession()
                : endpoint.getSessionFactory().withOptions()
                        .tenantIdentifier(endpoint.getTenantIdentifier())
                        .openSession();

        Transaction transaction = session.beginTransaction();

        try {
            if (endpoint.getFilters() != null) {
                endpoint.getFilters().forEach((filterName, parameters) -> {
                    var filter = session.enableFilter(filterName);
                    if (parameters != null) {
                        parameters.forEach(filter::setParameter);
                    }
                });
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = exchange.getMessage().getHeader(
                    HibernateConstants.HIBERNATE_PARAMETERS, Map.class);

            if (endpoint.getNaturalIdParameters() != null) {
                Object entity = session.find(
                        endpoint.getEntityType(),
                        endpoint.getNaturalIdParameters(),
                        KeyType.NATURAL);

                exchange.getMessage().setBody(entity);

                transaction.commit();
                session.close();
            } else if (endpoint.getSelectionQuery() != null) {
                SelectionQuery<?> query = session.createSelectionQuery(
                        endpoint.getSelectionQuery(), endpoint.getEntityType());

                session.setDefaultReadOnly(endpoint.isReadOnly());
                query.setReadOnly(endpoint.isReadOnly());

                if (parameters != null) {
                    parameters.forEach(query::setParameter);
                }

                if (endpoint.isStreaming()) {
                    exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                        @Override
                        public void onComplete(Exchange exchange) {
                            closeStreamingSession(session, transaction, false);
                        }

                        @Override
                        public void onFailure(Exchange exchange) {
                            closeStreamingSession(session, transaction, true);
                        }
                    });

                    Stream<?> stream = query.getResultStream();

                    exchange.getMessage().setBody(stream.onClose(
                            () -> closeStreamingSession(session, transaction, false)));

                    return;
                }

                exchange.getMessage().setBody(query.getResultList());

                transaction.commit();
                session.close();
            } else {
                MutationQuery query = session.createMutationQuery(endpoint.getMutationQuery());

                if (parameters != null) {
                    parameters.forEach(query::setParameter);
                }

                exchange.getMessage().setBody(query.execute());

                transaction.commit();
                session.close();
            }
        } catch (Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            session.close();
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
        try (StatelessSession session = endpoint.getSessionFactory().openStatelessSession()) {
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
