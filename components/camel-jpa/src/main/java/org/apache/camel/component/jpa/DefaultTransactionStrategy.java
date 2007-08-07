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

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import org.apache.camel.impl.ServiceSupport;

import org.springframework.orm.jpa.JpaCallback;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * @version $Revision$
 */
public class DefaultTransactionStrategy extends ServiceSupport implements TransactionStrategy {
    private EntityManagerFactory entityManagerFactory;
    private EntityManager entityManager;

    public DefaultTransactionStrategy(EntityManagerFactory entityManagerFactory) {
        notNull(entityManagerFactory, "entityManagerFactory");
        this.entityManagerFactory = entityManagerFactory;
    }

    public DefaultTransactionStrategy(EntityManager entityManager) {
        notNull(entityManager, "entityManager");
        this.entityManager = entityManager;
    }

    public Object execute(JpaCallback callback) {
        EntityManager em = getEntityManager();
        EntityTransaction transaction = em.getTransaction();
        transaction.begin();

        try {
            Object answer = callback.doInJpa(em);
            transaction.commit();
            return answer;
        } catch (RuntimeException e) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw e;
        }
    }

    public EntityManager getEntityManager() {
        if (entityManager == null) {
            entityManager = entityManagerFactory.createEntityManager();
        }
        return entityManager;
    }

    protected void doStart() throws Exception {
        // force lazy construction
        getEntityManager();
    }

    protected void doStop() throws Exception {
        if (entityManager != null) {
            entityManager.close();
        }
    }
}
