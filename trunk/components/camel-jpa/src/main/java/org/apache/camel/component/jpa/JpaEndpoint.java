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
package org.apache.camel.component.jpa;

import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.InvalidPayloadRuntimeException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.ExpressionAdapter;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.springframework.orm.jpa.JpaTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * @version $Revision$
 */
public class JpaEndpoint extends ScheduledPollEndpoint {
    private EntityManagerFactory entityManagerFactory;
    private PlatformTransactionManager transactionManager;
    private String persistenceUnit = "camel";
    private JpaTemplate template;
    private Expression producerExpression;
    private int maximumResults = -1;
    private Class<?> entityType;
    private Map<Object, Object> entityManagerProperties;
    private boolean consumeDelete = true;
    private boolean consumeLockEntity = true;
    private boolean flushOnSend = true;
    private int maxMessagesPerPoll;
    private boolean usePersist;

    public JpaEndpoint() {
    }

    public JpaEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public JpaEndpoint(String uri, JpaComponent component) {
        super(uri, component);
        entityManagerFactory = component.getEntityManagerFactory();
        transactionManager = component.getTransactionManager();
    }

    public JpaEndpoint(String endpointUri, EntityManagerFactory entityManagerFactory) {
        super(endpointUri);
        this.entityManagerFactory = entityManagerFactory;
    }

    public JpaEndpoint(String endpointUri, EntityManagerFactory entityManagerFactory, PlatformTransactionManager transactionManager) {
        super(endpointUri);
        this.entityManagerFactory = entityManagerFactory;
        this.transactionManager = transactionManager;
    }

    public Producer createProducer() throws Exception {
        validate();
        return new JpaProducer(this, getProducerExpression());
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        validate();
        JpaConsumer consumer = new JpaConsumer(this, processor);
        consumer.setMaxMessagesPerPoll(getMaxMessagesPerPoll());
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public void configureProperties(Map<String, Object> options) {
        super.configureProperties(options);
        Map<String, Object> emProperties = IntrospectionSupport.extractProperties(options, "emf.");
        if (emProperties != null) {
            setEntityManagerProperties(CastUtils.cast(emProperties));
        }
    }

    public boolean isSingleton() {
        return false;
    }

    @Override
    protected String createEndpointUri() {
        return "jpa" + (entityType != null ? "://" + entityType.getName() : "");
    }


    // Properties
    // -------------------------------------------------------------------------
    public JpaTemplate getTemplate() {
        if (template == null) {
            template = createTemplate();
        }
        return template;
    }

    public void setTemplate(JpaTemplate template) {
        this.template = template;
    }

    public Expression getProducerExpression() {
        if (producerExpression == null) {
            producerExpression = createProducerExpression();
        }
        return producerExpression;
    }

    public void setProducerExpression(Expression producerExpression) {
        this.producerExpression = producerExpression;
    }

    public int getMaximumResults() {
        return maximumResults;
    }

    public void setMaximumResults(int maximumResults) {
        this.maximumResults = maximumResults;
    }

    public Class<?> getEntityType() {
        return entityType;
    }

    public void setEntityType(Class<?> entityType) {
        this.entityType = entityType;
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

    public PlatformTransactionManager getTransactionManager() {
        if (transactionManager == null) {
            transactionManager = createTransactionManager();
        }
        return transactionManager;
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public Map<Object, Object> getEntityManagerProperties() {
        if (entityManagerProperties == null) {
            entityManagerProperties = System.getProperties();
        }
        return entityManagerProperties;
    }

    public void setEntityManagerProperties(Map<Object, Object> entityManagerProperties) {
        this.entityManagerProperties = entityManagerProperties;
    }

    public String getPersistenceUnit() {
        return persistenceUnit;
    }

    public void setPersistenceUnit(String persistenceUnit) {
        this.persistenceUnit = persistenceUnit;
    }

    public boolean isConsumeDelete() {
        return consumeDelete;
    }

    public void setConsumeDelete(boolean consumeDelete) {
        this.consumeDelete = consumeDelete;
    }

    public boolean isConsumeLockEntity() {
        return consumeLockEntity;
    }

    public void setConsumeLockEntity(boolean consumeLockEntity) {
        this.consumeLockEntity = consumeLockEntity;
    }

    public boolean isFlushOnSend() {
        return flushOnSend;
    }

    public void setFlushOnSend(boolean flushOnSend) {
        this.flushOnSend = flushOnSend;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }
    
    public boolean isUsePersist() {
        return usePersist;
    }

    public void setUsePersist(boolean usePersist) {
        this.usePersist = usePersist;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    protected void validate() {
        ObjectHelper.notNull(getEntityManagerFactory(), "entityManagerFactory");
    }

    protected JpaTemplate createTemplate() {
        return new JpaTemplate(getEntityManagerFactory());
    }

    protected EntityManagerFactory createEntityManagerFactory() {
        return Persistence.createEntityManagerFactory(persistenceUnit, getEntityManagerProperties());
    }

    protected PlatformTransactionManager createTransactionManager() {
        JpaTransactionManager tm = new JpaTransactionManager(getEntityManagerFactory());
        tm.afterPropertiesSet();
        return tm;
    }

    protected EntityManager createEntityManager() {
        return getEntityManagerFactory().createEntityManager();
    }

    protected TransactionStrategy createTransactionStrategy() {
        return JpaTemplateTransactionStrategy.newInstance(getTransactionManager(), getTemplate());
    }

    protected Expression createProducerExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Object answer;

                // must have a body
                try {
                    if (getEntityType() == null) {
                        answer = exchange.getIn().getMandatoryBody();
                    } else {
                        answer = exchange.getIn().getMandatoryBody(getEntityType());
                    }
                } catch (InvalidPayloadException e) {
                    throw new InvalidPayloadRuntimeException(exchange, getEntityType(), e.getCause());
                }

                if (answer == null) {
                    throw new InvalidPayloadRuntimeException(exchange, getEntityType());
                } else {
                    return answer;
                }
            }
        };
    }
}
