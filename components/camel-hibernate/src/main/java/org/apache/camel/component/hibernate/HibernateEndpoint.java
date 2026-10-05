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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.hibernate.SessionFactory;

@UriEndpoint(firstVersion = "4.23.0", scheme = "hibernate", title = "Hibernate", syntax = "hibernate:entityClassName",
             category = { Category.DATABASE }, headersClass = HibernateConstants.class)
public class HibernateEndpoint extends ScheduledPollEndpoint {

    @UriPath(description = "Target entity class name or entity type name")
    @Metadata(required = true)
    private String entityClassName;

    @UriParam(description = "The HQL selection query to execute.")
    private String selectionQuery;

    @UriParam(description = "The HQL mutation query to execute.")
    private String mutationQuery;

    @UriParam(description = "The natural-id property values used for lookup.")
    private Map<String, Object> naturalIdParameters;

    @UriParam(description = "Whether the Hibernate session and selection query should be read-only.")
    private boolean readOnly;

    @UriParam(description = "Hibernate filters and their parameter values.")
    private Map<String, Map<String, Object>> filters;

    @UriParam(description = "The tenant identifier used to create the Hibernate session.")
    private String tenantIdentifier;

    @UriParam(description = "Stateless operation to perform: insert or upsert.")
    private String statelessOperation;

    @UriParam(description = "Whether selection query results should be returned as a stream.")
    private boolean streaming;

    @UriParam(description = "Whether the consumer should skip rows that are already locked by another consumer.")
    private boolean skipLocked;

    @UriParam(description = "The maximum number of entities to retrieve in a single poll.")
    private int maximumResults;

    private Class<?> entityType;
    private SessionFactory sessionFactory;

    public HibernateEndpoint() {
    }

    public HibernateEndpoint(String uri, HibernateComponent component) {
        super(uri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HibernateProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        HibernateConsumer consumer = new HibernateConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected void doStart() throws Exception {
        if (sessionFactory == null) {
            sessionFactory = ((HibernateComponent) getComponent()).getSessionFactory();
        }
        if (sessionFactory == null) {
            throw new IllegalArgumentException("SessionFactory must be configured or available on HibernateComponent");
        }

        boolean hasSelectionQuery = selectionQuery != null && !selectionQuery.isBlank();
        boolean hasMutationQuery = mutationQuery != null && !mutationQuery.isBlank();
        boolean hasNaturalIdParameters = naturalIdParameters != null && !naturalIdParameters.isEmpty();
        boolean hasStatelessOperation = statelessOperation != null && !statelessOperation.isBlank();

        int configuredOperations = 0;
        if (hasSelectionQuery) {
            configuredOperations++;
        }
        if (hasMutationQuery) {
            configuredOperations++;
        }
        if (hasNaturalIdParameters) {
            configuredOperations++;
        }

        if (hasStatelessOperation) {
            configuredOperations++;
        }

        if (configuredOperations != 1) {
            throw new IllegalArgumentException(
                    "Exactly one of selectionQuery, mutationQuery, naturalIdParameters or statelessOperation must be configured");
        }

        if (hasStatelessOperation && !statelessOperation.equals("insert") && !statelessOperation.equals("upsert")) {
            throw new IllegalArgumentException("Invalid statelessOperation: " + statelessOperation);
        }

        if (streaming && !hasSelectionQuery) {
            throw new IllegalArgumentException("streaming requires selectionQuery");
        }

        resolveEntityType();
        super.doStart();
    }

    protected void resolveEntityType() throws ClassNotFoundException {
        if (entityType == null && entityClassName != null && !entityClassName.isEmpty()) {
            this.entityType = getCamelContext().getClassResolver().resolveMandatoryClass(entityClassName);
        }
    }

    public String getEntityClassName() {
        return entityClassName;
    }

    public void setEntityClassName(String entityClassName) {
        this.entityClassName = entityClassName;
    }

    public Class<?> getEntityType() {
        return entityType;
    }

    public void setEntityType(Class<?> entityType) {
        this.entityType = entityType;
        if (entityType != null) {
            this.entityClassName = entityType.getName();
        }
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public String getSelectionQuery() {
        return selectionQuery;
    }

    public void setSelectionQuery(String selectionQuery) {
        this.selectionQuery = selectionQuery;
    }

    public String getMutationQuery() {
        return mutationQuery;
    }

    public void setMutationQuery(String mutationQuery) {
        this.mutationQuery = mutationQuery;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public Map<String, Object> getNaturalIdParameters() {
        return naturalIdParameters;
    }

    public void setNaturalIdParameters(Map<String, Object> naturalIdParameters) {
        this.naturalIdParameters = naturalIdParameters;
    }

    public Map<String, Map<String, Object>> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, Map<String, Object>> filters) {
        this.filters = filters;
    }

    public String getTenantIdentifier() {
        return tenantIdentifier;
    }

    public void setTenantIdentifier(String tenantIdentifier) {
        this.tenantIdentifier = tenantIdentifier;
    }

    public String getStatelessOperation() {
        return statelessOperation;
    }

    public void setStatelessOperation(String statelessOperation) {
        this.statelessOperation = statelessOperation;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }

    public boolean isSkipLocked() {
        return skipLocked;
    }

    public void setSkipLocked(boolean skipLocked) {
        this.skipLocked = skipLocked;
    }

    public int getMaximumResults() {
        return maximumResults;
    }

    public void setMaximumResults(int maximumResults) {
        this.maximumResults = maximumResults;
    }
}
