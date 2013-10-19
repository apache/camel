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

import java.util.Collection;

import javax.persistence.EntityManager;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @version 
 */
public class JpaProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(JpaProducer.class);
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final Expression expression;

    public JpaProducer(JpaEndpoint endpoint, Expression expression) {
        super(endpoint);
        this.expression = expression;
        this.entityManager = endpoint.createEntityManager();
        this.transactionTemplate = endpoint.createTransactionTemplate();
    }

    @Override
    public JpaEndpoint getEndpoint() {
        return (JpaEndpoint) super.getEndpoint();
    }

    public void process(final Exchange exchange) {
        exchange.getIn().setHeader(JpaConstants.ENTITYMANAGER, entityManager);
        final Object values = expression.evaluate(exchange, Object.class);
        if (values != null) {
            transactionTemplate.execute(new TransactionCallback<Object>() {
                public Object doInTransaction(TransactionStatus status) {
                    entityManager.joinTransaction();
                    if (values.getClass().isArray()) {
                        Object[] array = (Object[])values;
                        for (int index = 0; index < array.length; index++) {
                            save(array[index], entityManager);
                        }
                    } else if (values instanceof Collection) {
                        Collection<?> collection = (Collection<?>)values;
                        for (Object entity : collection) {
                            save(entity, entityManager);
                        }
                    } else {
                        Object managedEntity = save(values, entityManager);
                        if (!getEndpoint().isUsePersist()) {
                            exchange.getIn().setBody(managedEntity);
                        }
                    }

                    if (getEndpoint().isFlushOnSend()) {
                        // there may be concurrency so need to join tx before flush
                        entityManager.joinTransaction();
                        entityManager.flush();
                    }

                    return null;
                }

                /**
                 * save the given entity end return the managed entity
                 * @return the managed entity
                 */
                private Object save(final Object entity, EntityManager entityManager) {
                    // there may be concurrency so need to join tx before persist/merge
                    entityManager.joinTransaction();
                    if (getEndpoint().isUsePersist()) {
                        entityManager.persist(entity);
                        return entity;
                    } else {
                        return entityManager.merge(entity);
                    }
                }
            });
        }
        exchange.getIn().removeHeader(JpaConstants.ENTITYMANAGER);
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        entityManager.close();
        LOG.trace("closed the EntityManager {} on {}", entityManager, this);
    }

}
