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

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.InOnly;
import org.apache.camel.InOut;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Pattern;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.StreamCache;
import org.apache.camel.spi.ErrorHandlerAware;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.StringQuoteHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.asList;
import static org.apache.camel.util.ObjectHelper.asString;

/**
 * Information about a method to be used for invocation.
 */
public class MethodInfo {
    private static final Logger LOG = LoggerFactory.getLogger(MethodInfo.class);

    private CamelContext camelContext;
    private Class<?> type;
    private Method method;
    private final List<ParameterInfo> parameters;
    private final List<ParameterInfo> bodyParameters;
    private final boolean hasCustomAnnotation;
    private final boolean hasHandlerAnnotation;
    private Expression parametersExpression;
    private ExchangePattern pattern = ExchangePattern.InOut;
    private AsyncProcessor recipientList;
    private AsyncProcessor routingSlip;
    private AsyncProcessor dynamicRouter;

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
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        @Override
        public String toString() {
            return "DynamicRouter[invoking: " + method + " on bean: " + pojo + "]";
        }
    }

    public MethodInfo(CamelContext camelContext, Class<?> type, Method method, List<ParameterInfo> parameters,
                      List<ParameterInfo> bodyParameters,
                      boolean hasCustomAnnotation, boolean hasHandlerAnnotation) {
        this.camelContext = camelContext;
        this.type = type;
        this.method = method;
        this.parameters = parameters;
        this.bodyParameters = bodyParameters;
        this.hasCustomAnnotation = hasCustomAnnotation;
        this.hasHandlerAnnotation = hasHandlerAnnotation;
        this.parametersExpression = createParametersExpression();

        Map<Class<?>, Annotation> collectedMethodAnnotation = collectMethodAnnotations(type, method);

        // configure MEP if there was an annotation with @Pattern/@InOnly/@InOut
        ExchangePattern aep = findExchangePatternAnnotation(collectedMethodAnnotation);
        if (aep != null) {
            pattern = aep;
        }

        org.apache.camel.RoutingSlip routingSlipAnnotation
                = (org.apache.camel.RoutingSlip) collectedMethodAnnotation.get(org.apache.camel.RoutingSlip.class);
        if (routingSlipAnnotation != null) {
            routingSlip = PluginHelper.getAnnotationBasedProcessorFactory(camelContext)
                    .createRoutingSlip(camelContext, routingSlipAnnotation);
            // add created routingSlip as a service so we have its lifecycle managed
            try {
                camelContext.addService(routingSlip);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        org.apache.camel.DynamicRouter dynamicRouterAnnotation
                = (org.apache.camel.DynamicRouter) collectedMethodAnnotation.get(org.apache.camel.DynamicRouter.class);
        if (dynamicRouterAnnotation != null) {
            dynamicRouter = PluginHelper.getAnnotationBasedProcessorFactory(camelContext)
                    .createDynamicRouter(camelContext, dynamicRouterAnnotation);
            // add created dynamicRouter as a service so we have its lifecycle managed
            try {
                camelContext.addService(dynamicRouter);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        org.apache.camel.RecipientList recipientListAnnotation
                = (org.apache.camel.RecipientList) collectedMethodAnnotation.get(org.apache.camel.RecipientList.class);
        if (recipientListAnnotation != null) {
            recipientList = PluginHelper.getAnnotationBasedProcessorFactory(camelContext)
                    .createRecipientList(camelContext, recipientListAnnotation);
            // add created recipientList as a service so we have its lifecycle managed
            try {
                camelContext.addService(recipientList);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
    }

    /**
     * Finds the oneway annotation in priority order; look for method level annotations first, then the class level
     * annotations, then super class annotations then interface annotations
     */
    private Map<Class<?>, Annotation> collectMethodAnnotations(Class<?> c, Method method) {
        Map<Class<?>, Annotation> annotations = new HashMap<>();

        Set<Class<?>> search = new LinkedHashSet<>();
        // first look for the top class itself and its super classes
        addTypeAndSuperTypes(c, search);
        // and interfaces for all the classes as last
        Set<Class<?>> searchInterfaces = new LinkedHashSet<>();
        for (Class<?> aClazz : search) {
            Class<?>[] interfaces = aClazz.getInterfaces();
            for (Class<?> anInterface : interfaces) {
                addTypeAndSuperTypes(anInterface, searchInterfaces);
            }
        }
        search.addAll(searchInterfaces);
        collectMethodAnnotations(search.iterator(), method.getName(), annotations);
        return annotations;
    }

    /**
     * Adds the current class and all of its base classes (apart from {@link Object} to the given list
     */
    private static void addTypeAndSuperTypes(Class<?> type, Set<Class<?>> result) {
        for (Class<?> t = type; t != null && t != Object.class; t = t.getSuperclass()) {
            result.add(t);
        }
    }

    private void collectMethodAnnotations(
            Iterator<?> it, String targetMethodName, Map<Class<?>, Annotation> annotations) {
        Class<?>[] paramTypes = method.getParameterTypes();
        boolean aep = false;
        while (it.hasNext()) {
            Class<?> searchType = (Class<?>) it.next();
            Method[] methods = searchType.isInterface() ? searchType.getMethods() : searchType.getDeclaredMethods();
            for (Method method : methods) {
                if (targetMethodName.equals(method.getName()) && Arrays.equals(paramTypes, method.getParameterTypes())) {
                    for (Annotation a : method.getAnnotations()) {
                        // favour existing annotation so only add if not exists
                        Class<?> at = a.annotationType();

                        annotations.putIfAbsent(at, a);

                        aep |= at == Pattern.class || at == InOnly.class || at == InOut.class;
                    }
                }
            }
            // special for @Pattern/@InOut/@InOnly
            if (!aep) {
                // look for this on class level
                for (Annotation a : searchType.getAnnotations()) {
                    // favour existing annotation so only add if not exists
                    Class<?> at = a.annotationType();
                    boolean valid = at == Pattern.class || at == InOnly.class || at == InOut.class;
                    if (valid && !annotations.containsKey(at)) {
                        aep = true;
                        annotations.put(at, a);
                    }
                }
            }
        }
    }

    @Override
    public String toString() {
        return method.toString();
    }

    /**
     * For fine grained error handling for outputs of this EIP. The base error handler is used as base and then cloned
     * for each output processor.
     *
     * This is used internally only by Camel - not for end users.
     */
    public void setErrorHandler(Processor errorHandler) {
        // special for @RecipientList which needs to be injected with error handler it should use
        if (recipientList instanceof ErrorHandlerAware) {
            ((ErrorHandlerAware) recipientList).setErrorHandler(errorHandler);
        }
    }

    private Object[] initializeArguments(boolean hasParameters, Exchange exchange) {
        if (hasParameters) {
            if (parametersExpression != null) {
                parametersExpression.init(camelContext);
                return parametersExpression.evaluate(exchange, Object[].class);
            }
        }

        return null;
    }

    public MethodInvocation createMethodInvocation(final Object pojo, boolean hasParameters, final Exchange exchange) {
        final Object[] arguments = initializeArguments(hasParameters, exchange);

        return new MethodInvocation() {
            public Method getMethod() {
                return method;
            }

            public Object[] getArguments() {
                return arguments;
            }

            public boolean proceed(AsyncCallback callback) {
                try {
                    // reset cached streams so they can be read again
                    MessageHelper.resetStreamCache(exchange.getIn());
                    return doProceed(callback);
                } catch (InvocationTargetException e) {
                    exchange.setException(e.getTargetException());
                    callback.done(true);
                    return true;
                } catch (Exception e) {
                    exchange.setException(e);
                    callback.done(true);
                    return true;
                }
            }

            private boolean doProceed(AsyncCallback callback) throws Exception {
                // dynamic router should be invoked beforehand
                if (dynamicRouter != null) {
                    if (!ServiceHelper.isStarted(dynamicRouter)) {
                        ServiceHelper.startService(dynamicRouter);
                    }
                    // use an expression which invokes the method to be used by dynamic router
                    Expression expression = new DynamicRouterExpression(pojo);
                    expression.init(camelContext);
                    exchange.setProperty(ExchangePropertyKey.EVALUATE_EXPRESSION_RESULT, expression);
                    return dynamicRouter.process(exchange, callback);
                }

                // invoke pojo
                if (LOG.isTraceEnabled()) {
                    LOG.trace(">>>> invoking: {} on bean: {} with arguments: {} for exchange: {}", method, pojo,
                            asString(arguments), exchange);
                }
                Object result = invoke(method, pojo, arguments, exchange);

                // the method may be a closure or chained method returning a callable which should be called
                if (result instanceof Callable) {
                    LOG.trace("Method returned Callback which will be called: {}", result);
                    Object callableResult = ((Callable) result).call();
                    if (callableResult != null) {
                        result = callableResult;
                    } else {
                        // if callable returned null we should not change the body
                        result = Void.TYPE;
                    }
                }

                if (recipientList != null) {
                    // ensure its started
                    if (!ServiceHelper.isStarted(recipientList)) {
                        ServiceHelper.startService(recipientList);
                    }
                    exchange.setProperty(ExchangePropertyKey.EVALUATE_EXPRESSION_RESULT, result);
                    return recipientList.process(exchange, callback);
                }
                if (routingSlip != null) {
                    if (!ServiceHelper.isStarted(routingSlip)) {
                        ServiceHelper.startService(routingSlip);
                    }
                    exchange.setProperty(ExchangePropertyKey.EVALUATE_EXPRESSION_RESULT, result);
                    return routingSlip.process(exchange, callback);
                }

                //If it's Java 8 async result
                if (CompletionStage.class.isAssignableFrom(method.getReturnType())) {
                    CompletionStage<?> completionStage = (CompletionStage<?>) result;

                    completionStage
                            .whenComplete((resultObject, e) -> {
                                if (e != null) {
                                    exchange.setException(e);
                                } else if (resultObject != null) {
                                    fillResult(exchange, resultObject);
                                }
                                callback.done(false);
                            });
                    return false;
                }

                // if the method returns something then set the value returned on the Exchange
                if (result != Void.TYPE && !method.getReturnType().equals(Void.TYPE)) {
                    fillResult(exchange, result);
                }

                // we did not use any of the eips, but just invoked the bean
                // so notify the callback we are done synchronously
                callback.done(true);
                return true;
            }

            public Object getThis() {
                return pojo;
            }

            public AccessibleObject getStaticPart() {
                return method;
            }
        };
    }

    private void fillResult(Exchange exchange, Object result) {
        LOG.trace("Setting bean invocation result : {}", result);

        // the bean component forces OUT if the MEP is OUT capable
        boolean out = exchange.hasOut() || ExchangeHelper.isOutCapable(exchange);
        Message old;
        if (out) {
            old = exchange.getOut();
            // propagate headers
            exchange.getOut().getHeaders().putAll(exchange.getIn().getHeaders());
        } else {
            old = exchange.getIn();
        }

        // create a new message container, so we do not drag specialized message objects along
        // but that is only needed if the old message is a specialized message
        boolean copyNeeded = !(old.getClass().equals(DefaultMessage.class));

        if (copyNeeded) {
            Message msg = new DefaultMessage(exchange.getContext());
            msg.copyFromWithNewBody(old, result);

            // replace message on exchange
            ExchangeHelper.replaceMessage(exchange, msg, false);
        } else {
            // no copy needed so set replace value directly
            old.setBody(result);
        }
    }

    public Class<?> getType() {
        return type;
    }

    public Method getMethod() {
        return method;
    }

    /**
     * Returns the {@link org.apache.camel.ExchangePattern} that should be used when invoking this method. This value
     * defaults to {@link org.apache.camel.ExchangePattern#InOut} unless some {@link org.apache.camel.Pattern}
     * annotation is used to override the message exchange pattern.
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
        return actualType != null && org.apache.camel.util.ObjectHelper.isAssignableFrom(bodyType, actualType);
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

    public boolean hasParameters() {
        return !parameters.isEmpty();
    }

    public boolean isReturnTypeVoid() {
        return method.getReturnType().getName().equals("void");
    }

    public boolean isStaticMethod() {
        return Modifier.isStatic(method.getModifiers());
    }

    /**
     * Returns true if this method is covariant with the specified method (this method may above or below the specified
     * method in the class hierarchy)
     */
    public boolean isCovariantWith(MethodInfo method) {
        return method.getMethod().getName().equals(this.getMethod().getName())
                && (method.getMethod().getReturnType().isAssignableFrom(this.getMethod().getReturnType())
                        || this.getMethod().getReturnType().isAssignableFrom(method.getMethod().getReturnType()))
                && Arrays.deepEquals(method.getMethod().getParameterTypes(), this.getMethod().getParameterTypes());
    }

    protected Object invoke(Method mth, Object pojo, Object[] arguments, Exchange exchange) throws InvocationTargetException {
        try {
            return ObjectHelper.invokeMethodSafe(mth, pojo, arguments);
        } catch (IllegalAccessException e) {
            throw new RuntimeExchangeException(
                    "IllegalAccessException occurred invoking method: " + mth + " using arguments: " + asList(arguments),
                    exchange, e);
        } catch (IllegalArgumentException e) {
            throw new RuntimeExchangeException(
                    "IllegalArgumentException occurred invoking method: " + mth + " using arguments: " + asList(arguments),
                    exchange, e);
        }
    }

    protected Expression[] createParameterExpressions() {
        final int size = parameters.size();
        LOG.trace("Creating parameters expression for {} parameters", size);

        final Expression[] expressions = new Expression[size];
        for (int i = 0; i < size; i++) {
            Expression parameterExpression = parameters.get(i).getExpression();
            expressions[i] = parameterExpression;
            LOG.trace("Parameter #{} has expression: {}", i, parameterExpression);
        }

        return expressions;
    }

    protected Expression createParametersExpression() {
        return new ParameterExpression(createParameterExpressions());
    }

    protected ExchangePattern findExchangePatternAnnotation(Map<Class<?>, Annotation> collectedMethodAnnotation) {
        if (collectedMethodAnnotation.get(InOnly.class) != null) {
            return ExchangePattern.InOnly;
        } else if (collectedMethodAnnotation.get(InOut.class) != null) {
            return ExchangePattern.InOut;
        } else {
            Pattern pattern = (Pattern) collectedMethodAnnotation.get(Pattern.class);
            return pattern != null ? pattern.value() : null;
        }
    }

    protected boolean hasExceptionParameter() {
        for (ParameterInfo parameter : parameters) {
            if (Exception.class.isAssignableFrom(parameter.getType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Expression to evaluate the bean parameter parameters and provide the correct values when the method is invoked.
     */
    private final class ParameterExpression implements Expression {
        private final Expression[] expressions;

        ParameterExpression(Expression[] expressions) {
            this.expressions = expressions;
        }

        @Override
        public void init(CamelContext context) {
            if (expressions != null) {
                for (Expression exp : expressions) {
                    if (exp != null) {
                        exp.init(context);
                    }
                }
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T evaluate(Exchange exchange, Class<T> type) {
            Object body = exchange.getIn().getBody();

            // if there was an explicit method name to invoke, then we should support using
            // any provided parameter values in the method name
            String methodName = exchange.getIn().getHeader(BeanConstants.BEAN_METHOD_NAME, String.class);
            // the parameter values is between the parenthesis
            String methodParameters = StringHelper.betweenOuterPair(methodName, '(', ')');
            // use an iterator to walk the parameter values
            Iterator<?> it = null;
            if (methodParameters != null) {
                // split the parameters safely separated by comma, but beware that we can have
                // quoted parameters which contains comma as well, so do a safe quote split (keep quotes)
                String[] parameters = StringQuoteHelper.splitSafeQuote(methodParameters, ',', true, true);
                it = ObjectHelper.createIterator(parameters, ",", true);
            }

            // remove headers as they should not be propagated
            // we need to do this before the expressions gets evaluated as it may contain
            // a @Bean expression which would by mistake read these headers. So the headers
            // must be removed at this point of time
            if (methodName != null) {
                exchange.getIn().removeHeader(Exchange.BEAN_METHOD_NAME);
            }

            Object[] answer = evaluateParameterExpressions(exchange, body, it);
            return (T) answer;
        }

        /**
         * Evaluates all the parameter expressions
         */
        private Object[] evaluateParameterExpressions(Exchange exchange, Object body, Iterator<?> it) {
            Object[] answer = new Object[expressions.length];
            for (int i = 0; i < expressions.length; i++) {

                if (body instanceof StreamCache) {
                    // need to reset stream cache for each expression as you may access the message body in multiple parameters
                    ((StreamCache) body).reset();
                }

                // grab the parameter value for the given index
                Object parameterValue = it != null && it.hasNext() ? it.next() : null;
                // and the expected parameter type
                Class<?> parameterType = parameters.get(i).getType();
                // the value for the parameter to use
                Object value = null;

                // prefer to use parameter value if given, as they override any bean parameter binding
                // we should skip * as its a type placeholder to indicate any type
                if (parameterValue != null && !parameterValue.equals("*")) {
                    // evaluate the parameter value binding
                    value = evaluateParameterValue(exchange, i, parameterValue, parameterType);
                }
                // use bean parameter binding, if still no value
                Expression expression = expressions[i];
                if (value == null && expression != null) {
                    value = evaluateParameterBinding(exchange, expression, i, parameterType);
                }
                // remember the value to use
                if (value != Void.TYPE) {
                    answer[i] = value;
                }
            }

            return answer;
        }

        /**
         * Evaluate using parameter values where the values can be provided in the method name syntax.
         * <p/>
         * This method returns accordingly:
         * <ul>
         * <li><tt>null</tt> - if not a parameter value</li>
         * <li><tt>Void.TYPE</tt> - if an explicit null, forcing Camel to pass in <tt>null</tt> for that given
         * parameter</li>
         * <li>a non <tt>null</tt> value - if the parameter was a parameter value, and to be used</li>
         * </ul>
         */
        private Object evaluateParameterValue(Exchange exchange, int index, Object parameterValue, Class<?> parameterType) {
            Object answer = null;

            // convert the parameter value to a String
            String exp = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, parameterValue);
            boolean valid;
            if (exp != null) {
                int pos1 = exp.indexOf(' ');
                int pos2 = exp.indexOf(".class");
                if (pos1 != -1 && pos2 != -1 && pos1 > pos2) {
                    exp = exp.substring(pos2 + 7); // clip <space>.class
                }

                // check if its a valid parameter value (no type declared via .class syntax)
                valid = BeanHelper.isValidParameterValue(exp);
                if (!valid) {
                    // it may be a parameter type instead, and if so, then we should return null,
                    // as this method is only for evaluating parameter values
                    Boolean isClass = BeanHelper.isAssignableToExpectedType(exchange.getContext().getClassResolver(), exp,
                            parameterType);
                    // the method will return a non-null value if exp is a class
                    if (isClass != null) {
                        return null;
                    }
                }

                // use simple language to evaluate the expression, as it may use the simple language to refer to message body, headers etc.
                Expression expression = null;
                try {
                    expression = exchange.getContext().resolveLanguage("simple").createExpression(exp);
                    parameterValue = expression.evaluate(exchange, Object.class);
                    // use "null" to indicate the expression returned a null value which is a valid response we need to honor
                    if (parameterValue == null) {
                        parameterValue = "null";
                    }
                } catch (Exception e) {
                    throw new ExpressionEvaluationException(
                            expression, "Cannot create/evaluate simple expression: " + exp
                                        + " to be bound to parameter at index: " + index + " on method: " + getMethod(),
                            exchange, e);
                }

                // special for explicit null parameter values (as end users can explicit indicate they want null as parameter)
                // see method javadoc for details
                if ("null".equals(parameterValue)) {
                    return Void.TYPE;
                }

                // the parameter value may match the expected type, then we use it as-is
                if (parameterType.isAssignableFrom(parameterValue.getClass())) {
                    valid = true;
                } else {
                    // String values from the simple language is always valid
                    if (!valid) {
                        valid = parameterValue instanceof String;
                        if (!valid) {
                            // the parameter value was not already valid, but since the simple language have evaluated the expression
                            // which may change the parameterValue, so we have to check it again to see if it is now valid
                            exp = exchange.getContext().getTypeConverter().tryConvertTo(String.class, parameterValue);
                            // re-validate if the parameter was not valid the first time
                            valid = BeanHelper.isValidParameterValue(exp);
                        }
                    }
                }

                if (valid) {
                    // we need to unquote String parameters, as the enclosing quotes is there to denote a parameter value
                    if (parameterValue instanceof String) {
                        parameterValue = StringHelper.removeLeadingAndEndingQuotes((String) parameterValue);
                    }
                    try {
                        // it is a valid parameter value, so convert it to the expected type of the parameter
                        answer = exchange.getContext().getTypeConverter().mandatoryConvertTo(parameterType, exchange,
                                parameterValue);
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("Parameter #{} evaluated as: {} type: {}", index, answer,
                                    org.apache.camel.util.ObjectHelper.type(answer));
                        }
                    } catch (Exception e) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Cannot convert from type: {} to type: {} for parameter #{}",
                                    org.apache.camel.util.ObjectHelper.type(parameterValue), parameterType, index);
                        }
                        throw new ParameterBindingException(e, method, index, parameterType, parameterValue);
                    }
                }
            }

            return answer;
        }

        /**
         * Evaluate using classic parameter binding using the pre compute expression
         */
        private Object evaluateParameterBinding(Exchange exchange, Expression expression, int index, Class<?> parameterType) {
            Object answer = null;

            // use object first to avoid type conversion so we know if there is a value or not
            Object result = expression.evaluate(exchange, Object.class);
            if (result != null) {
                try {
                    if (parameterType.isInstance(result)) {
                        // optimize if the value is already the same type
                        answer = result;
                    } else {
                        // we got a value now try to convert it to the expected type
                        answer = exchange.getContext().getTypeConverter().mandatoryConvertTo(parameterType, result);
                    }
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Parameter #{} evaluated as: {} type: {}", index, answer,
                                org.apache.camel.util.ObjectHelper.type(answer));
                    }
                } catch (NoTypeConversionAvailableException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Cannot convert from type: {} to type: {} for parameter #{}",
                                org.apache.camel.util.ObjectHelper.type(result), parameterType, index);
                    }
                    throw new ParameterBindingException(e, method, index, parameterType, result);
                }
            } else {
                LOG.trace("Parameter #{} evaluated as null", index);
            }

            return answer;
        }

        @Override
        public String toString() {
            return "ParametersExpression: " + Arrays.asList(expressions);
        }

    }
}
