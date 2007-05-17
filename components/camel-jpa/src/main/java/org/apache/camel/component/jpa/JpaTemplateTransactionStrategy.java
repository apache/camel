/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jpa;

import org.apache.camel.impl.ServiceSupport;
import org.springframework.orm.jpa.JpaCallback;
import org.springframework.orm.jpa.JpaTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceException;

/**
 * Delegates the strategy to the {@link JpaTemplate} and {@link TransactionTemplate} for transaction handling
 *
 * @version $Revision$
 */
public class JpaTemplateTransactionStrategy extends ServiceSupport implements TransactionStrategy {
    private final JpaTemplate jpaTemplate;
    private final TransactionTemplate transactionTemplate;

    /**
     * Creates a new implementation from the given JPA factory
     */
    public static JpaTemplateTransactionStrategy newInstance(EntityManagerFactory emf) {
        JpaTemplate template = new JpaTemplate(emf);
        return newInstance(emf, template);
    }

    public static JpaTemplateTransactionStrategy newInstance(EntityManagerFactory emf, JpaTemplate template) {
        JpaTransactionManager transactionManager = new JpaTransactionManager(emf);
        transactionManager.afterPropertiesSet();

        TransactionTemplate tranasctionTemplate = new TransactionTemplate(transactionManager);
        tranasctionTemplate.afterPropertiesSet();

        return new JpaTemplateTransactionStrategy(template, tranasctionTemplate);
    }

    public JpaTemplateTransactionStrategy(JpaTemplate jpaTemplate, TransactionTemplate transactionTemplate) {
        this.jpaTemplate = jpaTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    public Object execute(final JpaCallback callback) {
        return transactionTemplate.execute(new TransactionCallback() {
            public Object doInTransaction(TransactionStatus status) {
                return jpaTemplate.execute(new JpaCallback() {
                    public Object doInJpa(EntityManager entityManager) throws PersistenceException {
                        return callback.doInJpa(entityManager);
                    }
                });
            }
        });
    }

    protected void doStart() throws Exception {
    }

    protected void doStop() throws Exception {
    }
}
