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
package org.apache.camel.component.bean;

import org.apache.camel.Body;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Header;
import org.apache.camel.Message;
import org.apache.camel.Property;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.ExpressionBuilder;
import static org.apache.camel.util.ExchangeHelper.convertToType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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
    private MethodInfo defaultMethod;
    private List<MethodInfo> operationsWithBody = new ArrayList<MethodInfo>();

    public BeanInfo(Class type, MethodInvocationStrategy strategy) {
        this.type = type;
        this.strategy = strategy;
        introspect(getType());
        if (operations.size() == 1) {
            Collection<MethodInfo> methodInfos = operations.values();
            for (MethodInfo methodInfo : methodInfos) {
                defaultMethod = methodInfo;
            }
        }
    }

    public Class getType() {
        return type;
    }

    public MethodInvocation createInvocation(Method method, Object pojo, Exchange exchange) throws RuntimeCamelException {
        MethodInfo methodInfo = introspect(type, method);
        if (methodInfo != null) {
            return methodInfo.createMethodInvocation(pojo, exchange);
        }
        return null;
    }

    public MethodInvocation createInvocation(Object pojo, Exchange exchange) throws RuntimeCamelException, AmbiguousMethodCallException {
        MethodInfo methodInfo = null;

        // TODO use some other mechanism?
        String name = exchange.getIn().getHeader(BeanProcessor.METHOD_NAME, String.class);
        if (name != null) {
            methodInfo = operations.get(name);
        }
        if (methodInfo == null) {
            methodInfo = chooseMethod(pojo, exchange);
        }
        if (methodInfo == null) {
            methodInfo = defaultMethod;
        }
        if (methodInfo != null) {
            return methodInfo.createMethodInvocation(pojo, exchange);
        }
        return null;
    }

    protected void introspect(Class clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (isValidMethod(clazz, method)) {
                introspect(clazz, method);
            }
        }
        Class superclass = clazz.getSuperclass();
        if (superclass != null && !superclass.equals(Object.class)) {
            introspect(superclass);
        }
    }

    protected MethodInfo introspect(Class clazz, Method method) {
        Class[] parameterTypes = method.getParameterTypes();
        Annotation[][] parametersAnnotations = method.getParameterAnnotations();
        final Expression[] parameterExpressions = new Expression[parameterTypes.length];

        List<ParameterInfo> parameters = new ArrayList<ParameterInfo>();
        List<ParameterInfo> bodyParameters = new ArrayList<ParameterInfo>();

        for (int i = 0; i < parameterTypes.length; i++) {
            Class parameterType = parameterTypes[i];
            Annotation[] parameterAnnotations = parametersAnnotations[i];
            Expression expression = createParameterUnmarshalExpression(clazz, method,
                    parameterType, parameterAnnotations);
            if (expression == null) {
                if (parameterTypes.length == 1 && bodyParameters.isEmpty()) {
                    // lets assume its the body
                    expression = ExpressionBuilder.bodyExpression(parameterType);
                }
                else {
                    if (log.isDebugEnabled()) {
                        log.debug("No expression available for method: "
                                + method.toString() + " which already has a body so ignoring parameter: " + i + " so ignoring method");
                    }
                    return null;
                }
            }

            ParameterInfo parameterInfo = new ParameterInfo(i, parameterType, parameterAnnotations, expression);
            parameters.add(parameterInfo);
            if (isPossibleBodyParameter(parameterAnnotations)) {
                bodyParameters.add(parameterInfo);
            }
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
        MethodInfo methodInfo = new MethodInfo(clazz, method, parameters, bodyParameters);
        operations.put(opName, methodInfo);
        if (methodInfo.hasBodyParameter()) {
            operationsWithBody.add(methodInfo);
        }
        return methodInfo;
    }

    /**
     * Lets try choose one of the available methods to invoke if we can match
     * the message body to the body parameter
     *
     * @param pojo     the bean to invoke a method on
     * @param exchange the message exchange
     * @return the method to invoke or null if no definitive method could be matched
     */
    protected MethodInfo chooseMethod(Object pojo, Exchange exchange) throws AmbiguousMethodCallException {
        if (operationsWithBody.size() == 1) {
            return operationsWithBody.get(0);
        }
        else if (!operationsWithBody.isEmpty()) {
            // lets see if we can find a method who's body param type matches
            // the message body
            Message in = exchange.getIn();
            Object body = in.getBody();
            if (body != null) {
                Class bodyType = body.getClass();

                List<MethodInfo> possibles = new ArrayList<MethodInfo>();
                for (MethodInfo methodInfo : operationsWithBody) {
                    if (methodInfo.bodyParameterMatches(bodyType)) {
                        possibles.add(methodInfo);
                    }
                }
                if (possibles.size() == 1) {
                    return possibles.get(0);
                }
                else if (possibles.isEmpty()) {
                    // lets try converting
                    Object newBody = null;
                    MethodInfo matched = null;
                    for (MethodInfo methodInfo : operationsWithBody) {
                        Object value = convertToType(exchange, methodInfo.getBodyParameterType(), body);
                        if (value != null) {
                            if (newBody != null) {
                                throw new AmbiguousMethodCallException(exchange, Arrays.asList(matched, methodInfo));
                            }
                            else {
                                newBody = value;
                                matched = methodInfo;
                            }
                        }
                    }
                    if (matched != null) {
                        in.setBody(newBody);
                        return matched;
                    }
                }
                else {
                    throw new AmbiguousMethodCallException(exchange, possibles);
                }
            }
            return null;
        }
        return null;
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


    protected boolean isPossibleBodyParameter(Annotation[] annotations) {
        if (annotations != null) {
            for (Annotation annotation : annotations) {
                if ((annotation instanceof Property) || (annotation instanceof Header)) {
                    return false;
                }
            }
        }
        return true;
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

    protected boolean isValidMethod(Class clazz, Method method) {
        return Modifier.isPublic(method.getModifiers());
    }
}
