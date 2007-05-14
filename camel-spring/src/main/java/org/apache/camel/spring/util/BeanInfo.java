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
import org.apache.camel.Body;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Header;
import org.apache.camel.Property;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents the metadata about a bean type created via a combination of
 * introspection and annotations together with some useful sensible defaults
 *
 * @version $Revision: $
 */
public class BeanInfo {
    private static final transient Log log = LogFactory.getLog(BeanInfo.class);

    private Class type;
    private MethodInvocationStrategy strategy;
    private Map<String, MethodInfo> operations = new ConcurrentHashMap<String, MethodInfo>();
    private MethodInfo defaultExpression;

    public BeanInfo(Class type, MethodInvocationStrategy strategy) {
        this.type = type;
        this.strategy = strategy;
    }

    public Class getType() {
        return type;
    }

    public void introspect() {
        introspect(getType());
        if (operations.size() == 1) {
            Collection<MethodInfo> methodInfos = operations.values();
            for (MethodInfo methodInfo : methodInfos) {
                defaultExpression = methodInfo;
            }
        }
    }

    public MethodInvocation createInvocation(Method method, Object pojo, Exchange messageExchange) throws RuntimeCamelException {
        MethodInfo methodInfo = introspect(type, method);
        return methodInfo.createMethodInvocation(pojo, messageExchange);
    }

    public MethodInvocation createInvocation(Object pojo, Exchange messageExchange) throws RuntimeCamelException {
        MethodInfo methodInfo = null;

        // TODO use some other mechanism?
        String name = messageExchange.getIn().getHeader("org.apache.camel.MethodName", String.class);
        if (name != null) {
            methodInfo = operations.get(name);
        }
        if (methodInfo == null) {
            methodInfo = defaultExpression;
        }
        if (methodInfo != null) {
            return methodInfo.createMethodInvocation(pojo, messageExchange);
        }
        return null;
    }

    protected void introspect(Class clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            introspect(clazz, method);
        }
        Class superclass = clazz.getSuperclass();
        if (superclass != null && !superclass.equals(Object.class)) {
            introspect(superclass);
        }
    }

    protected MethodInfo introspect(Class clazz, Method method) {
        Class[] parameterTypes = method.getParameterTypes();
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        final Expression[] parameterExpressions = new Expression[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class parameterType = parameterTypes[i];
            Expression expression = createParameterUnmarshalExpression(clazz, method,
                    parameterType, parameterAnnotations[i]);
            if (expression == null) {
                if (log.isDebugEnabled()) {
                    log.debug("No expression available for method: "
                            + method.toString() + " parameter: " + i + " so ignoring method");
                }
                if (parameterTypes.length == 1) {
                	// lets assume its the body
                	expression = ExpressionBuilder.bodyExpression(parameterType);
                }
                else {
                	return null;
                }
            }
            parameterExpressions[i] = expression;
        }

        // now lets add the method to the repository
        String opName = method.getName();

        /*

        TODO allow an annotation to expose the operation name to use

        if (method.getAnnotation(Operation.class) != null) {
            String name = method.getAnnotation(Operation.class).name();
            if (name != null && name.length() > 0) {
                opName = name;
            }
        }
        */
        Expression parametersExpression = createMethodParametersExpression(parameterExpressions);
        MethodInfo methodInfo = new MethodInfo(clazz, method, parametersExpression);
        operations.put(opName, methodInfo);
        return methodInfo;
    }

    protected Expression createMethodParametersExpression(final Expression[] parameterExpressions) {
        return new Expression<Exchange>() {
            public Object evaluate(Exchange exchange) {
                Object[] answer = new Object[parameterExpressions.length];
                for (int i = 0; i < parameterExpressions.length; i++) {
                    Expression parameterExpression = parameterExpressions[i];
                    answer[i] = parameterExpression.evaluate(exchange);
                }
                return answer;
            }

            @Override
            public String toString() {
                return "parametersExpression" + Arrays.asList(parameterExpressions);
            }
        };
    }

    /**
     * Creates an expression for the given parameter type if the parameter can be mapped
     * automatically or null if the parameter cannot be mapped due to unsufficient
     * annotations or not fitting with the default type conventions.
     */
    protected Expression createParameterUnmarshalExpression(Class clazz, Method method, Class parameterType, Annotation[] parameterAnnotation) {

        // TODO look for a parameter annotation that converts into an expression
        for (Annotation annotation : parameterAnnotation) {
            Expression answer = createParameterUnmarshalExpressionForAnnotation(
                    clazz, method, parameterType, annotation);
            if (answer != null) {
                return answer;
            }
        }
        return strategy.getDefaultParameterTypeExpression(parameterType);
    }

    protected Expression createParameterUnmarshalExpressionForAnnotation(Class clazz, Method method, Class parameterType, Annotation annotation) {
        if (annotation instanceof Property) {
            Property propertyAnnotation = (Property) annotation;
            return ExpressionBuilder.propertyExpression(propertyAnnotation.name());
        }
        else if (annotation instanceof Header) {
            Header headerAnnotation = (Header) annotation;
            return ExpressionBuilder.headerExpression(headerAnnotation.name());
        }
        else if (annotation instanceof Body) {
            Body content = (Body) annotation;
            return ExpressionBuilder.bodyExpression(parameterType);

            // TODO allow annotations to be used to create expressions?
/*
        } else if (annotation instanceof XPath) {
            XPath xpathAnnotation = (XPath) annotation;
            return new JAXPStringXPathExpression(xpathAnnotation.xpath());
        }
*/
        }
        return null;
    }
}
