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
package org.apache.camel.spring.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.camel.Expression;
import org.apache.camel.Exchange;
import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Message;
import org.apache.camel.builder.ExpressionBuilder;

/**
 * Represents the strategy used to figure out how to map a message exchange to a POJO method invocation
 *
 * @version $Revision:$
 */
public class DefaultMethodInvocationStrategy implements MethodInvocationStrategy {

    private Map<Class, Expression> parameterTypeToExpressionMap = new ConcurrentHashMap<Class, Expression>();

    public DefaultMethodInvocationStrategy() {
    }

    public synchronized Expression getDefaultParameterTypeExpression(Class parameterType) {
        return parameterTypeToExpressionMap.get(parameterType);
    }

    /**
     * Adds a default parameter type mapping to an expression
     */
    public synchronized void addParameterMapping(Class parameterType, Expression expression) {
        parameterTypeToExpressionMap.put(parameterType, expression);
    }


    /**
     * Creates an invocation on the given POJO using annotations to decide which method to invoke
     * and to figure out which parameters to use
     */
/*
    public MethodInvocation createInvocation(Object pojo,
                                             BeanInfo beanInfo, 
                                             Exchange messageExchange,
                                             Endpoint pojoEndpoint) throws RuntimeCamelException {
        return beanInfo.createInvocation(pojo, messageExchange);
    }
*/


    public void loadDefaultRegistry() {
        addParameterMapping(Exchange.class, ExpressionBuilder.exchangeExpression());
        addParameterMapping(Message.class, ExpressionBuilder.inMessageExpression());
    }


}
