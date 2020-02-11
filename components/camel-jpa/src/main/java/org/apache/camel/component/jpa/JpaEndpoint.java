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

import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.LockModeType;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.InvalidPayloadRuntimeException;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The jpa component enables you to store and retrieve Java objects from databases using JPA.
 */
@UriEndpoint(firstVersion = "1.0.0", scheme = "jpa", title = "JPA", syntax = "jpa:entityType", label = "database,sql")
public class JpaEndpoint extends ScheduledPollEndpoint {

    private EntityManagerFactory entityManagerFactory;
    private PlatformTransactionManager transactionManager;
    private Expression producerExpression;

    @UriPath(description = "Entity class name") @Metadata(required = true)
    private Class<?> entityType;
    @UriParam(defaultValue = "camel") @Metadata(required = true)
    private String persistenceUnit = "camel";
    @UriParam(defaultValue = "true")
    private boolean joinTransaction = true;
    @UriParam(label = "advanced")
    private boolean sharedEntityManager;
    @UriParam(defaultValue = "-1")
    private int maximumResults = -1;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean consumeDelete = true;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean consumeLockEntity = true;
    @UriParam(label = "consumer")
    private int maxMessagesPerPoll;

    @UriParam
    private String query;
    @UriParam
    private String namedQuery;
    @UriParam
    private String nativeQuery;
    @UriParam(label = "consumer", defaultValue = "PESSIMISTIC_WRITE")
    private LockModeType lockModeType = LockModeType.PESSIMISTIC_WRITE;
    @UriParam(label = "consumer,advanced", multiValue = true)
    private Map<String, Object> parameters;
    @UriParam
    private Class<?> resultClass;
    @UriParam(label = "consumer")
    private boolean transacted;
    @UriParam(label = "consumer")
    private boolean skipLockedEntity;
    @UriParam(label = "consumer")
    private DeleteHandler<Object> deleteHandler;
    @UriParam(label = "consumer")
    private DeleteHandler<Object> preDeleteHandler;

    @UriParam(label = "producer", defaultValue = "true")
    private boolean flushOnSend = true;
    @UriParam(label = "producer")
    private boolean usePersist;
    @UriParam(label = "producer,advanced")
    private boolean usePassedInEntityManager;
    @UriParam(label = "producer")
    private boolean remove;
    @UriParam(label = "producer")
    private Boolean useExecuteUpdate;
    @UriParam(label = "producer")
    private boolean findEntity;

    @UriParam(label = "advanced", prefix = "emf.", multiValue = true)
    private Map<String, Object> entityManagerProperties;

    public JpaEndpoint() {
    }

    public JpaEndpoint(String uri, JpaComponent component) {
        super(uri, component);
        if (component != null) {
            entityManagerFactory = component.getEntityManagerFactory();
            transactionManager = component.getTransactionManager();
        }
    }

    @Override
    public JpaComponent getComponent() {
        return (JpaComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        validate();
        JpaProducer producer = new JpaProducer(this, getProducerExpression());
        producer.setQuery(getQuery());
        producer.setNamedQuery(getNamedQuery());
        producer.setNativeQuery(getNativeQuery());
        producer.setParameters(getParameters());
        producer.setResultClass(getResultClass());
        producer.setFindEntity(isFindEntity());
        producer.setUseExecuteUpdate(getUseExecuteUpdate());
        return producer;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        validate();
        JpaConsumer consumer = new JpaConsumer(this, processor);
        consumer.setMaxMessagesPerPoll(getMaxMessagesPerPoll());
        consumer.setQuery(getQuery());
        consumer.setNamedQuery(getNamedQuery());
        consumer.setNativeQuery(getNativeQuery());
        consumer.setLockModeType(getLockModeType());
        consumer.setParameters(getParameters());
        consumer.setResultClass(getResultClass());
        consumer.setTransacted(isTransacted());
        consumer.setSkipLockedEntity(isSkipLockedEntity());
        consumer.setDeleteHandler(getDeleteHandler());
        consumer.setPreDeleteHandler(getPreDeleteHandler());
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        JpaPollingConsumer consumer = new JpaPollingConsumer(this);
        consumer.setQuery(getQuery());
        consumer.setNamedQuery(getNamedQuery());
        consumer.setNativeQuery(getNativeQuery());
        consumer.setLockModeType(getLockModeType());
        consumer.setParameters(getParameters());
        consumer.setResultClass(getResultClass());
        return consumer;
    }

    @Override
    public void configureProperties(Map<String, Object> options) {
        Map<String, Object> emProperties = PropertiesHelper.extractProperties(options, "emf.");
        if (!emProperties.isEmpty()) {
            setEntityManagerProperties(emProperties);
        }
        super.configureProperties(options);
    }

    @Override
    protected String createEndpointUri() {
        return "jpa" + (entityType != null ? "://" + entityType.getName() : "");
    }

    // Properties
    // -------------------------------------------------------------------------
    public Expression getProducerExpression() {
        if (producerExpression == null) {
            producerExpression = createProducerExpression();
        }
        return producerExpression;
    }

    public void setProducerExpression(Expression producerExpression) {
        this.producerExpression = producerExpression;
    }

    public int getMaximumResults() {
        return maximumResults;
    }

    /**
     * Set the maximum number of results to retrieve on the Query.
     */
    public void setMaximumResults(int maximumResults) {
        this.maximumResults = maximumResults;
    }

    public Class<?> getEntityType() {
        return entityType;
    }

    /**
     * The JPA annotated class to use as entity.
     */
    public void setEntityType(Class<?> entityType) {
        this.entityType = entityType;
    }

    public EntityManagerFactory getEntityManagerFactory() {
        if (entityManagerFactory == null) {
            entityManagerFactory = createEntityManagerFactory();
        }
        return entityManagerFactory;
    }

    /**
     * The {@link EntityManagerFactory} to use.
     */
    public void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public PlatformTransactionManager getTransactionManager() {
        if (transactionManager == null) {
            transactionManager = createTransactionManager();
        }
        return transactionManager;
    }

    /**
     * To use the {@link PlatformTransactionManager} for managing transactions.
     */
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * Additional properties for the entity manager to use.
     */
    public Map<String, Object> getEntityManagerProperties() {
        if (entityManagerProperties == null) {
            entityManagerProperties = CastUtils.cast(System.getProperties());
        }
        return entityManagerProperties;
    }

    public void setEntityManagerProperties(Map<String, Object> entityManagerProperties) {
        this.entityManagerProperties = entityManagerProperties;
    }

    public String getPersistenceUnit() {
        return persistenceUnit;
    }

    /**
     * The JPA persistence unit used by default.
     */
    public void setPersistenceUnit(String persistenceUnit) {
        this.persistenceUnit = persistenceUnit;
    }

    public boolean isConsumeDelete() {
        return consumeDelete;
    }

    /**
     * If true, the entity is deleted after it is consumed; if false, the entity is not deleted.
     */
    public void setConsumeDelete(boolean consumeDelete) {
        this.consumeDelete = consumeDelete;
    }

    public boolean isConsumeLockEntity() {
        return consumeLockEntity;
    }

    /**
     * Specifies whether or not to set an exclusive lock on each entity bean while processing the results from polling.
     */
    public void setConsumeLockEntity(boolean consumeLockEntity) {
        this.consumeLockEntity = consumeLockEntity;
    }

    public boolean isFlushOnSend() {
        return flushOnSend;
    }

    /**
     * Flushes the EntityManager after the entity bean has been persisted.
     */
    public void setFlushOnSend(boolean flushOnSend) {
        this.flushOnSend = flushOnSend;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    /**
     * An integer value to define the maximum number of messages to gather per poll.
     * By default, no maximum is set. Can be used to avoid polling many thousands of messages when starting up the server.
     * Set a value of 0 or negative to disable.
     */
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }
    
    public boolean isUsePersist() {
        return usePersist;
    }

    /**
     * Indicates to use entityManager.persist(entity) instead of entityManager.merge(entity).
     * Note: entityManager.persist(entity) doesn't work for detached entities
     * (where the EntityManager has to execute an UPDATE instead of an INSERT query)!
     */
    public void setUsePersist(boolean usePersist) {
        this.usePersist = usePersist;
    }

    public boolean isRemove() {
        return remove;
    }

    /**
     * Indicates to use entityManager.remove(entity).
     */
    public void setRemove(boolean isRemove) {
        this.remove = isRemove;
    }

    public boolean isJoinTransaction() {
        return joinTransaction;
    }

    /**
     * The camel-jpa component will join transaction by default.
     * You can use this option to turn this off, for example if you use LOCAL_RESOURCE and join transaction
     * doesn't work with your JPA provider. This option can also be set globally on the JpaComponent,
     * instead of having to set it on all endpoints.
     */
    public void setJoinTransaction(boolean joinTransaction) {
        this.joinTransaction = joinTransaction;
    }

    public boolean isUsePassedInEntityManager() {
        return this.usePassedInEntityManager;
    }

    /**
     * If set to true, then Camel will use the EntityManager from the header
     * JpaConstants.ENTITY_MANAGER instead of the configured entity manager on the component/endpoint.
     * This allows end users to control which entity manager will be in use.
     */
    public void setUsePassedInEntityManager(boolean usePassedIn) {
        this.usePassedInEntityManager = usePassedIn;
    }

    public boolean isSharedEntityManager() {
        return sharedEntityManager;
    }

    /**
     * Whether to use Spring's SharedEntityManager for the consumer/producer.
     * Note in most cases joinTransaction should be set to false as this is not an EXTENDED EntityManager.
     */
    public void setSharedEntityManager(boolean sharedEntityManager) {
        this.sharedEntityManager = sharedEntityManager;
    }

    public String getQuery() {
        return query;
    }

    /**
     * To use a custom query.
     */
    public void setQuery(String query) {
        this.query = query;
    }

    public String getNamedQuery() {
        return namedQuery;
    }

    /**
     * To use a named query.
     */
    public void setNamedQuery(String namedQuery) {
        this.namedQuery = namedQuery;
    }

    public String getNativeQuery() {
        return nativeQuery;
    }

    /**
     * To use a custom native query. You may want to use the option resultClass also when using native queries.
     */
    public void setNativeQuery(String nativeQuery) {
        this.nativeQuery = nativeQuery;
    }

    public LockModeType getLockModeType() {
        return lockModeType;
    }

    /**
     * To configure the lock mode on the consumer.
     */
    public void setLockModeType(LockModeType lockModeType) {
        this.lockModeType = lockModeType;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * <p>This key/value mapping is used for building the query parameters.
     * It is expected to be of the generic type java.util.Map<String, Object> where the keys are the named parameters
     * of a given JPA query and the values are their corresponding effective values you want to select for.</p>
     * <p>When it's used for producer, Simple expression can be used as a parameter value. It allows you to
     * retrieve parameter values from the message body, header and etc.</p>
     */
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Class<?> getResultClass() {
        return resultClass;
    }

    /**
     * Defines the type of the returned payload (we will call entityManager.createNativeQuery(nativeQuery, resultClass)
     * instead of entityManager.createNativeQuery(nativeQuery)). Without this option, we will return an object array.
     * Only has an affect when using in conjunction with native query when consuming data.
     */
    public void setResultClass(Class<?> resultClass) {
        this.resultClass = resultClass;
    }

    public boolean isTransacted() {
        return transacted;
    }

    /**
     * Whether to run the consumer in transacted mode, by which all messages will either commit or rollback,
     * when the entire batch has been processed. The default behavior (false) is to commit all the previously
     * successfully processed messages, and only rollback the last failed message.
     */
    public void setTransacted(boolean transacted) {
        this.transacted = transacted;
    }

    public boolean isSkipLockedEntity() {
        return skipLockedEntity;
    }

    /**
     * To configure whether to use NOWAIT on lock and silently skip the entity.
     */
    public void setSkipLockedEntity(boolean skipLockedEntity) {
        this.skipLockedEntity = skipLockedEntity;
    }

    public DeleteHandler<Object> getDeleteHandler() {
        return deleteHandler;
    }

    /**
     * To use a custom DeleteHandler to delete the row after the consumer is done processing the exchange
     */
    public void setDeleteHandler(DeleteHandler<Object> deleteHandler) {
        this.deleteHandler = deleteHandler;
    }

    public DeleteHandler<Object> getPreDeleteHandler() {
        return preDeleteHandler;
    }

    /**
     * To use a custom Pre-DeleteHandler to delete the row after the consumer has read the entity.
     */
    public void setPreDeleteHandler(DeleteHandler<Object> preDeleteHandler) {
        this.preDeleteHandler = preDeleteHandler;
    }

    public Boolean getUseExecuteUpdate() {
        return useExecuteUpdate;
    }

    /**
     * To configure whether to use executeUpdate() when producer executes a query.
     * When you use INSERT, UPDATE or DELETE statement as a named query, you need to specify
     * this option to 'true'.
     */
    public void setUseExecuteUpdate(Boolean useExecuteUpdate) {
        this.useExecuteUpdate = useExecuteUpdate;
    }

    public boolean isFindEntity() {
        return findEntity;
    }

    /**
     * If enabled then the producer will find a single entity by using the message body as key and entityType as the class type.
     * This can be used instead of a query to find a single entity.
     */
    public void setFindEntity(boolean findEntity) {
        this.findEntity = findEntity;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    protected void validate() {
        ObjectHelper.notNull(getEntityManagerFactory(), "entityManagerFactory");
    }

    protected EntityManagerFactory createEntityManagerFactory() {
        LocalEntityManagerFactoryBean emfBean = new LocalEntityManagerFactoryBean();
        emfBean.setPersistenceUnitName(persistenceUnit);
        emfBean.setJpaPropertyMap(getEntityManagerProperties());
        emfBean.afterPropertiesSet();
        return emfBean.getObject();
    }

    protected PlatformTransactionManager createTransactionManager() {
        JpaTransactionManager tm = new JpaTransactionManager(getEntityManagerFactory());
        tm.afterPropertiesSet();
        return tm;
    }

    /**
     * @deprecated use {@link #getEntityManagerFactory()} to get hold of factory and create an entity manager using the factory.
     */
    @Deprecated
    protected EntityManager createEntityManager() {
        if (sharedEntityManager) {
            return SharedEntityManagerCreator.createSharedEntityManager(getEntityManagerFactory());
        } else {
            return getEntityManagerFactory().createEntityManager();
        }
    }

    protected TransactionTemplate createTransactionTemplate() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(getTransactionManager());
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transactionTemplate.afterPropertiesSet();
        return transactionTemplate;
    }

    protected Expression createProducerExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Object answer;

                // must have a body
                try {
                    if (getEntityType() == null) {
                        answer = exchange.getIn().getMandatoryBody();
                    } else {
                        answer = exchange.getIn().getMandatoryBody(getEntityType());
                    }
                } catch (InvalidPayloadException e) {
                    throw new InvalidPayloadRuntimeException(exchange, getEntityType(), e.getCause());
                }
                // is never null
                return answer;
            }
        };
    }

}
