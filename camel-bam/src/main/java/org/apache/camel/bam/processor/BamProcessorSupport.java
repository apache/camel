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
import org.apache.camel.RuntimeCamelException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * A base {@link Processor} for working on
 * <a href="http://activemq.apache.org/camel/bam.html">BAM</a> which a derived class would do the actual
 * persistence such as the {@link JpaBamProcessor}
 *
 * @version $Revision: $
 */
public abstract class BamProcessorSupport<T> implements Processor {
    private static final transient Log log = LogFactory.getLog(BamProcessorSupport.class);
    private Class<T> entityType;
    private Expression<Exchange> correlationKeyExpression;
    private TransactionTemplate transactionTemplate;

    protected BamProcessorSupport(TransactionTemplate transactionTemplate, Expression<Exchange> correlationKeyExpression) {
        this.transactionTemplate = transactionTemplate;
        this.correlationKeyExpression = correlationKeyExpression;

        Type type = getClass().getGenericSuperclass();
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type[] arguments = parameterizedType.getActualTypeArguments();
            if (arguments.length > 0) {
                Type argumentType = arguments[0];
                if (argumentType instanceof Class) {
                    this.entityType = (Class<T>) argumentType;
                }
            }
        }
        if (entityType == null) {
            throw new IllegalArgumentException("Could not infer the entity type!");
        }
    }

    protected BamProcessorSupport(TransactionTemplate transactionTemplate, Expression<Exchange> correlationKeyExpression, Class<T> entitytype) {
        this.transactionTemplate = transactionTemplate;
        this.entityType = entitytype;
        this.correlationKeyExpression = correlationKeyExpression;
    }

    public void process(final Exchange exchange) {
        transactionTemplate.execute(new TransactionCallback() {
            public Object doInTransaction(TransactionStatus status) {
                try {
                    Object key = getCorrelationKey(exchange);

                    T entity = loadEntity(exchange, key);

                    if (log.isDebugEnabled()) {
                        log.debug("Correlation key: " + key + " with entity: " + entity);
                    }
                    processEntity(exchange, entity);

                    return entity;
                }
                catch (Exception e) {
                    throw new RuntimeCamelException(e);
                }
            }
        });
    }

    // Properties
    //-----------------------------------------------------------------------
    public Expression<Exchange> getCorrelationKeyExpression() {
        return correlationKeyExpression;
    }

    public Class<T> getEntityType() {
        return entityType;
    }

    // Implemenation methods
    //-----------------------------------------------------------------------
    protected abstract void processEntity(Exchange exchange, T entity) throws Exception;

    protected abstract T loadEntity(Exchange exchange, Object key);

    protected Object getCorrelationKey(Exchange exchange) throws NoCorrelationKeyException {
        Object value = correlationKeyExpression.evaluate(exchange);
        if (value == null) {
            throw new NoCorrelationKeyException(this, exchange);
        }
        return value;
    }
}
