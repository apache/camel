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
package org.apache.camel.bam;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;

import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;

/**
 * A {@link Processor} for working on
 * <a href="http://activemq.apache.org/camel/bam.html">BAM</a>
 *
 * @version $Revision: $
 */
public abstract class BamProcessorSupport<T> implements Processor {
    private Class<T> entityType;
    private Expression<Exchange> correlationKeyExpression;


    protected BamProcessorSupport(Expression<Exchange> correlationKeyExpression) {
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

    protected BamProcessorSupport(Class<T> entitytype, Expression<Exchange> correlationKeyExpression) {
        this.entityType = entitytype;
        this.correlationKeyExpression = correlationKeyExpression;
    }

    public void process(Exchange exchange) throws Exception {
        Object key = getCorrelationKey(exchange);


        T entity = loadEntity(exchange, key);
        //storeProcessInExchange(exchange, entity);
        processEntity(exchange, entity);
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
