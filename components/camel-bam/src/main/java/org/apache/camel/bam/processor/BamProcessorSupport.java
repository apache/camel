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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.apache.camel.util.ObjectHelper.wrapRuntimeCamelException;

/**
 * A base {@link Processor} for working on <a
 * href="http://camel.apache.org/bam.html">BAM</a> which a derived
 * class would do the actual persistence such as the {@link JpaBamProcessor}
 *
 * @version 
 */
public abstract class BamProcessorSupport<T> implements Processor {
    private static final Logger LOG = LoggerFactory.getLogger(BamProcessorSupport.class);
    private Class<T> entityType;
    private Expression correlationKeyExpression;
    private TransactionTemplate transactionTemplate;
    private int retryCount = 20;
    private long retrySleep = 1000L;

    @SuppressWarnings("unchecked")
    protected BamProcessorSupport(TransactionTemplate transactionTemplate,
                                  Expression correlationKeyExpression) {
        this.transactionTemplate = transactionTemplate;
        this.correlationKeyExpression = correlationKeyExpression;

        Type type = getClass().getGenericSuperclass();
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType)type;
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

    protected BamProcessorSupport(TransactionTemplate transactionTemplate,
                                  Expression correlationKeyExpression, Class<T> entitytype) {
        this.transactionTemplate = transactionTemplate;
        this.entityType = entitytype;
        this.correlationKeyExpression = correlationKeyExpression;
    }

    public void process(final Exchange exchange) {
        for (int i = 1; i <= retryCount; i++) {
            if (i > 1) {
                LOG.info("Retrying attempt: " + i);
                try {
                    Thread.sleep(retrySleep);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            try {
                transactionTemplate.execute(new TransactionCallback<T>() {
                    public T doInTransaction(TransactionStatus status) {
                        try {
                            Object key = getCorrelationKey(exchange);

                            T entity = loadEntity(exchange, key);

                            LOG.debug("Correlation key: {} with entity: {}", key, entity);
                            processEntity(exchange, entity);

                            return entity;
                        } catch (Exception e) {
                            onError(status, e);
                            return null;
                        }
                    }
                });
                if (i > 1) {
                    LOG.debug("Attempt {} worked!", i);
                }
                return;
            } catch (Exception e) {
                LOG.warn("Failed to complete transaction. This exception is ignored.", e);
            }
        }
    }

    // Properties
    // -----------------------------------------------------------------------
    public Expression getCorrelationKeyExpression() {
        return correlationKeyExpression;
    }

    public Class<T> getEntityType() {
        return entityType;
    }

    // Implemenation methods
    // -----------------------------------------------------------------------
    protected abstract void processEntity(Exchange exchange, T entity) throws Exception;

    protected abstract T loadEntity(Exchange exchange, Object key) throws Exception;

    protected abstract Class<?> getKeyType();

    protected Object getCorrelationKey(Exchange exchange) throws NoCorrelationKeyException {
        Object value;
        Class<?> keyType = getKeyType();
        if (keyType != null) {
            value = correlationKeyExpression.evaluate(exchange, keyType);
        } else {
            value = correlationKeyExpression.evaluate(exchange, Object.class);
        }
        if (value == null) {
            throw new NoCorrelationKeyException(this, exchange);
        }
        return value;
    }

    protected void onError(TransactionStatus status, Exception e) throws RuntimeCamelException {
        status.setRollbackOnly();
        LOG.warn("Caught: " + e, e);
        throw wrapRuntimeCamelException(e);
    }

}
