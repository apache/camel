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

import javax.persistence.EntityManagerFactory;

import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

public class DefaultTransactionStrategy implements TransactionStrategy {
    private final TransactionTemplate transactionTemplate;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;

    public DefaultTransactionStrategy(PlatformTransactionManager transactionManager,
                                      EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
        if (transactionManager == null) {
            this.transactionManager = createTransactionManager(entityManagerFactory);
        } else {
            this.transactionManager = transactionManager;
        }
        this.transactionTemplate = createTransactionTemplate();
    }

    @Override
    public void executeInTransaction(Runnable runnable) {
        transactionTemplate.execute(new TransactionCallback<Object>() {
            public Object doInTransaction(TransactionStatus status) {
                runnable.run();
                return null;
            }
        });
    }

    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    protected PlatformTransactionManager createTransactionManager(EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager tm = new JpaTransactionManager(entityManagerFactory);
        tm.afterPropertiesSet();
        return tm;
    }

    protected TransactionTemplate createTransactionTemplate() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(getTransactionManager());
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transactionTemplate.afterPropertiesSet();
        return transactionTemplate;
    }
}
