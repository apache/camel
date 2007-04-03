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

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointResolver;
import org.apache.camel.Exchange;
import org.apache.camel.Service;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.springframework.orm.jpa.JpaTemplate;
import org.springframework.orm.jpa.LocalEntityManagerFactoryBean;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.JpaCallback;
import org.springframework.jca.support.LocalConnectionFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.dao.DataAccessException;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.net.URI;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * A JPA Component
 *
 * @version $Revision$
 */
public class JpaComponent extends DefaultComponent<Exchange> implements EndpointResolver {
    private EntityManagerFactory entityManagerFactory;
    private Map entityManagerProperties;
    private String entityManagerName = "camel";
    private JpaTemplate template;

    public Component resolveComponent(CamelContext container, String uri) throws Exception {
        return null;
    }

    public Endpoint resolveEndpoint(CamelContext container, String uri) throws Exception {
        if (!uri.startsWith("jpa:")) {
            return null;
        }
        URI u = new URI(uri);
        String path = u.getSchemeSpecificPart();
        String[] paths = ObjectHelper.splitOnCharacter(path, ":", 2);
        // ignore a prefix
        if (paths[1] != null) {
            path = paths[1];
        }
        JpaEndpoint endpoint = new JpaEndpoint(uri, this);

        // lets interpret the next string as a class
        if (path != null) {
            Class<?> type = ObjectHelper.loadClass(path);
            if (type != null) {
                endpoint.setEntityType(type);
            }
        }
        return endpoint;
    }

    // Properties
    //-------------------------------------------------------------------------
    public JpaTemplate getTemplate() {
        if (template == null) {
            template = createTemplate();
        }
        return template;
    }

    public void setTemplate(JpaTemplate template) {
        this.template = template;
    }

    public EntityManagerFactory getEntityManagerFactory() {
        if (entityManagerFactory == null) {
            entityManagerFactory = createEntityManagerFactory();
        }
        return entityManagerFactory;
    }

    public void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public Map getEntityManagerProperties() {
        if (entityManagerProperties == null) {
            entityManagerProperties = System.getProperties();
        }
        return entityManagerProperties;
    }

    public void setEntityManagerProperties(Map entityManagerProperties) {
        this.entityManagerProperties = entityManagerProperties;
    }

    public String getEntityManagerName() {
        return entityManagerName;
    }

    public void setEntityManagerName(String entityManagerName) {
        this.entityManagerName = entityManagerName;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected JpaTemplate createTemplate() {
  /*      EntityManagerFactory emf = getEntityManagerFactory();
        JpaTransactionManager transactionManager = new JpaTransactionManager(emf);
        transactionManager.afterPropertiesSet();

        final TransactionTemplate tranasctionTemplate = new TransactionTemplate(transactionManager);
        tranasctionTemplate.afterPropertiesSet();

        // lets auto-default to a JpaTemplate which implicitly creates a transaction
        // TODO surely there's a cleaner way to get the JpaTemplate to create a transaction if one is not present??
        return new JpaTemplate(emf) {
            @Override
            public Object execute(final JpaCallback action, final boolean exposeNativeEntityManager) throws DataAccessException {
                return tranasctionTemplate.execute(new TransactionCallback() {
                    public Object doInTransaction(TransactionStatus status) {
                        return doExecute(action, exposeNativeEntityManager);
                    }
                });

            }

            public Object doExecute(final JpaCallback action, final boolean exposeNativeEntityManager) throws DataAccessException {
                return super.execute(action, exposeNativeEntityManager);
            }
        };*/
        return new JpaTemplate(getEntityManagerFactory());
    }

    protected EntityManagerFactory createEntityManagerFactory() {
        //return Persistence.createEntityManagerFactory(entityManagerName);
        return Persistence.createEntityManagerFactory(entityManagerName, getEntityManagerProperties());
    }

    protected EntityManager createEntityManager() {
        return getEntityManagerFactory().createEntityManager();
    }
}
