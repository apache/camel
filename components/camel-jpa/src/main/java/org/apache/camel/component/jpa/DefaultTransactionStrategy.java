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

import jakarta.persistence.EntityManagerFactory;

import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public class DefaultTransactionStrategy implements TransactionStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultTransactionStrategy.class);
    private TransactionTemplate transactionTemplate;
    private PlatformTransactionManager transactionManager;

    public DefaultTransactionStrategy(CamelContext camelContext, EntityManagerFactory entityManagerFactory) {
        initTransactionManager(camelContext);
        if (transactionManager == null && entityManagerFactory != null) {
            this.transactionManager = createTransactionManager(entityManagerFactory);
        }
        this.transactionTemplate = createTransactionTemplate();
    }

    @Override
    public void executeInTransaction(Runnable runnable) {
        transactionTemplate.execute(status -> {
            runnable.run();
            return null;
        });
    }

    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
        this.transactionTemplate = createTransactionTemplate();
    }

    public TransactionTemplate getTransactionTemplate() {
        return transactionTemplate;
    }

    private void initTransactionManager(CamelContext camelContext) {
        // lookup transaction manager and use it if only one provided
        if (transactionManager == null && camelContext != null) {
            Map<String, PlatformTransactionManager> map
                    = camelContext.getRegistry().findByTypeWithName(PlatformTransactionManager.class);
            if (map != null) {
                if (map.size() == 1) {
                    transactionManager = map.values().iterator().next();
                    LOG.info("Using TransactionManager found in registry with id [{}] {}",
                            map.keySet().iterator().next(), transactionManager);
                } else {
                    LOG.debug("Could not find a single TransactionManager in registry as there was {} instances.", map.size());
                }
            }
        } else {
            LOG.info("Using TransactionManager configured on this component: {}", transactionManager);
        }

        // transaction manager could also be hidden in a template
        if (transactionManager == null && camelContext != null) {
            Map<String, TransactionTemplate> map
                    = camelContext.getRegistry().findByTypeWithName(TransactionTemplate.class);
            if (map != null) {
                if (map.size() == 1) {
                    transactionManager = map.values().iterator().next().getTransactionManager();
                    LOG.info("Using TransactionManager found in registry with id [{}] {}",
                            map.keySet().iterator().next(), transactionManager);
                } else {
                    LOG.debug("Could not find a single TransactionTemplate in registry as there was {} instances.", map.size());
                }
            }
        }
    }

    protected PlatformTransactionManager createTransactionManager(EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager tm = new JpaTransactionManager(entityManagerFactory);
        tm.afterPropertiesSet();
        return tm;
    }

    protected TransactionTemplate createTransactionTemplate() {
        TransactionTemplate newTransactionTemplate = new TransactionTemplate(getTransactionManager());
        newTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        newTransactionTemplate.afterPropertiesSet();
        return newTransactionTemplate;
    }
}
