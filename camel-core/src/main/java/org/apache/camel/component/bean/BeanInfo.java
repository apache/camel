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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Header;
import org.apache.camel.Headers;
import org.apache.camel.Message;
import org.apache.camel.OutHeaders;
import org.apache.camel.Properties;
import org.apache.camel.Property;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.language.LanguageAnnotation;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.apache.camel.util.ExchangeHelper.convertToType;

/**
 * Represents the metadata about a bean type created via a combination of
 * introspection and annotations together with some useful sensible defaults
 *
 * @version $Revision$
 */
public class BeanInfo {
    private static final transient Log LOG = LogFactory.getLog(BeanInfo.class);
    private final CamelContext camelContext;
    private Class type;
    private ParameterMappingStrategy strategy;
    private Map<String, MethodInfo> operations = new ConcurrentHashMap<String, MethodInfo>();
    private MethodInfo defaultMethod;
    private List<MethodInfo> operationsWithBody = new ArrayList<MethodInfo>();
    private List<MethodInfo> operationsWithCustomAnnotation = new ArrayList<MethodInfo>();

    public BeanInfo(CamelContext camelContext, Class type) {
        this(camelContext, type, createParameterMappingStrategy(camelContext));
    }

    public BeanInfo(CamelContext camelContext, Class type, ParameterMappingStrategy strategy) {
        this.camelContext = camelContext;
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

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public MethodInvocation createInvocation(Method method, Object pojo, Exchange exchange)
        throws RuntimeCamelException {
        MethodInfo methodInfo = introspect(type, method);
        if (methodInfo != null) {
            return methodInfo.createMethodInvocation(pojo, exchange);
        }
        return null;
    }

    public MethodInvocation createInvocation(Object pojo, Exchange exchange) throws RuntimeCamelException,
        AmbiguousMethodCallException {
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

        List<ParameterInfo> parameters = new ArrayList<ParameterInfo>();
        List<ParameterInfo> bodyParameters = new ArrayList<ParameterInfo>();

        boolean hasCustomAnnotation = false;
        for (int i = 0; i < parameterTypes.length; i++) {
            Class parameterType = parameterTypes[i];
            Annotation[] parameterAnnotations = parametersAnnotations[i];
            Expression expression = createParameterUnmarshalExpression(clazz, method, parameterType,
                                                                       parameterAnnotations);
            hasCustomAnnotation |= expression != null;

            ParameterInfo parameterInfo = new ParameterInfo(i, parameterType, parameterAnnotations,
                                                            expression);
            parameters.add(parameterInfo);

            if (expression == null) {
                hasCustomAnnotation |= ObjectHelper.hasAnnotation(parameterAnnotations, Body.class);
                if (bodyParameters.isEmpty()) {
                    // lets assume its the body
                    if (Exchange.class.isAssignableFrom(parameterType)) {
                        expression = ExpressionBuilder.exchangeExpression();
                    } else {
                        expression = ExpressionBuilder.bodyExpression(parameterType);
                    }
                    parameterInfo.setExpression(expression);
                    bodyParameters.add(parameterInfo);
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("No expression available for method: " + method.toString()
                                  + " which already has a body so ignoring parameter: " + i
                                  + " so ignoring method");
                    }
                    return null;
                }
            }

        }

        // now lets add the method to the repository
        String opName = method.getName();

        // TODO allow an annotation to expose the operation name to use
        /* if (method.getAnnotation(Operation.class) != null) { String name =
         * method.getAnnotation(Operation.class).name(); if (name != null &&
         * name.length() > 0) { opName = name; } }
         */
        MethodInfo methodInfo = new MethodInfo(clazz, method, parameters, bodyParameters, hasCustomAnnotation);
        operations.put(opName, methodInfo);
        if (methodInfo.hasBodyParameter()) {
            operationsWithBody.add(methodInfo);
        }
        if (methodInfo.isHasCustomAnnotation() && !methodInfo.hasBodyParameter()) {
            operationsWithCustomAnnotation.add(methodInfo);
        }
        return methodInfo;
    }

    /**
     * Lets try choose one of the available methods to invoke if we can match
     * the message body to the body parameter
     *
     * @param pojo the bean to invoke a method on
     * @param exchange the message exchange
     * @return the method to invoke or null if no definitive method could be
     *         matched
     */
    protected MethodInfo chooseMethod(Object pojo, Exchange exchange) throws AmbiguousMethodCallException {
        if (operationsWithBody.size() == 1) {
            return operationsWithBody.get(0);
        } else if (!operationsWithBody.isEmpty()) {
            return chooseMethodWithMatchingBody(exchange, operationsWithBody);
        } else if (operationsWithCustomAnnotation.size() == 1) {
            return operationsWithCustomAnnotation.get(0);
        }
        return null;
    }

    protected MethodInfo chooseMethodWithMatchingBody(Exchange exchange, Collection<MethodInfo> operationList) throws AmbiguousMethodCallException {
        // lets see if we can find a method who's body param type matches the message body
        Message in = exchange.getIn();
        Object body = in.getBody();
        if (body != null) {
            Class bodyType = body.getClass();

            List<MethodInfo> possibles = new ArrayList<MethodInfo>();
            for (MethodInfo methodInfo : operationList) {
                // TODO: AOP proxies have additioan methods - consider having a static
                // method exclude list to skip all known AOP proxy methods
                // TODO: This class could use some TRACE logging

                // test for MEP pattern matching
                boolean out = exchange.getPattern().isOutCapable();
                if (out && methodInfo.isReturnTypeVoid()) {
                    // skip this method as the MEP is Out so the method must return someting
                    continue;
                }
                
                // try to match the arguments
                if (methodInfo.bodyParameterMatches(bodyType)) {
                    possibles.add(methodInfo);
                }
            }
            if (possibles.size() == 1) {
                return possibles.get(0);
            } else if (possibles.isEmpty()) {
                // lets try converting
                Object newBody = null;
                MethodInfo matched = null;
                for (MethodInfo methodInfo : operationList) {
                    Object value = convertToType(exchange, methodInfo.getBodyParameterType(), body);
                    if (value != null) {
                        if (newBody != null) {
                            throw new AmbiguousMethodCallException(exchange, Arrays.asList(matched,
                                                                                           methodInfo));
                        } else {
                            newBody = value;
                            matched = methodInfo;
                        }
                    }
                }
                if (matched != null) {
                    in.setBody(newBody);
                    return matched;
                }
            } else {
                // if we only have a single method with custom annotations, lets use that one
                if (operationsWithCustomAnnotation.size() == 1) {
                    return operationsWithCustomAnnotation.get(0);
                }
                return chooseMethodWithCustomAnnotations(exchange, possibles);
            }
        }
        // no match so return null
        return null;
    }

    protected MethodInfo chooseMethodWithCustomAnnotations(Exchange exchange, Collection<MethodInfo> possibles) throws AmbiguousMethodCallException {
        // if we have only one method with custom annotations lets choose that
        MethodInfo chosen = null;
        for (MethodInfo possible : possibles) {
            if (possible.isHasCustomAnnotation()) {
                if (chosen != null) {
                    chosen = null;
                    break;
                } else {
                    chosen = possible;
                }
            }
        }
        if (chosen != null) {
            return chosen;
        }
        throw new AmbiguousMethodCallException(exchange, possibles);
    }

    /**
     * Creates an expression for the given parameter type if the parameter can
     * be mapped automatically or null if the parameter cannot be mapped due to
     * unsufficient annotations or not fitting with the default type
     * conventions.
     */
    protected Expression createParameterUnmarshalExpression(Class clazz, Method method, Class parameterType,
                                                            Annotation[] parameterAnnotation) {

        // TODO look for a parameter annotation that converts into an expression
        for (Annotation annotation : parameterAnnotation) {
            Expression answer = createParameterUnmarshalExpressionForAnnotation(clazz, method, parameterType,
                                                                                annotation);
            if (answer != null) {
                return answer;
            }
        }
        return strategy.getDefaultParameterTypeExpression(parameterType);
    }

    protected boolean isPossibleBodyParameter(Annotation[] annotations) {
        if (annotations != null) {
            for (Annotation annotation : annotations) {
                if ((annotation instanceof Property)
                        || (annotation instanceof Header)
                        || (annotation instanceof Headers)
                        || (annotation instanceof OutHeaders)
                        || (annotation instanceof Properties)) {
                    return false;
                }
                LanguageAnnotation languageAnnotation = annotation.annotationType().getAnnotation(LanguageAnnotation.class);
                if (languageAnnotation != null) {
                    return false;
                }
            }
        }
        return true;
    }

    protected Expression createParameterUnmarshalExpressionForAnnotation(Class clazz, Method method,
                                                                         Class parameterType,
                                                                         Annotation annotation) {
        if (annotation instanceof Property) {
            Property propertyAnnotation = (Property)annotation;
            return ExpressionBuilder.propertyExpression(propertyAnnotation.name());
        } else if (annotation instanceof Properties) {
            return ExpressionBuilder.propertiesExpression();
        } else if (annotation instanceof Header) {
            Header headerAnnotation = (Header)annotation;
            return ExpressionBuilder.headerExpression(headerAnnotation.name());
        } else if (annotation instanceof Headers) {
            return ExpressionBuilder.headersExpression();
        } else if (annotation instanceof OutHeaders) {
            return ExpressionBuilder.outHeadersExpression();
        } else {
            LanguageAnnotation languageAnnotation = annotation.annotationType().getAnnotation(LanguageAnnotation.class);
            if (languageAnnotation != null) {
                Class<?> type = languageAnnotation.factory();
                Object object = camelContext.getInjector().newInstance(type);
                if (object instanceof AnnotationExpressionFactory) {
                    AnnotationExpressionFactory expressionFactory = (AnnotationExpressionFactory) object;
                    return expressionFactory.createExpression(camelContext, annotation, languageAnnotation, parameterType);
                } else {
                    LOG.error("Ignoring bad annotation: " + languageAnnotation + "on method: " + method
                            + " which declares a factory: " + type.getName()
                            + " which does not implement " + AnnotationExpressionFactory.class.getName());
                }
            }
        }

        return null;
    }

    protected boolean isValidMethod(Class clazz, Method method) {
        // must be a public method
        if (!Modifier.isPublic(method.getModifiers())) {
            return false;
        }

        // return type must not be an Exchange
        if (method.getReturnType() != null && Exchange.class.isAssignableFrom(method.getReturnType())) {
            return false;
        }

        return true;
    }

    public static ParameterMappingStrategy createParameterMappingStrategy(CamelContext camelContext) {
        Registry registry = camelContext.getRegistry();
        ParameterMappingStrategy answer = registry.lookup(ParameterMappingStrategy.class.getName(),
                                                          ParameterMappingStrategy.class);
        if (answer == null) {
            answer = new DefaultParameterMappingStrategy();
        }
        return answer;
    }
}
