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
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Pattern;
import org.apache.camel.impl.ExpressionAdapter;
import org.apache.camel.processor.DynamicRouter;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.processor.RoutingSlip;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.asString;

/**
 * Information about a method to be used for invocation.
 *
 * @version 
 */
public class MethodInfo {
    private static final transient Logger LOG = LoggerFactory.getLogger(MethodInfo.class);

    private CamelContext camelContext;
    private Class<?> type;
    private Method method;
    private final List<ParameterInfo> parameters;
    private final List<ParameterInfo> bodyParameters;
    private final boolean hasCustomAnnotation;
    private final boolean hasHandlerAnnotation;
    private Expression parametersExpression;
    private ExchangePattern pattern = ExchangePattern.InOut;
    private RecipientList recipientList;
    private RoutingSlip routingSlip;
    private DynamicRouter dynamicRouter;

    /**
     * Adapter to invoke the method which has been annotated with the @DynamicRouter
     */
    private final class DynamicRouterExpression extends ExpressionAdapter {
        private final Object pojo;

        private DynamicRouterExpression(Object pojo) {
            this.pojo = pojo;
        }

        @Override
        public Object evaluate(Exchange exchange) {
            // evaluate arguments on each invocation as the parameters can have changed/updated since last invocation
            final Object[] arguments = parametersExpression.evaluate(exchange, Object[].class);
            try {
                return invoke(method, pojo, arguments, exchange);
            } catch (Exception e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }

        @Override
        public String toString() {
            return "DynamicRouter[invoking: " + method + " on bean: " + pojo + "]";
        }
    }

    public MethodInfo(CamelContext camelContext, Class<?> type, Method method, List<ParameterInfo> parameters, List<ParameterInfo> bodyParameters,
                      boolean hasCustomAnnotation, boolean hasHandlerAnnotation) {
        this.camelContext = camelContext;
        this.type = type;
        this.method = method;
        this.parameters = parameters;
        this.bodyParameters = bodyParameters;
        this.hasCustomAnnotation = hasCustomAnnotation;
        this.hasHandlerAnnotation = hasHandlerAnnotation;
        this.parametersExpression = createParametersExpression();

        Pattern oneway = findOneWayAnnotation(method);
        if (oneway != null) {
            pattern = oneway.value();
        }
        
        if (method.getAnnotation(org.apache.camel.RoutingSlip.class) != null
                && matchContext(method.getAnnotation(org.apache.camel.RoutingSlip.class).context())) {
            org.apache.camel.RoutingSlip annotation = method.getAnnotation(org.apache.camel.RoutingSlip.class);
            routingSlip = new RoutingSlip(camelContext);
            routingSlip.setDelimiter(annotation.delimiter());
            routingSlip.setIgnoreInvalidEndpoints(annotation.ignoreInvalidEndpoints());
            // add created routingSlip as a service so we have its lifecycle managed
            try {
                camelContext.addService(routingSlip);
            } catch (Exception e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }

        if (method.getAnnotation(org.apache.camel.DynamicRouter.class) != null
                && matchContext(method.getAnnotation(org.apache.camel.DynamicRouter.class).context())) {
            org.apache.camel.DynamicRouter annotation = method.getAnnotation(org.apache.camel.DynamicRouter.class);
            dynamicRouter = new DynamicRouter(camelContext);
            dynamicRouter.setDelimiter(annotation.delimiter());
            dynamicRouter.setIgnoreInvalidEndpoints(annotation.ignoreInvalidEndpoints());
            // add created dynamicRouter as a service so we have its lifecycle managed
            try {
                camelContext.addService(dynamicRouter);
            } catch (Exception e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }

        if (method.getAnnotation(org.apache.camel.RecipientList.class) != null
                && matchContext(method.getAnnotation(org.apache.camel.RecipientList.class).context())) {

            org.apache.camel.RecipientList annotation = method.getAnnotation(org.apache.camel.RecipientList.class);

            recipientList = new RecipientList(camelContext, annotation.delimiter());
            recipientList.setStopOnException(annotation.stopOnException());
            recipientList.setIgnoreInvalidEndpoints(annotation.ignoreInvalidEndpoints());
            recipientList.setParallelProcessing(annotation.parallelProcessing());
            recipientList.setStreaming(annotation.streaming());
            recipientList.setTimeout(annotation.timeout());

            if (ObjectHelper.isNotEmpty(annotation.executorServiceRef())) {
                ExecutorService executor = CamelContextHelper.mandatoryLookup(camelContext, annotation.executorServiceRef(), ExecutorService.class);
                recipientList.setExecutorService(executor);
            }

            if (annotation.parallelProcessing() && recipientList.getExecutorService() == null) {
                // we are running in parallel so we need a thread pool
                ExecutorService executor = camelContext.getExecutorServiceStrategy().newDefaultThreadPool(this, "@RecipientList");
                recipientList.setExecutorService(executor);
            }

            if (ObjectHelper.isNotEmpty(annotation.strategyRef())) {
                AggregationStrategy strategy = CamelContextHelper.mandatoryLookup(camelContext, annotation.strategyRef(), AggregationStrategy.class);
                recipientList.setAggregationStrategy(strategy);
            }

            // add created recipientList as a service so we have its lifecycle managed
            try {
                camelContext.addService(recipientList);
            } catch (Exception e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }
    }

    /**
     * Does the given context match this camel context
     */
    private boolean matchContext(String context) {
        if (ObjectHelper.isNotEmpty(context)) {
            if (!camelContext.getName().equals(context)) {
                return false;
            }
        }
        return true;
    }

    public String toString() {
        return method.toString();
    }

    public MethodInvocation createMethodInvocation(final Object pojo, final Exchange exchange) {
        final Object[] arguments = parametersExpression.evaluate(exchange, Object[].class);
        return new MethodInvocation() {
            public Method getMethod() {
                return method;
            }

            public Object[] getArguments() {
                return arguments;
            }

            public Object proceed(AsyncCallback callback, AtomicBoolean doneSync) throws Exception {
                // dynamic router should be invoked beforehand
                if (dynamicRouter != null) {
                    if (!dynamicRouter.isStarted()) {
                        ServiceHelper.startService(dynamicRouter);
                    }
                    // use a expression which invokes the method to be used by dynamic router
                    Expression expression = new DynamicRouterExpression(pojo);
                    boolean sync = dynamicRouter.doRoutingSlip(exchange, expression, callback);
                    // must remember the done sync returned from the dynamic router
                    doneSync.set(sync);
                    return Void.TYPE;
                }

                // invoke pojo
                if (LOG.isTraceEnabled()) {
                    LOG.trace(">>>> invoking: " + method + " on bean: " + pojo + " with arguments: " + asString(arguments) + " for exchange: " + exchange);
                }
                Object result = invoke(method, pojo, arguments, exchange);

                if (recipientList != null) {
                    // ensure its started
                    if (!recipientList.isStarted()) {
                        ServiceHelper.startService(recipientList);
                    }
                    boolean sync = recipientList.sendToRecipientList(exchange, result, callback);
                    // must remember the done sync returned from the recipient list
                    doneSync.set(sync);
                    // we don't want to return the list of endpoints
                    // return Void to indicate to BeanProcessor that there is no reply
                    return Void.TYPE;
                }
                if (routingSlip != null) {
                    if (!routingSlip.isStarted()) {
                        ServiceHelper.startService(routingSlip);
                    }
                    boolean sync = routingSlip.doRoutingSlip(exchange, result, callback);
                    // must remember the done sync returned from the routing slip
                    doneSync.set(sync);
                    return Void.TYPE;
                }

                return result;
            }

            public Object getThis() {
                return pojo;
            }

            public AccessibleObject getStaticPart() {
                return method;
            }
        };
    }

    public Class<?> getType() {
        return type;
    }

    public Method getMethod() {
        return method;
    }

    /**
     * Returns the {@link org.apache.camel.ExchangePattern} that should be used when invoking this method. This value
     * defaults to {@link org.apache.camel.ExchangePattern#InOut} unless some {@link org.apache.camel.Pattern} annotation is used
     * to override the message exchange pattern.
     *
     * @return the exchange pattern to use for invoking this method.
     */
    public ExchangePattern getPattern() {
        return pattern;
    }

    public Expression getParametersExpression() {
        return parametersExpression;
    }

    public List<ParameterInfo> getBodyParameters() {
        return bodyParameters;
    }

    public Class<?> getBodyParameterType() {
        if (bodyParameters.isEmpty()) {
            return null;
        }
        ParameterInfo parameterInfo = bodyParameters.get(0);
        return parameterInfo.getType();
    }

    public boolean bodyParameterMatches(Class<?> bodyType) {
        Class<?> actualType = getBodyParameterType();
        return actualType != null && ObjectHelper.isAssignableFrom(bodyType, actualType);
    }

    public List<ParameterInfo> getParameters() {
        return parameters;
    }

    public boolean hasBodyParameter() {
        return !bodyParameters.isEmpty();
    }

    public boolean hasCustomAnnotation() {
        return hasCustomAnnotation;
    }

    public boolean hasHandlerAnnotation() {
        return hasHandlerAnnotation;
    }

    public boolean isReturnTypeVoid() {
        return method.getReturnType().getName().equals("void");
    }

    protected Object invoke(Method mth, Object pojo, Object[] arguments, Exchange exchange) throws IllegalAccessException, InvocationTargetException {
        return mth.invoke(pojo, arguments);
    }

    protected Expression createParametersExpression() {
        final int size = parameters.size();
        if (LOG.isTraceEnabled()) {
            LOG.trace("Creating parameters expression for " + size + " parameters");
        }

        final Expression[] expressions = new Expression[size];
        for (int i = 0; i < size; i++) {
            Expression parameterExpression = parameters.get(i).getExpression();
            expressions[i] = parameterExpression;
            if (LOG.isTraceEnabled()) {
                LOG.trace("Parameter #" + i + " has expression: " + parameterExpression);
            }
        }
        return new Expression() {
            @SuppressWarnings("unchecked")
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                Object[] answer = new Object[size];
                Object body = exchange.getIn().getBody();
                boolean multiParameterArray = false;
                if (exchange.getIn().getHeader(Exchange.BEAN_MULTI_PARAMETER_ARRAY) != null) {
                    multiParameterArray = exchange.getIn().getHeader(Exchange.BEAN_MULTI_PARAMETER_ARRAY, Boolean.class);
                }
                for (int i = 0; i < size; i++) {
                    Object value = null;
                    if (multiParameterArray) {
                        value = ((Object[])body)[i];
                    } else {
                        Expression expression = expressions[i];
                        if (expression != null) {
                            // use object first to avoid type conversion so we know if there is a value or not
                            Object result = expression.evaluate(exchange, Object.class);
                            if (result != null) {
                                // we got a value now try to convert it to the expected type
                                try {
                                    value = exchange.getContext().getTypeConverter().mandatoryConvertTo(parameters.get(i).getType(), result);
                                    if (LOG.isTraceEnabled()) {
                                        LOG.trace("Parameter #" + i + " evaluated as: " + value + " type: " + ObjectHelper.type(value));
                                    }
                                } catch (NoTypeConversionAvailableException e) {
                                    throw ObjectHelper.wrapCamelExecutionException(exchange, e);
                                }
                            } else {
                                if (LOG.isTraceEnabled()) {
                                    LOG.trace("Parameter #" + i + " evaluated as null");
                                }
                            }
                        }
                    }
                    // now lets try to coerce the value to the required type
                    answer[i] = value;
                }
                return (T) answer;
            }

            @Override
            public String toString() {
                return "ParametersExpression: " + Arrays.asList(expressions);
            }

        };
    }

    /**
     * Finds the oneway annotation in priority order; look for method level annotations first, then the class level annotations,
     * then super class annotations then interface annotations
     *
     * @param method the method on which to search
     * @return the first matching annotation or none if it is not available
     */
    protected Pattern findOneWayAnnotation(Method method) {
        Pattern answer = getPatternAnnotation(method);
        if (answer == null) {
            Class<?> type = method.getDeclaringClass();

            // lets create the search order of types to scan
            List<Class<?>> typesToSearch = new ArrayList<Class<?>>();
            addTypeAndSuperTypes(type, typesToSearch);
            Class<?>[] interfaces = type.getInterfaces();
            for (Class<?> anInterface : interfaces) {
                addTypeAndSuperTypes(anInterface, typesToSearch);
            }

            // now lets scan for a type which the current declared class overloads
            answer = findOneWayAnnotationOnMethod(typesToSearch, method);
            if (answer == null) {
                answer = findOneWayAnnotation(typesToSearch);
            }
        }
        return answer;
    }

    /**
     * Returns the pattern annotation on the given annotated element; either as a direct annotation or
     * on an annotation which is also annotated
     *
     * @param annotatedElement the element to look for the annotation
     * @return the first matching annotation or null if none could be found
     */
    protected Pattern getPatternAnnotation(AnnotatedElement annotatedElement) {
        return getPatternAnnotation(annotatedElement, 2);
    }

    /**
     * Returns the pattern annotation on the given annotated element; either as a direct annotation or
     * on an annotation which is also annotated
     *
     * @param annotatedElement the element to look for the annotation
     * @param depth the current depth
     * @return the first matching annotation or null if none could be found
     */
    protected Pattern getPatternAnnotation(AnnotatedElement annotatedElement, int depth) {
        Pattern answer = annotatedElement.getAnnotation(Pattern.class);
        int nextDepth = depth - 1;

        if (nextDepth > 0) {
            // lets look at all the annotations to see if any of those are annotated
            Annotation[] annotations = annotatedElement.getAnnotations();
            for (Annotation annotation : annotations) {
                Class<? extends Annotation> annotationType = annotation.annotationType();
                if (annotation instanceof Pattern || annotationType.equals(annotatedElement)) {
                    continue;
                } else {
                    Pattern another = getPatternAnnotation(annotationType, nextDepth);
                    if (pattern != null) {
                        if (answer == null) {
                            answer = another;
                        } else {
                            LOG.warn("Duplicate pattern annotation: " + another + " found on annotation: " + annotation + " which will be ignored");
                        }
                    }
                }
            }
        }
        return answer;
    }

    /**
     * Adds the current class and all of its base classes (apart from {@link Object} to the given list
     */
    protected void addTypeAndSuperTypes(Class<?> type, List<Class<?>> result) {
        for (Class<?> t = type; t != null && t != Object.class; t = t.getSuperclass()) {
            result.add(t);
        }
    }

    /**
     * Finds the first annotation on the base methods defined in the list of classes
     */
    protected Pattern findOneWayAnnotationOnMethod(List<Class<?>> classes, Method method) {
        for (Class<?> type : classes) {
            try {
                Method definedMethod = type.getMethod(method.getName(), method.getParameterTypes());
                Pattern answer = getPatternAnnotation(definedMethod);
                if (answer != null) {
                    return answer;
                }
            } catch (NoSuchMethodException e) {
                // ignore
            }
        }
        return null;
    }


    /**
     * Finds the first annotation on the given list of classes
     */
    protected Pattern findOneWayAnnotation(List<Class<?>> classes) {
        for (Class<?> type : classes) {
            Pattern answer = getPatternAnnotation(type);
            if (answer != null) {
                return answer;
            }
        }
        return null;
    }

    protected boolean hasExceptionParameter() {
        for (ParameterInfo parameter : parameters) {
            if (Exception.class.isAssignableFrom(parameter.getType())) {
                return true;
            }
        }
        return false;
    }

}
