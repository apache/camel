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
package org.apache.camel.component.hibernate;

import java.util.Map;

import jakarta.persistence.EntityManagerFactory;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.hibernate.SessionFactory;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Native Hibernate component for Apache Camel supporting both JPA-backed and Native Hibernate execution modes.
 */
@Component("hibernate")
public class HibernateComponent extends DefaultComponent {

    @Metadata(description = "To use the SessionFactory as the factory for creating Hibernate sessions.")
    private SessionFactory sessionFactory;

    @Metadata(description = "To use the EntityManagerFactory as the factory for creating Hibernate sessions.")
    private EntityManagerFactory entityManagerFactory;

    @Metadata(description = "To use the Spring PlatformTransactionManager for managing transactions.")
    private PlatformTransactionManager transactionManager;

    public HibernateComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        HibernateEndpoint endpoint = new HibernateEndpoint(uri, this);

        // Target entity class or named configuration passed via URI path
        if (remaining != null && !remaining.isEmpty()) {
            endpoint.setEntityClassName(remaining);
        }

        setProperties(endpoint, parameters);

        // Auto-resolve required infrastructure from component defaults if not explicitly set on endpoint
        if (endpoint.getSessionFactory() == null && sessionFactory != null) {
            endpoint.setSessionFactory(sessionFactory);
        }
        if (endpoint.getEntityManagerFactory() == null && entityManagerFactory != null) {
            endpoint.setEntityManagerFactory(entityManagerFactory);
        }
        if (endpoint.getTransactionManager() == null && transactionManager != null) {
            endpoint.setTransactionManager(transactionManager);
        }

        // Auto-discover infrastructure beans from Camel Registry if still unassigned
        if (endpoint.getSessionFactory() == null) {
            SessionFactory sf = getCamelContext().getRegistry().findSingleByType(SessionFactory.class);
            if (sf != null) {
                endpoint.setSessionFactory(sf);
            }
        }
        if (endpoint.getEntityManagerFactory() == null) {
            EntityManagerFactory emf = getCamelContext().getRegistry().findSingleByType(EntityManagerFactory.class);
            if (emf != null) {
                endpoint.setEntityManagerFactory(emf);
            }
        }
        if (endpoint.getTransactionManager() == null) {
            PlatformTransactionManager tm = getCamelContext().getRegistry().findSingleByType(PlatformTransactionManager.class);
            if (tm != null) {
                endpoint.setTransactionManager(tm);
            }
        }

        return endpoint;
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    /**
     * To use the SessionFactory as the factory for creating Hibernate sessions.
     */
    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public EntityManagerFactory getEntityManagerFactory() {
        return entityManagerFactory;
    }

    /**
     * To use the EntityManagerFactory as the factory for creating Hibernate sessions.
     */
    public void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    /**
     * To use the Spring PlatformTransactionManager for managing transactions.
     */
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }
}
