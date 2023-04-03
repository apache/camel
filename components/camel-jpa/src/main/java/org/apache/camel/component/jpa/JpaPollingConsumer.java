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
package org.apache.camel.component.jpa;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.PollingConsumerSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.jpa.JpaHelper.getTargetEntityManager;

public class JpaPollingConsumer extends PollingConsumerSupport {

    private static final Logger LOG = LoggerFactory.getLogger(JpaPollingConsumer.class);

    private volatile ExecutorService executorService;
    private final EntityManagerFactory entityManagerFactory;
    private final TransactionStrategy transactionStrategy;
    private String query;
    private String namedQuery;
    private String nativeQuery;
    private LockModeType lockModeType = LockModeType.PESSIMISTIC_WRITE;
    private Class<?> resultClass;
    private QueryFactory queryFactory;
    private Map<String, Object> parameters;

    public JpaPollingConsumer(JpaEndpoint endpoint) {
        super(endpoint);
        this.entityManagerFactory = endpoint.getEntityManagerFactory();
        this.transactionStrategy = endpoint.getTransactionStrategy();
    }

    @Override
    public JpaEndpoint getEndpoint() {
        return (JpaEndpoint) super.getEndpoint();
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getNamedQuery() {
        return namedQuery;
    }

    public void setNamedQuery(String namedQuery) {
        this.namedQuery = namedQuery;
    }

    public String getNativeQuery() {
        return nativeQuery;
    }

    public void setNativeQuery(String nativeQuery) {
        this.nativeQuery = nativeQuery;
    }

    public LockModeType getLockModeType() {
        return lockModeType;
    }

    public void setLockModeType(LockModeType lockModeType) {
        this.lockModeType = lockModeType;
    }

    public Class<?> getResultClass() {
        return resultClass;
    }

    public void setResultClass(Class<?> resultClass) {
        this.resultClass = resultClass;
    }

    public QueryFactory getQueryFactory() {
        return queryFactory;
    }

    public void setQueryFactory(QueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    @Override
    public Exchange receive() {
        // resolve the entity manager before evaluating the expression
        final EntityManager entityManager = getTargetEntityManager(null, entityManagerFactory,
                getEndpoint().isUsePassedInEntityManager(), getEndpoint().isSharedEntityManager(), true);

        Exchange exchange = getEndpoint().createExchange();
        exchange.getIn().setHeader(JpaConstants.ENTITY_MANAGER, entityManager);
        transactionStrategy.executeInTransaction(new Runnable() {
            @Override
            public void run() {
                if (getEndpoint().isJoinTransaction()) {
                    entityManager.joinTransaction();
                }

                Query innerQuery = getQueryFactory().createQuery(entityManager);
                configureParameters(innerQuery);

                if (getEndpoint().isConsumeLockEntity()) {
                    innerQuery.setLockMode(getLockModeType());
                }

                LOG.trace("Created query {}", innerQuery);

                Object answer;

                try {
                    List<?> results = innerQuery.getResultList();

                    if (results != null && results.size() == 1) {
                        // we only have 1 entity so return that
                        answer = results.get(0);
                    } else {
                        // we have more data so return a list
                        answer = results;
                    }

                    // commit
                    LOG.debug("Flushing EntityManager");
                    entityManager.flush();

                    // must clear after flush
                    entityManager.clear();

                } catch (PersistenceException e) {
                    LOG.info("Disposing EntityManager {} on {} due to coming transaction rollback", entityManager, this);

                    entityManager.close();

                    throw e;
                }

                exchange.getIn().setBody(answer);
            }

        });

        return exchange;
    }

    @Override
    public Exchange receiveNoWait() {
        // call receive as-is
        return receive();
    }

    @Override
    public Exchange receive(long timeout) {
        // need to use a thread pool to perform the task so we can support timeout
        if (executorService == null) {
            executorService = getEndpoint().getComponent().getOrCreatePollingConsumerExecutorService();
        }

        // the task is the receive method
        Future<Exchange> future = executorService.submit((Callable<Exchange>) this::receive);
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (ExecutionException | InterruptedException e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        } catch (TimeoutException e) {
            // ignore as we hit timeout then return null
        }

        return null;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (queryFactory == null) {
            queryFactory = createQueryFactory();
            if (queryFactory == null) {
                throw new IllegalArgumentException(
                        "No queryType property configured on this consumer, nor an entityType configured on the endpoint so cannot consume");
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop

    }

    protected void configureParameters(Query query) {
        int maxResults = getEndpoint().getMaximumResults();
        if (maxResults > 0) {
            query.setMaxResults(maxResults);
        }
        // setup the parameter
        if (parameters != null) {
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
            }
        }
    }

    protected Exchange createExchange(Object result, EntityManager entityManager) {
        Exchange exchange = getEndpoint().createExchange();
        exchange.getIn().setBody(result);
        exchange.getIn().setHeader(JpaConstants.ENTITY_MANAGER, entityManager);
        return exchange;
    }

    protected QueryFactory createQueryFactory() {
        if (query != null) {
            return QueryBuilder.query(query);
        } else if (namedQuery != null) {
            return QueryBuilder.namedQuery(namedQuery);
        } else if (nativeQuery != null) {
            if (resultClass != null) {
                return QueryBuilder.nativeQuery(nativeQuery, resultClass);
            } else {
                return QueryBuilder.nativeQuery(nativeQuery);
            }
        } else {
            Class<?> entityType = getEndpoint().getEntityType();

            if (entityType == null) {
                return null;
            } else {
                // Check if we have a property name on the @Entity annotation
                String name = getEntityName(entityType);
                if (name != null) {
                    return QueryBuilder.query("select x from " + name + " x");
                } else {
                    // Remove package name of the entity to be conform with JPA 1.0 spec
                    return QueryBuilder.query("select x from " + entityType.getSimpleName() + " x");
                }
            }
        }
    }

    protected String getEntityName(Class<?> clazz) {
        Entity entity = clazz.getAnnotation(Entity.class);

        // Check if the property name has been defined for Entity annotation
        if (entity != null && !entity.name().isEmpty()) {
            return entity.name();
        } else {
            return null;
        }
    }
}
