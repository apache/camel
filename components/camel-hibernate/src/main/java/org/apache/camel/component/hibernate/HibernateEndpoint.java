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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.PropertiesHelper;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.resource.transaction.spi.TransactionCoordinatorBuilder;

@UriEndpoint(firstVersion = "4.24.0", scheme = "hibernate", title = "Hibernate", syntax = "hibernate:entityClassName",
             category = { Category.DATABASE }, headersClass = HibernateConstants.class)
public class HibernateEndpoint extends ScheduledPollEndpoint {

    @UriPath(description = "Target entity class name or entity type name")
    @Metadata(required = true)
    private String entityClassName;

    @UriParam(description = "The HQL selection query to execute.", label = "producer,consumer")
    private String selectionQuery;

    @UriParam(description = "The HQL mutation query to execute.", label = "producer")
    private String mutationQuery;

    @UriParam(description = "The natural-id property values used for lookup. String values can use Simple expressions from the message.",
              label = "producer", prefix = "naturalId.", multiValue = true)
    @Metadata(supportSimpleExpression = true)
    private Map<String, Object> naturalIdParameters;

    @UriParam(description = "Whether the Hibernate session and selection query should be read-only.",
              label = "producer,consumer")
    private boolean readOnly;

    @UriParam(description = "Hibernate filters and their parameter values.", label = "producer,consumer",
              prefix = "filter.", multiValue = true)
    private Map<String, Map<String, Object>> filters;

    @UriParam(description = "The tenant identifier used to create the Hibernate session.",
              label = "producer,consumer")
    private String tenantIdentifier;

    public enum StatelessOperation {
        INSERT,
        UPSERT
    }

    @UriParam(description = "Stateless operation to perform: insert or upsert.", label = "producer")
    private StatelessOperation statelessOperation;

    @UriParam(description = "Whether selection query results should be returned as a stream.", label = "producer")
    private boolean streaming;

    @UriParam(description = "Whether the consumer should skip rows that are already locked by another consumer.",
              label = "consumer")
    private boolean skipLocked;

    @UriParam(description = "The maximum number of entities to retrieve in a single poll.", label = "consumer")
    private int maximumResults;

    @UriParam(description = "Whether to delete consumed entities after successful processing.",
              label = "consumer", defaultValue = "true")
    private boolean consumeDelete = true;

    private Class<?> entityType;
    private SessionFactory sessionFactory;
    private boolean jta;

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
    public void configureProperties(Map<String, Object> options) {
        Map<String, Object> naturalId = PropertiesHelper.extractProperties(options, "naturalId.");
        if (!naturalId.isEmpty()) {
            setNaturalIdParameters(naturalId);
        }
        Map<String, Object> extractedFilters = PropertiesHelper.extractProperties(options, "filter.");
        if (!extractedFilters.isEmpty()) {
            setFilters(adaptFilters(extractedFilters));
        }
        super.configureProperties(options);
    }

    @Override
    protected void doStart() throws Exception {
        if (sessionFactory == null) {
            sessionFactory = ((HibernateComponent) getComponent()).getSessionFactory();
        }
        if (sessionFactory == null) {
            throw new IllegalArgumentException("SessionFactory must be configured or available on HibernateComponent");
        }

        jta = sessionFactory.unwrap(SessionFactoryImplementor.class)
                .getServiceRegistry()
                .requireService(TransactionCoordinatorBuilder.class)
                .isJta();

        boolean hasSelectionQuery = selectionQuery != null && !selectionQuery.isBlank();
        boolean hasMutationQuery = mutationQuery != null && !mutationQuery.isBlank();
        boolean hasNaturalIdParameters = naturalIdParameters != null && !naturalIdParameters.isEmpty();
        boolean hasStatelessOperation = statelessOperation != null;

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

        if (streaming && !hasSelectionQuery) {
            throw new IllegalArgumentException("streaming requires selectionQuery");
        }

        if (maximumResults < 0) {
            throw new IllegalArgumentException("maximumResults cannot be negative");
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

    public boolean isJta() {
        return jta;
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

    public StatelessOperation getStatelessOperation() {
        return statelessOperation;
    }

    public void setStatelessOperation(StatelessOperation statelessOperation) {
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

    public boolean isConsumeDelete() {
        return consumeDelete;
    }

    public void setConsumeDelete(boolean consumeDelete) {
        this.consumeDelete = consumeDelete;
    }

    private Map<String, Map<String, Object>> adaptFilters(Map<String, Object> extracted) {
        Map<String, Map<String, Object>> adapted = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : extracted.entrySet()) {
            Object value = resolveFilterValue(entry.getValue());
            if (value instanceof Map<?, ?> nested) {
                Map<String, Object> parameters = new LinkedHashMap<>();
                nested.forEach((key, nestedValue) -> parameters.put(String.valueOf(key), nestedValue));
                adapted.put(entry.getKey(), parameters);
            } else {
                String key = entry.getKey();
                int dot = key.indexOf('.');
                if (dot < 0) {
                    adapted.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
                } else {
                    adapted.computeIfAbsent(key.substring(0, dot), ignored -> new LinkedHashMap<>())
                            .put(key.substring(dot + 1), value);
                }
            }
        }
        return adapted;
    }

    private Object resolveFilterValue(Object value) {
        if (value instanceof String str && EndpointHelper.isReferenceParameter(str) && getCamelContext() != null) {
            return EndpointHelper.resolveReferenceParameter(getCamelContext(), str, Object.class, false);
        }
        return value;
    }
}
