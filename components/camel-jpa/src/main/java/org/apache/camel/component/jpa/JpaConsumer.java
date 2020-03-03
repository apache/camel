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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.LockModeType;
import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;
import javax.persistence.PessimisticLockException;
import javax.persistence.Query;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

public class JpaConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(JpaConsumer.class);

    private static final Map<String, Object> NOWAIT;
    private final EntityManagerFactory entityManagerFactory;
    private final TransactionTemplate transactionTemplate;
    private EntityManager entityManager;
    private QueryFactory queryFactory;
    private DeleteHandler<Object> deleteHandler;
    private DeleteHandler<Object> preDeleteHandler;
    private String query;
    private String namedQuery;
    private String nativeQuery;
    private LockModeType lockModeType = LockModeType.PESSIMISTIC_WRITE;
    private Map<String, Object> parameters;
    private Class<?> resultClass;
    private boolean transacted;
    private boolean skipLockedEntity;

    static {
        NOWAIT = new HashMap<>();
        NOWAIT.put("javax.persistence.lock.timeout", 0L);
    }

    private static final class DataHolder {
        private Exchange exchange;
        private Object result;
        private EntityManager manager;
        private DataHolder() {
        }
    }

    public JpaConsumer(JpaEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.entityManagerFactory = endpoint.getEntityManagerFactory();
        this.transactionTemplate = endpoint.createTransactionTemplate();
    }

    @Override
    protected int poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;
        
        // Recreate EntityManager in case it is disposed due to transaction rollback
        if (entityManager == null) {
            if (getEndpoint().isSharedEntityManager()) {
                this.entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
            } else {
                this.entityManager = entityManagerFactory.createEntityManager();
            }
            LOG.trace("Recreated EntityManager {} on {}", entityManager, this);
        }

        Object messagePolled = null;
        try {
            messagePolled = transactionTemplate.execute(new TransactionCallback<Object>() {
                public Object doInTransaction(TransactionStatus status) {
                    if (getEndpoint().isJoinTransaction()) {
                        entityManager.joinTransaction();
                    }

                    Queue<DataHolder> answer = new LinkedList<>();

                    Query query = getQueryFactory().createQuery(entityManager);
                    configureParameters(query);
                    LOG.trace("Created query {}", query);

                    List<?> results = query.getResultList();
                    LOG.trace("Got result list from query {}", results);

                    for (Object result : results) {
                        DataHolder holder = new DataHolder();
                        holder.manager = entityManager;
                        holder.result = result;
                        holder.exchange = createExchange(result, entityManager);
                        answer.add(holder);
                    }

                    PersistenceException cause = null;
                    int messagePolled = 0;
                    try {
                        messagePolled = processBatch(CastUtils.cast(answer));
                    } catch (Exception e) {
                        if (e instanceof PersistenceException) {
                            cause = (PersistenceException) e;
                        } else {
                            cause = new PersistenceException(e);
                        }
                    }

                    if (cause != null) {
                        if (!isTransacted()) {
                            LOG.warn("Error processing last message due: {}. Will commit all previous successful processed message, and ignore this last failure.", cause.getMessage(), cause);
                        } else {
                            // rollback all by throwning exception
                            throw cause;
                        }
                    }

                    // commit
                    LOG.debug("Flushing EntityManager");
                    entityManager.flush();
                    // must clear after flush
                    entityManager.clear();
                    return messagePolled;
                }
            });
        } catch (Exception e) {
            // Potentially EntityManager could be in an inconsistent state after transaction rollback,
            // so disposing it to have it recreated in next poll. cf. Java Persistence API 3.3.2 Transaction Rollback
            LOG.debug("Disposing EntityManager {} on {} due to coming transaction rollback", entityManager, this);
            entityManager.close();
            entityManager = null;
            throw new PersistenceException(e);
        }

        return getEndpoint().getCamelContext().getTypeConverter().convertTo(int.class, messagePolled);
    }


    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        // limit if needed
        if (maxMessagesPerPoll > 0 && total > maxMessagesPerPoll) {
            LOG.debug("Limiting to maximum messages to poll " + maxMessagesPerPoll + " as there were " + total + " messages in this poll.");
            total = maxMessagesPerPoll;
        }

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            DataHolder holder = org.apache.camel.util.ObjectHelper.cast(DataHolder.class, exchanges.poll());
            EntityManager entityManager = holder.manager;
            Exchange exchange = holder.exchange;
            Object result = holder.result;

            // add current index and total as properties
            exchange.setProperty(Exchange.BATCH_INDEX, index);
            exchange.setProperty(Exchange.BATCH_SIZE, total);
            exchange.setProperty(Exchange.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;
            if (lockEntity(result, entityManager)) {
                // Run the @PreConsumed callback
                createPreDeleteHandler().deleteObject(entityManager, result, exchange);

                // process the current exchange
                LOG.debug("Processing exchange: {}", exchange);
                getProcessor().process(exchange);
                if (exchange.getException() != null) {
                    // if we failed then throw exception
                    throw exchange.getException();
                }

                // Run the @Consumed callback
                getDeleteHandler().deleteObject(entityManager, result, exchange);
            }
        }

        return total;
    }

    // Properties
    // -------------------------------------------------------------------------
    @Override
    public JpaEndpoint getEndpoint() {
        return (JpaEndpoint) super.getEndpoint();
    }

    public QueryFactory getQueryFactory() {
        if (queryFactory == null) {
            queryFactory = createQueryFactory();
            if (queryFactory == null) {
                throw new IllegalArgumentException("No queryType property configured on this consumer, nor an entityType configured on the endpoint so cannot consume");
            }
        }
        return queryFactory;
    }

    public void setQueryFactory(QueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public DeleteHandler<Object> getDeleteHandler() {
        if (deleteHandler == null) {
            deleteHandler = createDeleteHandler();
        }
        return deleteHandler;
    }

    public void setDeleteHandler(DeleteHandler<Object> deleteHandler) {
        this.deleteHandler = deleteHandler;
    }

    public DeleteHandler<Object> getPreDeleteHandler() {
        if (preDeleteHandler == null) {
            preDeleteHandler = createPreDeleteHandler();
        }
        return preDeleteHandler;
    }

    public void setPreDeleteHandler(DeleteHandler<Object> preDeleteHandler) {
        this.preDeleteHandler = preDeleteHandler;
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

    public LockModeType getLockModeType() {
        return lockModeType;
    }

    public void setLockModeType(LockModeType lockModeType) {
        this.lockModeType = lockModeType;
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
    
    public Class<?> getResultClass() {
        return resultClass;
    }

    public void setResultClass(Class<?> resultClass) {
        this.resultClass = resultClass;
    }

    public boolean isTransacted() {
        return transacted;
    }

    /**
     * Sets whether to run in transacted mode or not.
     * <p/>
     * This option is default <tt>false</tt>. When <tt>false</tt> then all the good messages
     * will commit, and the first failed message will rollback.
     * However when <tt>true</tt>, then all messages will rollback, if just one message failed.
     */
    public void setTransacted(boolean transacted) {
        this.transacted = transacted;
    }

    /**
     * Sets whether to use NOWAIT on lock and silently skip the entity. This
     * allows different instances to process entities at the same time but not
     * processing the same entity.
     */
    public void setSkipLockedEntity(boolean skipLockedEntity) {
        this.skipLockedEntity = skipLockedEntity;
    }

    public boolean isSkipLockedEntity() {
        return skipLockedEntity;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * A strategy method to lock an object with an exclusive lock so that it can
     * be processed
     * 
     * @param entity the entity to be locked
     * @param entityManager entity manager
     * @return true if the entity was locked
     */
    protected boolean lockEntity(Object entity, EntityManager entityManager) {
        if (!getEndpoint().isConsumeLockEntity()) {
            return true;
        }
        try {
            LOG.debug("Acquiring exclusive lock on entity: {}", entity);
            if (isSkipLockedEntity()) {
                entityManager.lock(entity, lockModeType, NOWAIT);
            } else {
                entityManager.lock(entity, lockModeType);
            }
            return true;
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failed to achieve lock on entity: " + entity + ". Reason: " + e, e);
            }
            if (e instanceof PessimisticLockException || e instanceof OptimisticLockException) {
                //transaction marked as rollback can't continue gracefully
                throw (PersistenceException) e;
            }
            //TODO: Find if possible an alternative way to handle results of native queries.
            //Result of native queries are Arrays and cannot be locked by all JPA Providers.
            if (entity.getClass().isArray()) {
                return true;
            }
            return false;
        }
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
        if (entity != null && !entity.name().equals("")) {
            return entity.name();
        } else {
            return null;
        }
    }

    protected DeleteHandler<Object> createPreDeleteHandler() {
        // Look for @PreConsumed to allow custom callback before the Entity has been consumed
        final Class<?> entityType = getEndpoint().getEntityType();
        if (entityType != null) {
            // Inspect the method(s) annotated with @PreConsumed
            List<Method> methods = org.apache.camel.util.ObjectHelper.findMethodsWithAnnotation(entityType, PreConsumed.class);
            if (methods.size() > 1) {
                throw new IllegalStateException("Only one method can be annotated with the @PreConsumed annotation but found: " + methods);
            } else if (methods.size() == 1) {
                // Inspect the parameters of the @PreConsumed method
                final Method method = methods.get(0);
                final boolean useExchangeParameter = checkParameters(method);
                return new DeleteHandler<Object>() {
                    @Override
                    public void deleteObject(EntityManager entityManager, Object entityBean, Exchange exchange) {
                        // The entityBean could be an Object array
                        if (entityType.isInstance(entityBean)) {
                            if (useExchangeParameter) {
                                ObjectHelper.invokeMethod(method, entityBean, exchange);
                            } else {
                                ObjectHelper.invokeMethod(method, entityBean);
                            }
                        }
                    }
                };
            }
        }

        // else do nothing
        return new DeleteHandler<Object>() {
            @Override
            public void deleteObject(EntityManager entityManager, Object entityBean, Exchange exchange) {
                // Do nothing
            }
        };
    }

    protected DeleteHandler<Object> createDeleteHandler() {
        // look for @Consumed to allow custom callback when the Entity has been consumed
        final Class<?> entityType = getEndpoint().getEntityType();
        if (entityType != null) {
            List<Method> methods = org.apache.camel.util.ObjectHelper.findMethodsWithAnnotation(entityType, Consumed.class);
            if (methods.size() > 1) {
                throw new IllegalArgumentException("Only one method can be annotated with the @Consumed annotation but found: " + methods);
            } else if (methods.size() == 1) {
                final Method method = methods.get(0);
                final boolean useExchangeParameter = checkParameters(method);
                return new DeleteHandler<Object>() {
                    public void deleteObject(EntityManager entityManager, Object entityBean, Exchange exchange) {
                        if (entityType.isInstance(entityBean)) {
                            if (useExchangeParameter) {
                                ObjectHelper.invokeMethod(method, entityBean, exchange);
                            } else {
                                ObjectHelper.invokeMethod(method, entityBean);
                            }
                        }
                    }
                };
            }
        }
        if (getEndpoint().isConsumeDelete()) {
            return new DeleteHandler<Object>() {
                public void deleteObject(EntityManager entityManager, Object entityBean, Exchange exchange) {
                    entityManager.remove(entityBean);
                }
            };
        } else {
            return new DeleteHandler<Object>() {
                public void deleteObject(EntityManager entityManager, Object entityBean, Exchange exchange) {
                    // do nothing
                }
            };
        }
    }
    
    protected boolean checkParameters(Method method) {
        boolean result = false;
        Class<?>[] parameters = method.getParameterTypes();
        if (parameters.length == 1 && parameters[0].isAssignableFrom(Exchange.class)) {
            result = true;
        } 
        if (parameters.length > 0 && !result) {
            throw new IllegalStateException("@PreConsumed annotated method cannot have parameter other than Exchange");
        }
        return result;
    }

    protected void configureParameters(Query query) {
        int maxResults = getEndpoint().getMaximumResults();
        if (maxResults > 0) {
            query.setMaxResults(maxResults);
        }
        // setup the parameter
        if (parameters != null) {
            for (Entry<String, Object> entry : parameters.entrySet()) {
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

    @Override
    protected void doStart() throws Exception {
        // need to setup entity manager first
        if (getEndpoint().isSharedEntityManager()) {
            this.entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        } else {
            this.entityManager = entityManagerFactory.createEntityManager();
        }
        LOG.trace("Created EntityManager {} on {}", entityManager, this);

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    @Override
    protected void doShutdown() throws Exception {
        if (entityManager != null) {
            this.entityManager.close();
            LOG.trace("Closed EntityManager {} on {}", entityManager, this);
        }

        super.doShutdown();
    }
}
