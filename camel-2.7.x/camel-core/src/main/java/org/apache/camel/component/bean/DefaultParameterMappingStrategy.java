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
package org.apache.camel.component.bean;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.TypeConverter;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.spi.Registry;

/**
 * Represents the strategy used to figure out how to map a message exchange to a POJO method invocation
 *
 * @version 
 */
public class DefaultParameterMappingStrategy implements ParameterMappingStrategy {
    private final Map<Class<?>, Expression> parameterTypeToExpressionMap = new ConcurrentHashMap<Class<?>, Expression>();

    public DefaultParameterMappingStrategy() {
        loadDefaultRegistry();
    }

    public Expression getDefaultParameterTypeExpression(Class<?> parameterType) {
        return parameterTypeToExpressionMap.get(parameterType);
    }

    /**
     * Adds a default parameter type mapping to an expression
     */
    public void addParameterMapping(Class<?> parameterType, Expression expression) {
        parameterTypeToExpressionMap.put(parameterType, expression);
    }

    public void loadDefaultRegistry() {
        addParameterMapping(Exchange.class, ExpressionBuilder.exchangeExpression());
        addParameterMapping(Message.class, ExpressionBuilder.inMessageExpression());
        addParameterMapping(Exception.class, ExpressionBuilder.exchangeExceptionExpression());
        addParameterMapping(TypeConverter.class, ExpressionBuilder.typeConverterExpression());
        addParameterMapping(Registry.class, ExpressionBuilder.registryExpression());
        addParameterMapping(CamelContext.class, ExpressionBuilder.camelContextExpression());
    }
}
