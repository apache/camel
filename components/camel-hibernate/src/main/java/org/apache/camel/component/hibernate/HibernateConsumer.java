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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledPollConsumer;
import org.hibernate.LockMode;
import org.hibernate.Session;
import org.hibernate.Timeouts;
import org.hibernate.Transaction;
import org.hibernate.query.SelectionQuery;

public class HibernateConsumer extends ScheduledPollConsumer {

    private final HibernateEndpoint endpoint;

    public HibernateConsumer(HibernateEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected int poll() throws Exception {
        if (endpoint.getSelectionQuery() == null || endpoint.getSelectionQuery().isBlank()) {
            throw new IllegalArgumentException("Hibernate consumer requires selectionQuery");
        }

        Session session = endpoint.getTenantIdentifier() == null
                ? endpoint.getSessionFactory().openSession()
                : endpoint.getSessionFactory().withOptions()
                        .tenantIdentifier(endpoint.getTenantIdentifier())
                        .openSession();

        try (session) {
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

                SelectionQuery<?> query = session.createSelectionQuery(
                        endpoint.getSelectionQuery(), endpoint.getEntityType());

                if (endpoint.isReadOnly()) {
                    session.setDefaultReadOnly(true);
                    query.setReadOnly(true);
                }

                if (endpoint.getMaximumResults() > 0) {
                    query.setMaxResults(endpoint.getMaximumResults());
                }

                if (endpoint.isSkipLocked()) {
                    query.setHibernateLockMode(LockMode.PESSIMISTIC_WRITE);
                    query.setLockTimeout(Timeouts.SKIP_LOCKED);
                }

                List<?> results = query.getResultList();

                for (Object result : results) {
                    Exchange exchange = null;
                    try {
                        exchange = createExchange(false);
                        exchange.getMessage().setBody(result);
                        exchange.setProperty(HibernateConstants.HIBERNATE_SESSION, session);

                        getProcessor().process(exchange);

                        if (exchange.getException() != null) {
                            handleException(exchange.getException());
                        }
                    } catch (Exception e) {
                        handleException(e);
                    } finally {
                        releaseExchange(exchange, false);
                    }
                }

                transaction.commit();
                return results.size();
            } catch (Exception e) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw e;
            }
        }
    }
}
