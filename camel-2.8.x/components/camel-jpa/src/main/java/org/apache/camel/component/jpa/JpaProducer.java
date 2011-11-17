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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.impl.DefaultProducer;
import org.springframework.orm.jpa.JpaCallback;

/**
 * @version 
 */
public class JpaProducer extends DefaultProducer {
    private final TransactionStrategy template;
    private final JpaEndpoint endpoint;
    private final Expression expression;

    public JpaProducer(JpaEndpoint endpoint, Expression expression) {
        super(endpoint);
        this.endpoint = endpoint;
        this.expression = expression;
        this.template = endpoint.createTransactionStrategy();
    }

    public void process(final Exchange exchange) {
        exchange.getIn().setHeader(JpaConstants.JPA_TEMPLATE, endpoint.getTemplate());
        final Object values = expression.evaluate(exchange, Object.class);
        if (values != null) {
            template.execute(new JpaCallback() {
                @SuppressWarnings("unchecked")
                public Object doInJpa(EntityManager entityManager) throws PersistenceException {
                    if (values.getClass().isArray()) {
                        Object[] array = (Object[]) values;
                        for (int index = 0; index < array.length; index++) {
                            Object managedEntity = save(array[index], entityManager);
                            if (!endpoint.isUsePersist()) {
                                array[index] = managedEntity;
                            }
                        }
                    } else if (values instanceof Collection) {
                        Collection collection = (Collection) values;
                        List managedEntities = new ArrayList();
                        for (Iterator iter = collection.iterator(); iter.hasNext();) {
                            Object managedEntity = save(iter.next(), entityManager);
                            managedEntities.add(managedEntity);
                        }
                        if (!endpoint.isUsePersist()) {
                            collection.clear();
                            collection.addAll(managedEntities);
                        }
                    } else {
                        Object managedEntity = save(values, entityManager);
                        if (!endpoint.isUsePersist()) {
                            exchange.getIn().setBody(managedEntity);                            
                        }
                    }
                    
                    if (endpoint.isFlushOnSend()) {
                        entityManager.flush();
                    }
                    return null;
                }

                /**
                 * save the given entity end return the managed entity
                 * 
                 * @param entity
                 * @param entityManager
                 * @return the managed entity
                 */
                private Object save(final Object entity, EntityManager entityManager) {
                    if (endpoint.isUsePersist()) {
                        entityManager.persist(entity);
                        return entity;
                    } else {
                        return entityManager.merge(entity);
                    }
                }
            });
        }
        exchange.getIn().removeHeader(JpaConstants.JPA_TEMPLATE);
    }
}
