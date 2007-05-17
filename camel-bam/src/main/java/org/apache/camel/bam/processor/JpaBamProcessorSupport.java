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
package org.apache.camel.bam.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.bam.model.ProcessDefinition;
import org.apache.camel.bam.rules.ActivityRules;
import org.apache.camel.util.IntrospectionSupport;
import org.springframework.orm.jpa.JpaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * A base class for JPA based BAM which can use any entity to store the process instance information which
 * allows derived classes to specialise the process instance entity.
 *
 * @version $Revision: $
 */
public class JpaBamProcessorSupport<T> extends BamProcessorSupport<T> {
    private ActivityRules activityRules;
    private JpaTemplate template;
    private String findByKeyQuery;
    private String keyPropertyName = "correlationKey";

    public JpaBamProcessorSupport(TransactionTemplate transactionTemplate, JpaTemplate template, Expression<Exchange> correlationKeyExpression, ActivityRules activityRules, Class<T> entitytype) {
        super(transactionTemplate, correlationKeyExpression, entitytype);
        this.activityRules = activityRules;
        this.template = template;
    }

    public JpaBamProcessorSupport(TransactionTemplate transactionTemplate, JpaTemplate template, Expression<Exchange> correlationKeyExpression, ActivityRules activityRules) {
        super(transactionTemplate, correlationKeyExpression);
        this.activityRules = activityRules;
        this.template = template;
    }

    public String getFindByKeyQuery() {
        if (findByKeyQuery == null) {
            findByKeyQuery = createFindByKeyQuery();
        }
        return findByKeyQuery;
    }

    public void setFindByKeyQuery(String findByKeyQuery) {
        this.findByKeyQuery = findByKeyQuery;
    }

    public ActivityRules getActivityRules() {
        return activityRules;
    }

    public void setActivityRules(ActivityRules activityRules) {
        this.activityRules = activityRules;
    }

    public String getKeyPropertyName() {
        return keyPropertyName;
    }

    public void setKeyPropertyName(String keyPropertyName) {
        this.keyPropertyName = keyPropertyName;
    }

    public JpaTemplate getTemplate() {
        return template;
    }

    public void setTemplate(JpaTemplate template) {
        this.template = template;
    }

    // Implementatiom methods
    //-----------------------------------------------------------------------
    protected T loadEntity(Exchange exchange, Object key) {
        List<T> list = template.find(getFindByKeyQuery(), key);
        T entity = null;
        if (!list.isEmpty()) {
            entity = list.get(0);
        }
        if (entity == null) {
            entity = createEntity(exchange, key);
            setKeyProperty(entity, key);
            ProcessDefinition definition = ProcessDefinition.getRefreshedProcessDefinition(template, getActivityRules().getProcessRules().getProcessDefinition());
            setProcessDefinitionProperty(entity, definition);
            template.persist(entity);
        }
        return entity;
    }

    /**
     * Sets the key property on the new entity
     */
    protected void setKeyProperty(T entity, Object key) {
        IntrospectionSupport.setProperty(entity, getKeyPropertyName(), key);
    }

    protected void setProcessDefinitionProperty(T entity, ProcessDefinition processDefinition) {
        IntrospectionSupport.setProperty(entity, "processDefinition", processDefinition);
    }

    /**
     * Create a new instance of the entity for the given key
     */
    protected T createEntity(Exchange exchange, Object key) {
        return (T) exchange.getContext().getInjector().newInstance(getEntityType());
    }

    protected void processEntity(Exchange exchange, T entity) throws Exception {
        if (entity instanceof Processor) {
            Processor processor = (Processor) entity;
            processor.process(exchange);
        }
        else {
            // TODO add other extension points - eg. passing in Activity
            throw new IllegalArgumentException("No processor defined for this route");
        }
    }

    protected String createFindByKeyQuery() {
        return "select x from " + getEntityType().getName() + " x where x." + getKeyPropertyName() + " = ?1";
    }
}
