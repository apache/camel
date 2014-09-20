/**
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
import javax.persistence.EntityManagerFactory;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A JPA Component
 *
 * @version 
 */
public class JpaComponent extends UriEndpointComponent {
    private static final Logger LOG = LoggerFactory.getLogger(JpaComponent.class);
    private EntityManagerFactory entityManagerFactory;
    private PlatformTransactionManager transactionManager;
    private boolean joinTransaction = true;

    public JpaComponent() {
        super(JpaEndpoint.class);
    }

    // Properties
    //-------------------------------------------------------------------------
    public EntityManagerFactory getEntityManagerFactory() {
        return entityManagerFactory;
    }

    public void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    @Override
    protected Endpoint createEndpoint(String uri, String path, Map<String, Object> options) throws Exception {
        JpaEndpoint endpoint = new JpaEndpoint(uri, this);
        endpoint.setJoinTransaction(isJoinTransaction());

        // lets interpret the next string as a class
        if (ObjectHelper.isNotEmpty(path)) {
            // provide the class loader of this component to work in OSGi environments as camel-jpa must be able
            // to resolve the entity classes
            Class<?> type = getCamelContext().getClassResolver().resolveClass(path, JpaComponent.class.getClassLoader());
            if (type != null) {
                endpoint.setEntityType(type);
            }
        }
        return endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // lookup entity manager factory and use it if only one provided
        if (entityManagerFactory == null) {
            Map<String, EntityManagerFactory> map = getCamelContext().getRegistry().findByTypeWithName(EntityManagerFactory.class);
            if (map != null) {
                if (map.size() == 1) {
                    entityManagerFactory = map.values().iterator().next();
                    LOG.info("Using EntityManagerFactory found in registry with id ["
                            + map.keySet().iterator().next() + "] " + entityManagerFactory);
                } else {
                    LOG.debug("Could not find a single EntityManagerFactory in registry as there was " + map.size() + " instances.");
                }
            }
        } else {
            LOG.info("Using EntityManagerFactory configured: " + entityManagerFactory);
        }

        // lookup transaction manager and use it if only one provided
        if (transactionManager == null) {
            Map<String, PlatformTransactionManager> map = getCamelContext().getRegistry().findByTypeWithName(PlatformTransactionManager.class);
            if (map != null) {
                if (map.size() == 1) {
                    transactionManager = map.values().iterator().next();
                    LOG.info("Using TransactionManager found in registry with id ["
                            + map.keySet().iterator().next() + "] " + transactionManager);
                } else {
                    LOG.debug("Could not find a single TransactionManager in registry as there was " + map.size() + " instances.");
                }
            }
        } else {
            LOG.info("Using TransactionManager configured on this component: " + transactionManager);
        }

        // transaction manager could also be hidden in a template
        if (transactionManager == null) {
            Map<String, TransactionTemplate> map = getCamelContext().getRegistry().findByTypeWithName(TransactionTemplate.class);
            if (map != null) {
                if (map.size() == 1) {
                    transactionManager = map.values().iterator().next().getTransactionManager();
                    LOG.info("Using TransactionManager found in registry with id ["
                            + map.keySet().iterator().next() + "] " + transactionManager);
                } else {
                    LOG.debug("Could not find a single TransactionTemplate in registry as there was " + map.size() + " instances.");
                }
            }
        }

        // warn about missing configuration
        if (entityManagerFactory == null) {
            LOG.warn("No EntityManagerFactory has been configured on this JpaComponent. Each JpaEndpoint will auto create their own EntityManagerFactory.");
        }
        if (transactionManager == null) {
            LOG.warn("No TransactionManager has been configured on this JpaComponent. Each JpaEndpoint will auto create their own JpaTransactionManager.");
        }
    }

    public boolean isJoinTransaction() {
        return joinTransaction;
    }

    public void setJoinTransaction(boolean joinTransaction) {
        this.joinTransaction = joinTransaction;
    }
}
