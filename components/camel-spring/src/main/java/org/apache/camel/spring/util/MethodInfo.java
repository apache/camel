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

import org.aopalliance.intercept.MethodInvocation;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @version $Revision: $
 */
public class MethodInfo {
    private Class type;
    private Method method;
    private Expression parametersExpression;

    public MethodInfo(Class type, Method method, Expression parametersExpression) {
        this.type = type;
        this.method = method;
        this.parametersExpression = parametersExpression;
    }

    public MethodInvocation createMethodInvocation(final Object pojo, final Exchange messageExchange) {
        final Object[] arguments = (Object[]) parametersExpression.evaluate(messageExchange);
        return new MethodInvocation() {
            public Method getMethod() {
                return method;
            }

            public Object[] getArguments() {
                return arguments;
            }

            public Object proceed() throws Throwable {
                return invoke(method, pojo, arguments, messageExchange);
            }

            public Object getThis() {
                return pojo;
            }

            public AccessibleObject getStaticPart() {
                return method;
            }
        };
    }

    public Class getType() {
        return type;
    }

    public Method getMethod() {
        return method;
    }

    public Expression getParametersExpression() {
        return parametersExpression;
    }

    protected Object invoke(Method mth, Object pojo, Object[] arguments, Exchange exchange) throws IllegalAccessException, InvocationTargetException {
        return mth.invoke(pojo, arguments);
    }
}
