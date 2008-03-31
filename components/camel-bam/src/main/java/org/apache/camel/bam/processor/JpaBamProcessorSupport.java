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
package org.apache.camel.bam.processor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.bam.model.ProcessDefinition;
import org.apache.camel.bam.rules.ActivityRules;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.orm.jpa.JpaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A base class for JPA based BAM which can use any entity to store the process
 * instance information which allows derived classes to specialise the process
 * instance entity.
 *
 * @version $Revision$
 */
public class JpaBamProcessorSupport<T> extends BamProcessorSupport<T> {
    private static final transient Log LOG = LogFactory.getLog(JpaBamProcessorSupport.class);

    private static final Lock LOCK = new ReentrantLock(); // lock used for concurrency issues
    private ActivityRules activityRules;
    private JpaTemplate template;
    private String findByKeyQuery;
    private String keyPropertyName = "correlationKey";
    private boolean correlationKeyIsPrimary = true;

    public JpaBamProcessorSupport(TransactionTemplate transactionTemplate, JpaTemplate template,
                                  Expression<Exchange> correlationKeyExpression, ActivityRules activityRules,
                                  Class<T> entitytype) {
        super(transactionTemplate, correlationKeyExpression, entitytype);
        this.activityRules = activityRules;
        this.template = template;
    }

    public JpaBamProcessorSupport(TransactionTemplate transactionTemplate, JpaTemplate template,
                                  Expression<Exchange> correlationKeyExpression, ActivityRules activityRules) {
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

    public boolean isCorrelationKeyIsPrimary() {
        return correlationKeyIsPrimary;
    }

    public void setCorrelationKeyIsPrimary(boolean correlationKeyIsPrimary) {
        this.correlationKeyIsPrimary = correlationKeyIsPrimary;
    }

    // Implementatiom methods
    // -----------------------------------------------------------------------
    protected T loadEntity(Exchange exchange, Object key) throws Exception {
        LOCK.lock();
        try {
            T entity = findEntityByCorrelationKey(key);
            if (entity == null) {
                entity = createEntity(exchange, key);
                setKeyProperty(entity, key);
                ProcessDefinition definition = ProcessDefinition
                    .getRefreshedProcessDefinition(template, getActivityRules().getProcessRules()
                        .getProcessDefinition());
                setProcessDefinitionProperty(entity, definition);
                template.persist(entity);

                // Now we must flush to avoid concurrent updates clashing trying to
                // insert the same row
                LOG.debug("About to flush on entity: " + entity + " with key: " + key);
                template.flush();
            }
            return entity;
        } finally {
            LOCK.unlock();
        }
    }

    protected T findEntityByCorrelationKey(Object key) {
        if (isCorrelationKeyIsPrimary()) {
            return template.find(getEntityType(), key);
        } else {
            List<T> list = template.find(getFindByKeyQuery(), key);
            if (list.isEmpty()) {
                return null;
            } else {
                return list.get(0);
            }
        }
    }

    protected Class getKeyType() {
        try {
            Method getter = IntrospectionSupport.getPropertyGetter(getEntityType(), getKeyPropertyName());
            return getter.getReturnType();
        } catch (NoSuchMethodException e) {
            LOG.warn("no such getter for: " + getKeyPropertyName() + " on " + getEntityType() + ". Reason: "
                     + e, e);
            return null;
        }
    }

    /**
     * Sets the key property on the new entity
     */
    protected void setKeyProperty(T entity, Object key) throws Exception {
        IntrospectionSupport.setProperty(entity, getKeyPropertyName(), key);
    }

    protected void setProcessDefinitionProperty(T entity, ProcessDefinition processDefinition)
        throws Exception {
        IntrospectionSupport.setProperty(entity, "processDefinition", processDefinition);
    }

    /**
     * Create a new instance of the entity for the given key
     */
    protected T createEntity(Exchange exchange, Object key) {
        return (T)exchange.getContext().getInjector().newInstance(getEntityType());
    }

    protected void processEntity(Exchange exchange, T entity) throws Exception {
        if (entity instanceof Processor) {
            Processor processor = (Processor)entity;
            processor.process(exchange);
        } else {
            // TODO add other extension points - eg. passing in Activity
            throw new IllegalArgumentException("No processor defined for this route");
        }
    }

    protected String createFindByKeyQuery() {
        return "select x from " + getEntityType().getName() + " x where x." + getKeyPropertyName() + " = ?1";
    }
}
