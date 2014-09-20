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

import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.TypedQuery;

import static org.apache.camel.bam.EntityManagers.closeNonTransactionalEntityManager;
import static org.apache.camel.bam.EntityManagers.resolveEntityManager;

public class EntityManagerTemplate {

    private final EntityManagerFactory entityManagerFactory;

    public EntityManagerTemplate(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public <T> T execute(EntityManagerCallback<T> entityManagerCallback) {
        EntityManager entityManager = null;
        try {
            entityManager = resolveEntityManager(entityManagerFactory);
            return entityManagerCallback.execute(entityManager);
        } finally {
            closeNonTransactionalEntityManager(entityManager);
        }
    }

    public void persist(final Object entity) {
        execute(new EntityManagerCallback<Object>() {
            @Override
            public Object execute(EntityManager entityManager) {
                entityManager.persist(entity);
                return null;
            }
        });
    }

    public void flush() {
        execute(new EntityManagerCallback<Object>() {
            @Override
            public Object execute(EntityManager entityManager) {
                entityManager.flush();
                return null;
            }
        });
    }

    public <T> List<T> find(final Class<T> entity, final String query, final Map<String, Object> parameters) {
        return execute(new EntityManagerCallback<List<T>>() {
            @Override
            public List<T> execute(EntityManager entityManager) {
                TypedQuery<T> typedQuery = entityManager.createQuery(query, entity);
                for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
                    typedQuery.setParameter(parameter.getKey(), parameter.getValue());
                }
                return typedQuery.getResultList();
            }
        });
    }

}
