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

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("hibernate")
public class HibernateComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(HibernateComponent.class);

    @Metadata(description = "The Hibernate SessionFactory to use.")
    private SessionFactory sessionFactory;

    @Metadata(description = "The DataSource to use for bootstrapping the SessionFactory (bean reference or registry name).")
    private Object dataSource;

    @Metadata(description = "Explicit array of entity classes.")
    private Class[] entityClasses;

    @Metadata(description = "Schema generation action: none, validate, update, create.", defaultValue = "none")
    private String schemaAction = "none";

    @Metadata(description = "Arbitrary Hibernate configuration properties passthrough map.")
    private Map<String, Object> hibernateProperties = new HashMap<>();

    private boolean sessionFactoryOwned;
    private StandardServiceRegistry serviceRegistry;

    public HibernateComponent() {
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // 1. Explicitly configured SessionFactory takes precedence (External, unowned)
        if (sessionFactory != null) {
            sessionFactoryOwned = false;
            LOG.info("Using explicitly configured Hibernate SessionFactory (external, unowned).");
            return;
        }

        // 2. Try to resolve SessionFactory from Camel registry (External, unowned)
        sessionFactory = getCamelContext().getRegistry().findSingleByType(SessionFactory.class);
        if (sessionFactory != null) {
            sessionFactoryOwned = false;
            LOG.info("Reusing existing Hibernate SessionFactory found in Camel registry (external, unowned).");
            return;
        }

        // 3. Bootstrap SessionFactory natively (Component-created, owned)
        validateSchemaAction(schemaAction);
        sessionFactory = createAndBootstrapSessionFactory();
        sessionFactoryOwned = true;
        LOG.info("Successfully bootstrapped native Hibernate SessionFactory (component-created, owned).");
    }

    @Override
    protected void doStop() throws Exception {
        if (sessionFactoryOwned && sessionFactory != null && !sessionFactory.isClosed()) {
            LOG.info("Closing component-owned Hibernate SessionFactory.");
            sessionFactory.close();
        }
        if (serviceRegistry != null) {
            StandardServiceRegistryBuilder.destroy(serviceRegistry);
            serviceRegistry = null;
        }
        sessionFactory = null;
        sessionFactoryOwned = false;
        super.doStop();
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        HibernateEndpoint endpoint = new HibernateEndpoint(uri, this);

        if (remaining != null && !remaining.isEmpty()) {
            endpoint.setEntityClassName(remaining);
        }

        setProperties(endpoint, parameters);
        endpoint.setSessionFactory(sessionFactory);

        return endpoint;
    }

    private void validateSchemaAction(String action) {
        if (action == null) {
            return;
        }
        switch (action.toLowerCase()) {
            case "none":
            case "validate":
            case "update":
            case "create":
                break;
            default:
                throw new IllegalArgumentException(
                        "Invalid schemaAction: '" + action + "'. Supported values are: none, validate, update, create.");
        }
    }

    private SessionFactory createAndBootstrapSessionFactory() {
        StandardServiceRegistryBuilder registryBuilder = new StandardServiceRegistryBuilder();

        Map<String, Object> settings = new HashMap<>();
        if (hibernateProperties != null) {
            settings.putAll(hibernateProperties);
        }

        // Resolve DataSource
        DataSource resolvedDataSource = resolveDataSource();
        if (resolvedDataSource != null) {
            settings.put(AvailableSettings.JAKARTA_NON_JTA_DATASOURCE, resolvedDataSource);
        }

        // Map schema action explicitly; "none" means do not request schema generation/update
        if (schemaAction != null && !schemaAction.equalsIgnoreCase("none")) {
            settings.put(AvailableSettings.HBM2DDL_AUTO, schemaAction.toLowerCase());
        }

        registryBuilder.applySettings(settings);
        serviceRegistry = registryBuilder.build();

        try {
            MetadataSources metadataSources = new MetadataSources(serviceRegistry);

            // Register explicit entity classes
            if (entityClasses != null) {
                for (Class<?> clazz : entityClasses) {
                    metadataSources.addAnnotatedClass(clazz);
                }
            }

            return metadataSources.buildMetadata().buildSessionFactory();
        } catch (Exception e) {
            // Cleanup service registry to prevent resource leaks on bootstrap failure
            if (serviceRegistry != null) {
                StandardServiceRegistryBuilder.destroy(serviceRegistry);
                serviceRegistry = null;
            }
            sessionFactory = null;
            sessionFactoryOwned = false;
            throw new RuntimeCamelException("Failed to bootstrap Hibernate SessionFactory", e);
        }
    }

    private DataSource resolveDataSource() {
        if (dataSource == null) {
            return getCamelContext().getRegistry().findSingleByType(DataSource.class);
        }
        if (dataSource instanceof DataSource ds) {
            return ds;
        }
        if (dataSource instanceof String dsName) {
            DataSource ds = getCamelContext().getRegistry().lookupByNameAndType(dsName, DataSource.class);
            if (ds == null) {
                throw new IllegalArgumentException(
                        "DataSource bean with name '" + dsName + "' could not be found in Camel registry.");
            }
            return ds;
        }
        throw new IllegalArgumentException(
                "Configured dataSource is neither a DataSource instance nor a valid registry name string.");
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
        this.sessionFactoryOwned = false;
    }

    public Object getDataSource() {
        return dataSource;
    }

    public void setDataSource(Object dataSource) {
        this.dataSource = dataSource;
    }

    public Class[] getEntityClasses() {
        return entityClasses;
    }

    public void setEntityClasses(Class[] entityClasses) {
        this.entityClasses = entityClasses;
    }

    public String getSchemaAction() {
        return schemaAction;
    }

    public void setSchemaAction(String schemaAction) {
        this.schemaAction = schemaAction;
    }

    public Map<String, Object> getHibernateProperties() {
        return hibernateProperties;
    }

    public void setHibernateProperties(Map<String, Object> hibernateProperties) {
        this.hibernateProperties = hibernateProperties;
    }
}
