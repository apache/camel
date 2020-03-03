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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.language.simple.SimpleLanguage;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.apache.camel.component.jpa.JpaHelper.getTargetEntityManager;

public class JpaProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(JpaProducer.class);

    private final EntityManagerFactory entityManagerFactory;
    private final TransactionTemplate transactionTemplate;
    private final Expression expression;
    private String query;
    private String namedQuery;
    private String nativeQuery;
    private boolean findEntity;
    private Map<String, Object> parameters;
    private Class<?> resultClass;
    private QueryFactory queryFactory;
    private Boolean useExecuteUpdate;

    public JpaProducer(JpaEndpoint endpoint, Expression expression) {
        super(endpoint);
        this.expression = expression;
        this.entityManagerFactory = endpoint.getEntityManagerFactory();
        this.transactionTemplate = endpoint.createTransactionTemplate();
    }

    @Override
    public JpaEndpoint getEndpoint() {
        return (JpaEndpoint) super.getEndpoint();
    }

    public QueryFactory getQueryFactory() {
        if (queryFactory == null) {
            if (query != null) {
                queryFactory = QueryBuilder.query(query);
            } else if (namedQuery != null) {
                queryFactory = QueryBuilder.namedQuery(namedQuery);
            } else if (nativeQuery != null) {
                if (resultClass != null) {
                    queryFactory = QueryBuilder.nativeQuery(nativeQuery, resultClass);
                } else {
                    queryFactory = QueryBuilder.nativeQuery(nativeQuery);
                }
            }
        }
        return queryFactory;
    }

    public void setQueryFactory(QueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public void setParameters(Map<String, Object> params) {
        this.parameters = params;
    }

    public Map<String, Object> getParameters() {
        return parameters;
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

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public boolean isFindEntity() {
        return findEntity;
    }

    public void setFindEntity(boolean findEntity) {
        this.findEntity = findEntity;
    }

    public Class<?> getResultClass() {
        return resultClass;
    }

    public void setResultClass(Class<?> resultClass) {
        this.resultClass = resultClass;
    }

    public void setUseExecuteUpdate(Boolean executeUpdate) {
        this.useExecuteUpdate = executeUpdate;
    }

    public boolean isUseExecuteUpdate() {
        if (useExecuteUpdate == null) {
            if (query != null) {
                if (query.regionMatches(true, 0, "select", 0, 6)) {
                    useExecuteUpdate = false;
                } else {
                    useExecuteUpdate = true;
                }
            } else if (nativeQuery != null) {
                if (nativeQuery.regionMatches(true, 0, "select", 0, 6)) {
                    useExecuteUpdate = false;
                } else {
                    useExecuteUpdate = true;
                }
            } else {
                useExecuteUpdate = false;
            }
        }
        return useExecuteUpdate;
    }

    @Override
    public void process(final Exchange exchange) {
        // resolve the entity manager before evaluating the expression
        final EntityManager entityManager = getTargetEntityManager(exchange, entityManagerFactory,
                getEndpoint().isUsePassedInEntityManager(), getEndpoint().isSharedEntityManager(), true);

        if (findEntity) {
            processFind(exchange, entityManager);
        } else if (getQueryFactory() != null) {
            processQuery(exchange, entityManager);
        } else {
            processEntity(exchange, entityManager);
        }
    }

    protected void processQuery(Exchange exchange, EntityManager entityManager) {
        Query query = getQueryFactory().createQuery(entityManager);
        configureParameters(query, exchange);

        transactionTemplate.execute(new TransactionCallback<Object>() {
            public Object doInTransaction(TransactionStatus status) {
                if (getEndpoint().isJoinTransaction()) {
                    entityManager.joinTransaction();
                }

                Object answer = isUseExecuteUpdate() ? query.executeUpdate() : query.getResultList();
                Message target = exchange.getPattern().isOutCapable() ? exchange.getOut() : exchange.getIn();
                target.setBody(answer);

                if (getEndpoint().isFlushOnSend()) {
                    entityManager.flush();
                }

                return null;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void configureParameters(Query query, Exchange exchange) {
        int maxResults = getEndpoint().getMaximumResults();
        if (maxResults > 0) {
            query.setMaxResults(maxResults);
        }
        // setup the parameters
        Map<String, ?> params;
        if (parameters != null) {
            params = parameters;
        } else {
            params = exchange.getIn().getHeader(JpaConstants.JPA_PARAMETERS_HEADER, Map.class);
        }
        if (params != null) {
            params.forEach((key, value) -> {
                Object resolvedValue = value;
                if (value instanceof String) {
                    resolvedValue = SimpleLanguage.expression((String)value).evaluate(exchange, Object.class);
                }
                query.setParameter(key, resolvedValue);
            });
        }
    }

    protected void processFind(Exchange exchange, EntityManager entityManager) {
        final Object key = exchange.getMessage().getBody();

        if (key != null) {
            transactionTemplate.execute(new TransactionCallback<Object>() {
                public Object doInTransaction(TransactionStatus status) {
                    if (getEndpoint().isJoinTransaction()) {
                        entityManager.joinTransaction();
                    }

                    Object answer = entityManager.find(getEndpoint().getEntityType(), key);
                    LOG.debug("Find: {} -> {}", key, answer);

                    Message target = exchange.getPattern().isOutCapable() ? exchange.getOut() : exchange.getIn();
                    target.setBody(answer);

                    if (getEndpoint().isFlushOnSend()) {
                        entityManager.flush();
                    }

                    return null;
                }
            });
        }
    }

    protected void processEntity(Exchange exchange, EntityManager entityManager) {
        final Object values = expression.evaluate(exchange, Object.class);

        if (values != null) {
            transactionTemplate.execute(new TransactionCallback<Object>() {
                public Object doInTransaction(TransactionStatus status) {
                    if (getEndpoint().isJoinTransaction()) {
                        entityManager.joinTransaction();
                    }

                    if (values.getClass().isArray()) {
                        Object[] array = (Object[])values;
                        // need to create an array to store returned values as they can be updated
                        // by JPA such as setting auto assigned ids
                        Object[] managedArray = new Object[array.length];
                        Object managedEntity;
                        for (int i = 0; i < array.length; i++) {
                            Object element = array[i];
                            if (!getEndpoint().isRemove()) {
                                managedEntity = save(element);
                            } else {
                                managedEntity = remove(element);
                            }
                            managedArray[i] = managedEntity;
                        }
                        if (!getEndpoint().isUsePersist()) {
                            // and copy back to original array
                            System.arraycopy(managedArray, 0, array, 0, array.length);
                            exchange.getIn().setBody(array);
                        }
                    } else if (values instanceof Collection) {
                        Collection<?> collection = (Collection<?>)values;
                        // need to create a list to store returned values as they can be updated
                        // by JPA such as setting auto assigned ids
                        Collection managedCollection = new ArrayList<>(collection.size());
                        Object managedEntity;
                        for (Object entity : collection) {
                            if (!getEndpoint().isRemove()) {
                                managedEntity = save(entity);
                            } else {
                                managedEntity = remove(entity);
                            }
                            managedCollection.add(managedEntity);
                        }
                        if (!getEndpoint().isUsePersist()) {
                            exchange.getIn().setBody(managedCollection);
                        }
                    } else {
                        Object managedEntity;
                        if (!getEndpoint().isRemove()) {
                            managedEntity = save(values);
                        } else {
                            managedEntity = remove(values);
                        }
                        if (!getEndpoint().isUsePersist()) {
                            exchange.getIn().setBody(managedEntity);
                        }
                    }

                    if (getEndpoint().isFlushOnSend()) {
                        entityManager.flush();
                    }

                    return null;
                }

                /**
                 * Save the given entity and return the managed entity
                 *
                 * @return the managed entity
                 */
                private Object save(final Object entity) {
                    LOG.debug("save: {}", entity);
                    if (getEndpoint().isUsePersist()) {
                        entityManager.persist(entity);
                        return entity;
                    } else {
                        return entityManager.merge(entity);
                    }
                }

                /**
                 * Remove the given entity and return the managed entity
                 *
                 * @return the managed entity
                 */
                private Object remove(final Object entity) {
                    LOG.debug("remove: {}", entity);

                    Object managedEntity;

                    // First check if entity is attached to the persistence context
                    if (entityManager.contains(entity)) {
                        managedEntity = entity;
                    } else {
                        // If not, merge entity state into context before removing it
                        managedEntity = entityManager.merge(entity);
                    }

                    entityManager.remove(managedEntity);
                    return managedEntity;
                }
            });
        }
    }

}
