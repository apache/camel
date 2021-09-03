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

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

/**
 * Helper for JPA.
 */
public final class JpaHelper {

    private JpaHelper() {
    }

    /**
     * Gets or creates an {@link javax.persistence.EntityManager} to use.
     *
     * @param  exchange                 the current exchange, or <tt>null</tt> if no exchange
     * @param  entityManagerFactory     the entity manager factory (mandatory)
     * @param  usePassedInEntityManager whether to use an existing {@link javax.persistence.EntityManager} which has
     *                                  been stored on the exchange in the header with key
     *                                  {@link org.apache.camel.component.jpa.JpaConstants#ENTITY_MANAGER}
     * @param  useSharedEntityManager   whether to use SharedEntityManagerCreator if not already passed in
     * @return                          the entity manager (is never null)
     */
    public static EntityManager getTargetEntityManager(
            Exchange exchange, EntityManagerFactory entityManagerFactory,
            boolean usePassedInEntityManager, boolean useSharedEntityManager, boolean allowRecreate) {
        EntityManager em = null;

        // favor using entity manager provided as a header from the end user
        if (exchange != null && usePassedInEntityManager) {
            em = exchange.getIn().getHeader(JpaConstants.ENTITY_MANAGER, EntityManager.class);
        }

        // then try reuse any entity manager which has been previously created and stored on the exchange
        if (em == null && exchange != null) {
            em = getEntityManagerMap(exchange).get(getKey(entityManagerFactory));
        }

        if (em == null && useSharedEntityManager) {
            em = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        }

        if (em == null) {
            em = createEntityManager(exchange, entityManagerFactory);
        }

        if (allowRecreate && em == null || !em.isOpen()) {
            em = createEntityManager(exchange, entityManagerFactory);
        }

        return em;
    }

    private static EntityManager createEntityManager(Exchange exchange, EntityManagerFactory entityManagerFactory) {
        EntityManager em;
        em = entityManagerFactory.createEntityManager();
        if (exchange != null) {
            // we want to reuse the EM so store as property and make sure we close it when done with the exchange
            Map<String, EntityManager> entityManagers = getEntityManagerMap(exchange);
            entityManagers.put(getKey(entityManagerFactory), em);
            exchange.adapt(ExtendedExchange.class).addOnCompletion(new JpaCloseEntityManagerOnCompletion(em));
        }
        return em;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, EntityManager> getEntityManagerMap(Exchange exchange) {
        Map<String, EntityManager> entityManagers = exchange.getProperty(JpaConstants.ENTITY_MANAGER, Map.class);
        if (entityManagers == null) {
            entityManagers = new HashMap<>();
            exchange.setProperty(JpaConstants.ENTITY_MANAGER, entityManagers);
        }
        return entityManagers;
    }

    private static String getKey(EntityManagerFactory entityManagerFactory) {
        return String.valueOf(entityManagerFactory.hashCode());
    }
}
