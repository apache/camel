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
package org.apache.camel.bam;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Utility class designed to orchestrate JPA {@link javax.persistence.EntityManager} and Spring transactions.
 */
public final class EntityManagers {

    private EntityManagers() {
    }

    /**
     * Fetches {@link javax.persistence.EntityManager} bound to the current Spring transaction or creates new manager.
     *
     * @param entityManagerFactory {@link javax.persistence.EntityManagerFactory} used either as a key for a Spring
     *                             transaction resource lookup or as a factory to create new
     *                             {@link javax.persistence.EntityManager}.
     * @return {@link javax.persistence.EntityManager} bound to the existing Spring transaction or new manager instance.
     */
    public static EntityManager resolveEntityManager(EntityManagerFactory entityManagerFactory) {
        EntityManagerHolder entityManagerHolder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(entityManagerFactory);
        if (entityManagerHolder != null) {
            return entityManagerHolder.getEntityManager();
        }
        return entityManagerFactory.createEntityManager();
    }

    public static void closeNonTransactionalEntityManager(EntityManager entityManager) {
        if (entityManager == null) {
            return;
        }
        boolean isTransactional = TransactionSynchronizationManager.hasResource(entityManager.getEntityManagerFactory());
        if (isTransactional) {
            entityManager.close();
        }
    }

}
