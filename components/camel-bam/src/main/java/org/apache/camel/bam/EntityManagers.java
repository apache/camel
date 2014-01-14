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

public final class EntityManagers {

    private EntityManagers() {
    }

    public static EntityManager resolveEntityManager(EntityManagerFactory entityManagerFactory) {
        EntityManagerHolder entityManagerHolder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(entityManagerFactory);
        if (entityManagerHolder != null) {
            return entityManagerHolder.getEntityManager();
        }
        return entityManagerFactory.createEntityManager();
    }

    public static void closeNonTransactionalEntityManager(EntityManager entityManager) {
        boolean isTransactional = TransactionSynchronizationManager.hasResource(entityManager.getEntityManagerFactory());
        if (entityManager != null && isTransactional) {
            entityManager.close();
        }
    }

}
