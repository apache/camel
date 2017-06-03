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

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.bam.EntityManagerCallback;
import org.apache.camel.bam.EntityManagerTemplate;
import org.apache.camel.bam.QueryUtils;
import org.apache.camel.bam.model.ProcessDefinition;
import org.apache.camel.bam.rules.ActivityRules;
import org.apache.camel.util.IntrospectionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A base class for JPA based BAM which can use any entity to store the process
 * instance information which allows derived classes to specialise the process
 * instance entity.
 *
 * @version
 */
public class JpaBamProcessorSupport<T> extends BamProcessorSupport<T> {
    private static final Logger LOG = LoggerFactory.getLogger(JpaBamProcessorSupport.class);

    private static final Lock LOCK = new ReentrantLock(); // lock used for concurrency issues

    private ActivityRules activityRules;
    private EntityManagerFactory entityManagerFactory;
    private EntityManagerTemplate entityManagerTemplate;
    private String findByKeyQuery;
    private String keyPropertyName = "correlationKey";
    private boolean correlationKeyIsPrimary = true;

    public JpaBamProcessorSupport(TransactionTemplate transactionTemplate, EntityManagerFactory entityManagerFactory,
            Expression correlationKeyExpression, ActivityRules activityRules, Class<T> entitytype) {
        super(transactionTemplate, correlationKeyExpression, entitytype);
        this.activityRules = activityRules;
        setEntityManagerFactory(entityManagerFactory);
    }

    public JpaBamProcessorSupport(TransactionTemplate transactionTemplate, EntityManagerFactory entityManagerFactory,
            Expression correlationKeyExpression, ActivityRules activityRules) {
        super(transactionTemplate, correlationKeyExpression);
        this.activityRules = activityRules;
        setEntityManagerFactory(entityManagerFactory);
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

    public EntityManagerFactory getEntityManagerFactory() {
        return entityManagerFactory;
    }

    public void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
        this.entityManagerTemplate = new EntityManagerTemplate(entityManagerFactory);
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
            LOG.trace("LoadEntity call");
            T entity = findEntityByCorrelationKey(key);
            if (entity == null) {
                entity = createEntity(exchange, key);
                setKeyProperty(entity, key);
                ProcessDefinition definition = ProcessDefinition.getRefreshedProcessDefinition(entityManagerTemplate,
                        getActivityRules().getProcessRules().getProcessDefinition());
                setProcessDefinitionProperty(entity, definition);
                entityManagerTemplate.persist(entity);

                // Now we must flush to avoid concurrent updates clashing trying to
                // insert the same row
                LOG.debug("About to flush on entity: {} with key: {}", entity, key);
                entityManagerTemplate.flush();
            }
            return entity;
        } finally {
            LOCK.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    protected T findEntityByCorrelationKey(final Object key) {
        if (isCorrelationKeyIsPrimary()) {
            return entityManagerTemplate.execute(new EntityManagerCallback<T>() {
                @Override
                public T execute(EntityManager entityManager) {
                    return entityManager.find(getEntityType(), key);
                }
            });
        } else {
            List<T> list = entityManagerTemplate.execute(new EntityManagerCallback<List<T>>() {
                @Override
                public List<T> execute(EntityManager entityManager) {
                    return entityManager.createQuery(getFindByKeyQuery()).setParameter("key", key).getResultList();
                }
            });
            if (list.isEmpty()) {
                return null;
            } else {
                return list.get(0);
            }
        }
    }

    protected Class<?> getKeyType() {
        try {
            Method getter = IntrospectionSupport.getPropertyGetter(getEntityType(), getKeyPropertyName());
            return getter.getReturnType();
        } catch (NoSuchMethodException e) {
            LOG.warn("no such getter for: " + getKeyPropertyName() + " on " + getEntityType() + ". This exception will be ignored.", e);
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
        return exchange.getContext().getInjector().newInstance(getEntityType());
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
        return "select x from " + QueryUtils.getTypeName(getEntityType()) + " x where x." + getKeyPropertyName() + " = :key";
    }
}
