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

import jakarta.persistence.EntityManagerFactory;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.jpa.DefaultTransactionStrategy;
import org.apache.camel.component.jpa.TransactionStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.hibernate.SessionFactory;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Perform database operations using Hibernate ORM supporting both JPA-backed and Native Hibernate modes.
 */
@UriEndpoint(firstVersion = "4.23.0", scheme = "hibernate", title = "Hibernate", syntax = "hibernate:entityClassName",
             category = { Category.DATABASE })
public class HibernateEndpoint extends ScheduledPollEndpoint {

    @UriPath(description = "Target entity class name or entity type name")
    @Metadata(required = true)
    private String entityClassName;

    private Class<?> entityType;

    @UriParam(description = "The EntityManagerFactory to use")
    private EntityManagerFactory entityManagerFactory;

    @UriParam(description = "The Hibernate SessionFactory to use")
    private SessionFactory sessionFactory;

    @UriParam(description = "The PlatformTransactionManager to use")
    private PlatformTransactionManager transactionManager;

    private volatile TransactionStrategy transactionStrategy;

    @UriParam(description = "HQL query to execute")
    private String query;

    @UriParam(description = "Named query to execute")
    private String namedQuery;

    @UriParam(description = "Native SQL query to execute")
    private String nativeQuery;

    @UriParam(defaultValue = "-1", description = "Maximum number of results to retrieve")
    private int maximumResults = -1;

    @UriParam(defaultValue = "true", description = "Whether to delete consumed entities after polling")
    private boolean consumeDelete = true;

    @UriParam(defaultValue = "false",
              description = "Indicates to use entityManager.persist(entity) or session.persist(entity) instead of merge")
    private boolean usePersist;

    @UriParam(label = "producer",
              description = "To configure whether to use executeUpdate() when the producer executes a query. When you use INSERT, UPDATE or DELETE as a named query, you need to specify this option to true because Camel does not look into the named query unlike query and nativeQuery.")
    private Boolean useExecuteUpdate;

    @UriParam(description = "Parameters to pass to the query in key-value map format", multiValue = true,
              prefix = "parameters.")
    private Map<String, Object> parameters;

    public HibernateEndpoint() {
    }

    public HibernateEndpoint(String uri, HibernateComponent component) {
        super(uri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HibernateProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        HibernateConsumer consumer = new HibernateConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public SessionFactory getResolvedSessionFactory() {
        if (sessionFactory != null) {
            return sessionFactory;
        }

        if (entityManagerFactory != null) {
            return entityManagerFactory.unwrap(SessionFactory.class);
        }

        throw new IllegalArgumentException(
                "Either SessionFactory or EntityManagerFactory must be configured on HibernateEndpoint");
    }

    public boolean isJpaBacked() {
        return entityManagerFactory != null;
    }

    public boolean isNativeHibernate() {
        return entityManagerFactory == null && sessionFactory != null;
    }

    public TransactionStrategy getTransactionStrategy() {
        if (!isJpaBacked()) {
            throw new IllegalStateException(
                    "A TransactionStrategy is only available when an EntityManagerFactory is configured");
        }

        TransactionStrategy strategy = transactionStrategy;
        if (strategy == null) {
            synchronized (this) {
                strategy = transactionStrategy;
                if (strategy == null) {
                    transactionStrategy = strategy = createTransactionStrategy();
                }
            }
        }

        return strategy;
    }

    public void setTransactionStrategy(TransactionStrategy transactionStrategy) {
        this.transactionStrategy = transactionStrategy;
    }

    protected TransactionStrategy createTransactionStrategy() {
        if (entityManagerFactory == null) {
            throw new IllegalArgumentException(
                    "EntityManagerFactory must be configured to create a transaction strategy");
        }

        DefaultTransactionStrategy strategy = new DefaultTransactionStrategy(getCamelContext(), entityManagerFactory);

        if (transactionManager != null) {
            strategy.setTransactionManager(transactionManager);
        }

        return strategy;
    }

    @Override
    protected void doStart() throws Exception {
        validateConfiguration();
        resolveEntityType();
        if (isJpaBacked() && transactionStrategy == null) {
            transactionStrategy = createTransactionStrategy();
        }
        super.doStart();
    }

    protected void validateConfiguration() {
        if (entityManagerFactory == null && sessionFactory == null) {
            throw new IllegalArgumentException(
                    "Either EntityManagerFactory or SessionFactory must be configured on HibernateEndpoint");
        }

        if (entityManagerFactory != null && sessionFactory != null) {
            SessionFactory unwrappedSessionFactory;

            try {
                unwrappedSessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        "The configured EntityManagerFactory cannot be unwrapped to a Hibernate SessionFactory",
                        e);
            }

            if (unwrappedSessionFactory != sessionFactory) {
                throw new IllegalArgumentException(
                        "The configured EntityManagerFactory and SessionFactory do not refer to the same Hibernate SessionFactory");
            }
        }
    }

    protected void resolveEntityType() throws ClassNotFoundException {
        if (entityType == null && entityClassName != null && !entityClassName.isEmpty()) {
            this.entityType = getCamelContext().getClassResolver().resolveMandatoryClass(entityClassName);
        }
    }

    public String getEntityClassName() {
        return entityClassName;
    }

    public void setEntityClassName(String entityClassName) {
        this.entityClassName = entityClassName;
    }

    public Class<?> getEntityType() {
        return entityType;
    }

    public void setEntityType(Class<?> entityType) {
        this.entityType = entityType;
        if (entityType != null) {
            this.entityClassName = entityType.getName();
        }
    }

    public EntityManagerFactory getEntityManagerFactory() {
        return entityManagerFactory;
    }

    public void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
        this.transactionStrategy = null;
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
        this.transactionStrategy = null;
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

    public int getMaximumResults() {
        return maximumResults;
    }

    public void setMaximumResults(int maximumResults) {
        this.maximumResults = maximumResults;
    }

    public boolean isConsumeDelete() {
        return consumeDelete;
    }

    public void setConsumeDelete(boolean consumeDelete) {
        this.consumeDelete = consumeDelete;
    }

    public boolean isUsePersist() {
        return usePersist;
    }

    public void setUsePersist(boolean usePersist) {
        this.usePersist = usePersist;
    }

    public Boolean getUseExecuteUpdate() {
        return useExecuteUpdate;
    }

    public void setUseExecuteUpdate(Boolean useExecuteUpdate) {
        this.useExecuteUpdate = useExecuteUpdate;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }
}
