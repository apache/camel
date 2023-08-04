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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import jakarta.persistence.EntityManagerFactory;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A JPA Component
 */
@Component("jpa")
public class JpaComponent extends HealthCheckComponent {

    private static final Logger LOG = LoggerFactory.getLogger(JpaComponent.class);

    private ExecutorService pollingConsumerExecutorService;

    @Metadata
    private EntityManagerFactory entityManagerFactory;
    @Metadata
    private TransactionStrategy transactionStrategy;
    @Metadata(defaultValue = "true")
    private boolean joinTransaction = true;
    @Metadata
    private boolean sharedEntityManager;
    @Metadata
    private Map<String, Class<?>> aliases = new HashMap<>();

    public JpaComponent() {
        // default constructor
    }

    // Properties
    //-------------------------------------------------------------------------
    public EntityManagerFactory getEntityManagerFactory() {
        return entityManagerFactory;
    }

    /**
     * To use the {@link EntityManagerFactory}. This is strongly recommended to configure.
     */
    public void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public TransactionStrategy getTransactionStrategy() {
        return transactionStrategy;
    }

    /**
     * To use the {@link TransactionStrategy} for running the operations in a transaction.
     */
    public void setTransactionStrategy(TransactionStrategy transactionStrategy) {
        this.transactionStrategy = transactionStrategy;
    }

    public boolean isJoinTransaction() {
        return joinTransaction;
    }

    /**
     * The camel-jpa component will join transaction by default. You can use this option to turn this off, for example
     * if you use LOCAL_RESOURCE and join transaction doesn't work with your JPA provider. This option can also be set
     * globally on the JpaComponent, instead of having to set it on all endpoints.
     */
    public void setJoinTransaction(boolean joinTransaction) {
        this.joinTransaction = joinTransaction;
    }

    public boolean isSharedEntityManager() {
        return sharedEntityManager;
    }

    /**
     * Whether to use Spring's SharedEntityManager for the consumer/producer. Note in most cases joinTransaction should
     * be set to false as this is not an EXTENDED EntityManager.
     */
    public void setSharedEntityManager(boolean sharedEntityManager) {
        this.sharedEntityManager = sharedEntityManager;
    }

    public void addAlias(String alias, Class<?> clazz) {
        this.aliases.put(alias, clazz);
    }

    /**
     * Maps an alias to a JPA entity class. The alias can then be used in the endpoint URI (instead of the fully
     * qualified class name).
     */
    public void setAliases(Map<String, Class<?>> aliases) {
        this.aliases = aliases;
    }

    public Map<String, Class<?>> getAliases() {
        return aliases;
    }

    synchronized ExecutorService getOrCreatePollingConsumerExecutorService() {
        if (pollingConsumerExecutorService == null) {
            LOG.debug("Creating thread pool for JpaPollingConsumer to support polling using timeout");
            pollingConsumerExecutorService
                    = getCamelContext().getExecutorServiceManager().newDefaultThreadPool(this, "JpaPollingConsumer");
        }
        return pollingConsumerExecutorService;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    @Override
    protected Endpoint createEndpoint(String uri, String path, Map<String, Object> options) throws Exception {
        JpaEndpoint endpoint = new JpaEndpoint(uri, this);
        endpoint.setJoinTransaction(isJoinTransaction());
        endpoint.setSharedEntityManager(isSharedEntityManager());

        Map<String, Object> params = PropertiesHelper.extractProperties(options, "parameters.", true);
        if (!params.isEmpty()) {
            endpoint.setParameters(params);
        }

        // lets interpret the next string as an alias or class
        if (ObjectHelper.isNotEmpty(path)) {
            Class<?> type = aliases.get(path);
            if (type == null) {
                // provide the class loader of this component to work in OSGi environments as camel-jpa must be able
                // to resolve the entity classes
                type = getCamelContext().getClassResolver().resolveClass(path, JpaComponent.class.getClassLoader());
            }

            if (type != null) {
                endpoint.setEntityType(type);
            }
        }
        setProperties(endpoint, options);
        return endpoint;
    }

    private void initEntityManagerFactory() {
        // lookup entity manager factory and use it if only one provided
        if (entityManagerFactory == null) {
            Map<String, EntityManagerFactory> map
                    = getCamelContext().getRegistry().findByTypeWithName(EntityManagerFactory.class);
            if (map != null) {
                if (map.size() == 1) {
                    entityManagerFactory = map.values().iterator().next();
                    LOG.info("Using EntityManagerFactory found in registry with id [{}] {}",
                            map.keySet().iterator().next(), entityManagerFactory);
                } else {
                    LOG.debug("Could not find a single EntityManagerFactory in registry as there was {} instances.",
                            map.size());
                }
            }
        } else {
            LOG.info("Using EntityManagerFactory configured: {}", entityManagerFactory);
        }
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        initEntityManagerFactory();

        // warn about missing configuration
        if (entityManagerFactory == null) {
            LOG.warn(
                    "No EntityManagerFactory has been configured on this JpaComponent. Each JpaEndpoint will auto create their own EntityManagerFactory.");
        }

        if (transactionStrategy != null) {
            LOG.info("Using TransactionStrategy configured: {}", transactionStrategy);
        } else {
            createTransactionStrategy();
        }
    }

    private void createTransactionStrategy() {
        if (transactionStrategy == null && getEntityManagerFactory() != null) {
            transactionStrategy = new DefaultTransactionStrategy(getCamelContext(), getEntityManagerFactory());
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (pollingConsumerExecutorService != null) {
            getCamelContext().getExecutorServiceManager().shutdown(pollingConsumerExecutorService);
            pollingConsumerExecutorService = null;
        }
    }
}
