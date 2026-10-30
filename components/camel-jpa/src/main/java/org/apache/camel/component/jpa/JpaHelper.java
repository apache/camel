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

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

/**
 * Helper for JPA.
 */
public final class JpaHelper {

    private static final Logger LOG = LoggerFactory.getLogger(JpaHelper.class);

    private JpaHelper() {
    }

    /**
     * Gets or creates an {@link jakarta.persistence.EntityManager} to use.
     *
     * @param  exchange                 the current exchange, or <tt>null</tt> if no exchange
     * @param  entityManagerFactory     the entity manager factory (mandatory)
     * @param  usePassedInEntityManager whether to use an existing {@link jakarta.persistence.EntityManager} which has
     *                                  been stored on the exchange in the header with key
     *                                  {@link org.apache.camel.component.jpa.JpaConstants#ENTITY_MANAGER}
     * @param  useSharedEntityManager   whether to use SharedEntityManagerCreator if not already passed in
     * @return                          the entity manager (is never null): the resource must be closed by the client
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

        // then try reuse any entity manager from the transaction context
        if (em == null && exchange != null && exchange.isTransacted()) {
            Map<String, Object> data = getTransactionContextData(exchange);
            if (data != null) {
                em = (EntityManager) data.get(getKey(entityManagerFactory));
            }
        }

        if (em == null && useSharedEntityManager) {
            // NOTE: the stream will be closed by the client of this factory method
            em = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory); // NOSONAR
        }

        if (em == null) {
            em = createEntityManager(exchange, entityManagerFactory);
        }

        if (allowRecreate && em == null || !em.isOpen()) {
            em = createEntityManager(exchange, entityManagerFactory);
        }

        return em;
    }

    /**
     * Gets the {@link TransactionStrategy} to use when none has been configured: the single {@link TransactionStrategy}
     * in the registry if there is one and autowiring is enabled, otherwise a new {@link DefaultTransactionStrategy} (a
     * WARN is logged when the registry has more than one).
     *
     * @param  camelContext         the camel context, or <tt>null</tt> if not available (then the registry is not used)
     * @param  entityManagerFactory the entity manager factory
     * @param  autowiredEnabled     whether autowiring is enabled (otherwise the registry is not used)
     * @return                      the transaction strategy (is never null)
     */
    public static TransactionStrategy resolveTransactionStrategy(
            CamelContext camelContext, EntityManagerFactory entityManagerFactory, boolean autowiredEnabled) {
        if (camelContext != null && autowiredEnabled) {
            TransactionStrategy strategy = camelContext.getRegistry().findSingleByType(TransactionStrategy.class);
            if (strategy != null) {
                LOG.debug("Using TransactionStrategy found in registry: {}", strategy);
                return strategy;
            }
            int count = camelContext.getRegistry().findByType(TransactionStrategy.class).size();
            if (count > 1) {
                LOG.warn("Found {} TransactionStrategy beans in the registry, so none of them is used."
                         + " Configure the transactionStrategy option to choose one.",
                        count);
            }
        }
        return createDefaultTransactionStrategy(camelContext, entityManagerFactory);
    }

    /**
     * Copy JpaConstants.ENTITY_MANAGER property from source to target exchange.
     *
     * @param target The target exchange
     * @param source The source exchange
     */
    public static void copyEntityManagers(Exchange target, Exchange source) {
        if (target != null && source != null && target.getProperty(JpaConstants.ENTITY_MANAGER) == null) {
            Map<String, EntityManager> entityManagers = source.getProperty(JpaConstants.ENTITY_MANAGER, Map.class);
            if (entityManagers != null) {
                target.setProperty(JpaConstants.ENTITY_MANAGER, entityManagers);
            }
        }
    }

    // the only place creating the Spring based DefaultTransactionStrategy, so runtimes without Spring can replace it:
    // Camel Quarkus replaces this method by its name, so do not rename it or change its signature
    private static TransactionStrategy createDefaultTransactionStrategy(
            CamelContext camelContext, EntityManagerFactory entityManagerFactory) {
        return new DefaultTransactionStrategy(camelContext, entityManagerFactory);
    }

    private static EntityManager createEntityManager(Exchange exchange, EntityManagerFactory entityManagerFactory) {
        EntityManager em;
        em = entityManagerFactory.createEntityManager();
        if (exchange != null) {
            // we want to reuse the EM so store as property and make sure we close it when done with the exchange
            Map<String, EntityManager> entityManagers = getEntityManagerMap(exchange);
            entityManagers.put(getKey(entityManagerFactory), em);

            // we want to reuse the EM in the same transaction
            if (exchange.isTransacted()) {
                Map<String, Object> data = getTransactionContextData(exchange);
                if (data != null) {
                    data.put(getKey(entityManagerFactory), em);
                }
            }
            exchange.getExchangeExtension().addOnCompletion(new JpaCloseEntityManagerOnCompletion(em));
        }
        return em;
    }

    private static Map<String, Object> getTransactionContextData(Exchange exchange) {
        Map<String, Object> data = null;
        if (exchange.isTransacted()) {
            data = exchange.getProperty(Exchange.TRANSACTION_CONTEXT_DATA, Map.class);
        }
        return data;
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
