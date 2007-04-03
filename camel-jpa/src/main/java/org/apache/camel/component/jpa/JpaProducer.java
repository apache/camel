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

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.converter.ObjectConverter;
import org.apache.camel.impl.DefaultProducer;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.util.Iterator;

/**
 * @version $Revision$
 */
public class JpaProducer extends DefaultProducer<Exchange> {
    private EntityManager entityManager;
    private final JpaEndpoint endpoint;
    private Expression<Exchange> expression;

    public JpaProducer(JpaEndpoint endpoint, EntityManager entityManager, Expression<Exchange> expression) {
        super(endpoint);
        this.endpoint = endpoint;
        this.entityManager = entityManager;
        this.expression = expression;
    }

    public void onExchange(Exchange exchange) {
        Object values = expression.evaluate(exchange);
        if (values != null) {
            // TODO remove explicit transaction handling?
            EntityTransaction transaction = entityManager.getTransaction();
            transaction.begin();
            try {
                Iterator iter = ObjectConverter.iterator(values);
                while (iter.hasNext()) {
                    Object value = iter.next();
                    entityManager.persist(value);
                }
                transaction.commit();
            }
            catch (RuntimeException e) {
                transaction.rollback();
                throw e;
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        entityManager.close();
        super.doStop();
    }
}
