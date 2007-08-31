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

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ExchangeHelper;

/**
 * @version $Revision: $
 */
public class MethodInfo {
    private Class type;
    private Method method;
    private final List<ParameterInfo> parameters;
    private final List<ParameterInfo> bodyParameters;
    private final boolean hasCustomAnnotation;
    private Expression parametersExpression;

    public MethodInfo(Class type, Method method, List<ParameterInfo> parameters, List<ParameterInfo> bodyParameters, boolean hasCustomAnnotation) {
        this.type = type;
        this.method = method;
        this.parameters = parameters;
        this.bodyParameters = bodyParameters;
        this.hasCustomAnnotation = hasCustomAnnotation;
        this.parametersExpression = createParametersExpression();
    }

    public String toString() {
        return method.toString();
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

    public List<ParameterInfo> getBodyParameters() {
        return bodyParameters;
    }

    public Class getBodyParameterType() {
        ParameterInfo parameterInfo = bodyParameters.get(0);
        return parameterInfo.getType();
    }


    public boolean bodyParameterMatches(Class bodyType) {
        Class actualType = getBodyParameterType();
        return actualType != null && ObjectHelper.isAssignableFrom(bodyType, actualType);
    }

    public List<ParameterInfo> getParameters() {
        return parameters;
    }

    public boolean hasBodyParameter() {
        return !bodyParameters.isEmpty();
    }

    public boolean isHasCustomAnnotation() {
        return hasCustomAnnotation;
    }

    protected Object invoke(Method mth, Object pojo, Object[] arguments, Exchange exchange) throws IllegalAccessException, InvocationTargetException {
        return mth.invoke(pojo, arguments);
    }

    protected Expression createParametersExpression() {
        final int size = parameters.size();
        final Expression[] expressions = new Expression[size];
        for (int i = 0; i < size; i++) {
            Expression parameterExpression = parameters.get(i).getExpression();
            expressions[i] = parameterExpression;
        }
        return new Expression<Exchange>() {
            public Object evaluate(Exchange exchange) {
                Object[] answer = new Object[size];
                for (int i = 0; i < size; i++) {
                    Object value = expressions[i].evaluate(exchange);
                    // now lets try to coerce the value to the required type
                    value = ExchangeHelper.convertToType(exchange, parameters.get(i).getType(), value);
                    answer[i] = value;
                }
                return answer;
            }

            @Override
            public String toString() {
                return "ParametersExpression: " + Arrays.asList(expressions);
            }
        };
    }
}
