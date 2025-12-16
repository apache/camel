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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeException;
import org.apache.camel.ExchangeProperties;
import org.apache.camel.ExchangeProperty;
import org.apache.camel.Expression;
import org.apache.camel.Handler;
import org.apache.camel.Header;
import org.apache.camel.Headers;
import org.apache.camel.Message;
import org.apache.camel.PropertyInject;
import org.apache.camel.Variable;
import org.apache.camel.Variables;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.support.language.AnnotationExpressionFactory;
import org.apache.camel.support.language.DefaultAnnotationExpressionFactory;
import org.apache.camel.support.language.LanguageAnnotation;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.StringQuoteHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.bean.ParameterMappingStrategyHelper.createParameterMappingStrategy;

/**
 * Represents the metadata about a bean type created via a combination of introspection and annotations together with
 * some useful sensible defaults
 */
public class BeanInfo {
    private static final Logger LOG = LoggerFactory.getLogger(BeanInfo.class);
    private static final String CGLIB_CLASS_SEPARATOR = "$$";
    private static final String CGLIB_METHOD_MARKER = "CGLIB$";
    private static final String BYTE_BUDDY_METHOD_MARKER = "$accessor$";
    private static final String CLIENT_PROXY_SUFFIX = "_ClientProxy";
    private static final String SUBCLASS_SUFFIX = "_Subclass";
    private static final String[] EXCLUDED_METHOD_NAMES = new String[] {
            "equals", "finalize", "getClass", "hashCode", "notify", "notifyAll", "wait", // java.lang.Object
            "getInvocationHandler", "getProxyClass", "isProxyClass", "newProxyInstance" // java.lang.Proxy
    };
    private final CamelContext camelContext;
    private final BeanComponent component;
    private final Class<?> type;
    private final ParameterMappingStrategy strategy;
    private final MethodInfo defaultMethod;
    // shared state with details of operations introspected from the bean, created during the constructor
    private Map<String, List<MethodInfo>> operations = new HashMap<>();
    private List<MethodInfo> operationsWithBody = new ArrayList<>();
    private List<MethodInfo> operationsWithNoBody = new ArrayList<>();
    private List<MethodInfo> operationsWithCustomAnnotation = new ArrayList<>();
    private List<MethodInfo> operationsWithHandlerAnnotation = new ArrayList<>();
    private Map<Method, MethodInfo> methodMap = new HashMap<>();
    private boolean publicConstructors;
    private boolean publicNoArgConstructors;

    public BeanInfo(CamelContext camelContext, Class<?> type) {
        this(camelContext, type, createParameterMappingStrategy(camelContext),
             camelContext.getComponent("bean", BeanComponent.class));
    }

    public BeanInfo(CamelContext camelContext, Method explicitMethod, ParameterMappingStrategy parameterMappingStrategy,
                    BeanComponent beanComponent) {
        this(camelContext, explicitMethod.getDeclaringClass(), null, explicitMethod, parameterMappingStrategy, beanComponent);
    }

    public BeanInfo(CamelContext camelContext, Class<?> type, ParameterMappingStrategy strategy, BeanComponent beanComponent) {
        this(camelContext, type, null, null, strategy, beanComponent);
    }

    public BeanInfo(CamelContext camelContext, Class<?> type, Object instance, Method explicitMethod,
                    ParameterMappingStrategy strategy,
                    BeanComponent beanComponent) {

        this.camelContext = camelContext;
        this.type = type;
        this.strategy = strategy;
        this.component = beanComponent;

        final BeanInfoCacheKey key = new BeanInfoCacheKey(type, instance, explicitMethod);
        final BeanInfoCacheKey key2 = instance != null ? new BeanInfoCacheKey(type, null, explicitMethod) : null;

        // lookup if we have a bean info cache
        BeanInfo beanInfo = component.getBeanInfoFromCache(key);
        if (key2 != null && beanInfo == null) {
            beanInfo = component.getBeanInfoFromCache(key2);
        }
        if (beanInfo != null) {
            // copy the values from the cache we need
            defaultMethod = beanInfo.defaultMethod;
            operations = beanInfo.operations;
            operationsWithBody = beanInfo.operationsWithBody;
            operationsWithNoBody = beanInfo.operationsWithNoBody;
            operationsWithCustomAnnotation = beanInfo.operationsWithCustomAnnotation;
            operationsWithHandlerAnnotation = beanInfo.operationsWithHandlerAnnotation;
            methodMap = beanInfo.methodMap;
            publicConstructors = beanInfo.publicConstructors;
            publicNoArgConstructors = beanInfo.publicNoArgConstructors;
            return;
        }

        if (explicitMethod != null) {
            // must be a valid method
            if (!isValidMethod(type, explicitMethod)) {
                throw new IllegalArgumentException(
                        "The method " + explicitMethod + " is not valid (for example the method must be public)");
            }
            introspect(getType(), explicitMethod);
        } else {
            introspect(getType());
        }

        // if there are only 1 method with 1 operation then select it as a default/fallback method
        MethodInfo method = null;
        if (operations.size() == 1) {
            List<MethodInfo> methods = operations.values().iterator().next();
            if (methods.size() == 1) {
                method = methods.get(0);
            }
        }
        defaultMethod = method;

        // mark the operations lists as unmodifiable, as they should not change during runtime
        // to keep this code thread safe
        operations = Collections.unmodifiableMap(operations);
        operationsWithBody = Collections.unmodifiableList(operationsWithBody);
        operationsWithNoBody = Collections.unmodifiableList(operationsWithNoBody);
        operationsWithCustomAnnotation = Collections.unmodifiableList(operationsWithCustomAnnotation);
        operationsWithHandlerAnnotation = Collections.unmodifiableList(operationsWithHandlerAnnotation);
        methodMap = Collections.unmodifiableMap(methodMap);

        // key must be instance based for custom/handler annotations
        boolean instanceBased = !operationsWithCustomAnnotation.isEmpty() || !operationsWithHandlerAnnotation.isEmpty();
        // do not cache Exchange based beans
        instanceBased &= DefaultExchange.class != type;
        if (instanceBased) {
            // add new bean info to cache (instance based)
            component.addBeanInfoToCache(key, this);
        } else {
            // add new bean info to cache (not instance based, favor key2 if possible)
            BeanInfoCacheKey k = key2 != null ? key2 : key;
            component.addBeanInfoToCache(k, this);
        }
    }

    public Class<?> getType() {
        return type;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public MethodInvocation createInvocation(Object pojo, Exchange exchange) {
        return createInvocation(pojo, exchange, null);
    }

    public MethodInvocation createInvocation(Object pojo, Exchange exchange, String methodName)
            throws AmbiguousMethodCallException, MethodNotFoundException {

        MethodInfo methodInfo = null;

        if (methodName != null) {

            // do not use qualifier for name
            String name = methodName;
            if (methodName.contains("(")) {
                name = StringHelper.before(methodName, "(");
                // the must be a ending parenthesis
                if (!methodName.endsWith(")")) {
                    throw new IllegalArgumentException("Method should end with parenthesis, was " + methodName);
                }
                // and there must be an even number of parenthesis in the syntax
                // (we can use betweenOuterPair as it return null if the syntax is invalid)
                if (StringHelper.betweenOuterPair(methodName, '(', ')') == null) {
                    throw new IllegalArgumentException("Method should have even pair of parenthesis, was " + methodName);
                }
            }
            boolean emptyParameters = methodName.endsWith("()");

            // special for getClass, as we want the user to be able to invoke this method
            // for example to log the class type or the likes
            if ("class".equals(name) || "getClass".equals(name)) {
                methodInfo = createGetClassInvocation(pojo, exchange);
                // special for length on an array type
            } else if ("length".equals(name) && pojo.getClass().isArray()) {
                methodInfo = createLengthInvocation(pojo, exchange);
            } else {
                List<MethodInfo> methods = getOperations(name);
                if (methods != null && methods.size() == 1) {
                    methodInfo = createSingleMethodInvocation(pojo, exchange, methods, emptyParameters, methodName);
                } else if (methods != null) {
                    methodInfo = evalMethods(pojo, exchange, methodName, emptyParameters, name, methods);
                } else {
                    // a specific method was given to invoke but not found
                    throw new MethodNotFoundException(exchange, pojo, methodName);
                }
            }
        }

        if (methodInfo == null && methodMap.size() >= 2) {
            // only try to choose if there is at least 2 methods
            methodInfo = chooseMethod(pojo, exchange, null);
        }
        if (methodInfo == null) {
            methodInfo = defaultMethod;
        }
        if (methodInfo != null) {
            LOG.trace("Chosen method to invoke: {} on bean: {}", methodInfo, pojo);
            return methodInfo.createMethodInvocation(pojo, methodInfo.hasParameters(), exchange);
        }

        LOG.debug("Cannot find suitable method to invoke on bean: {}", pojo);
        return null;
    }

    private MethodInfo evalMethods(
            Object pojo, Exchange exchange, String methodName, boolean emptyParameters, String name, List<MethodInfo> methods) {
        MethodInfo methodInfo;
        // there are more methods with that name so we cannot decide which to use

        // but first let's try to choose a method and see if that complies with the name
        // must use the method name which may have qualifiers
        methodInfo = chooseMethod(pojo, exchange, methodName);

        // validate that if we want an explicit no-arg method, then that's what we get
        if (emptyParameters) {
            if (methodInfo == null || methodInfo.hasParameters()) {
                // we could not find a no-arg method with that name
                throw new MethodNotFoundException(exchange, pojo, methodName, "(with no parameters)");
            }
        }

        if (methodInfo == null || !name.equals(methodInfo.getMethod().getName())) {
            throw new AmbiguousMethodCallException(exchange, methods);
        }
        return methodInfo;
    }

    private static MethodInfo createSingleMethodInvocation(
            Object pojo, Exchange exchange, List<MethodInfo> methods, boolean emptyParameters, String methodName) {
        MethodInfo methodInfo;
        // only one method then choose it
        methodInfo = methods.get(0);

        // validate that if we want an explicit no-arg method, then that's what we get
        if (emptyParameters && methodInfo.hasParameters()) {
            throw new MethodNotFoundException(exchange, pojo, methodName, "(with no parameters)");
        }
        return methodInfo;
    }

    private static MethodInfo createLengthInvocation(Object pojo, Exchange exchange) {
        MethodInfo methodInfo;
        try {
            // need to use arrayLength method from ObjectHelper as Camel's bean OGNL support is method invocation based
            // and not for accessing fields. And hence we need to create a MethodInfo instance with a method to call
            // and therefore use arrayLength from ObjectHelper to return the array length field.
            Method method = org.apache.camel.util.ObjectHelper.class.getMethod("arrayLength", Object[].class);
            ParameterInfo pi = new ParameterInfo(
                    0, Object[].class, false, null, ExpressionBuilder.mandatoryBodyExpression(Object[].class, true));
            List<ParameterInfo> lpi = new ArrayList<>(1);
            lpi.add(pi);
            methodInfo = new MethodInfo(exchange.getContext(), pojo.getClass(), method, lpi, lpi, false, false);
            // Need to update the message body to be pojo for the invocation
            exchange.getIn().setBody(pojo);
        } catch (NoSuchMethodException e) {
            throw new MethodNotFoundException(exchange, pojo, "getClass");
        }
        return methodInfo;
    }

    private static MethodInfo createGetClassInvocation(Object pojo, Exchange exchange) {
        MethodInfo methodInfo;
        try {
            Method method = pojo.getClass().getMethod("getClass");
            methodInfo = new MethodInfo(
                    exchange.getContext(), pojo.getClass(), method, Collections.emptyList(),
                    Collections.emptyList(), false, false);
        } catch (NoSuchMethodException e) {
            throw new MethodNotFoundException(exchange, pojo, "getClass");
        }
        return methodInfo;
    }

    /**
     * Introspects the given class
     *
     * @param clazz the class
     */
    private void introspect(Class<?> clazz) {

        // does the class have any public constructors?
        publicConstructors = clazz.getConstructors().length > 0;
        publicNoArgConstructors = org.apache.camel.util.ObjectHelper.hasDefaultPublicNoArgConstructor(clazz);

        MethodsFilter methods = new MethodsFilter(getType());
        introspect(clazz, methods);

        // now introspect the methods and filter non valid methods
        for (Method method : methods.asReadOnlyList()) {
            boolean valid = isValidMethod(clazz, method);
            LOG.trace("Method: {} is valid: {}", method, valid);
            if (valid) {
                introspect(clazz, method);
            }
        }
    }

    private void introspect(Class<?> clazz, MethodsFilter filteredMethods) {
        // get the target clazz as it could potentially have been enhanced by
        // CGLIB etc.
        clazz = getTargetClass(clazz);
        org.apache.camel.util.ObjectHelper.notNull(clazz, "clazz", this);

        LOG.trace("Introspecting class: {}", clazz);

        for (Method m : clazz.getDeclaredMethods()) {
            filteredMethods.filterMethod(m);
        }

        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null && !superClass.equals(Object.class)) {
            introspect(superClass, filteredMethods);
        }
        for (Class<?> superInterface : clazz.getInterfaces()) {
            introspect(superInterface, filteredMethods);
        }
    }

    /**
     * Introspects the given method
     *
     * @param clazz  the class
     * @param method the method
     */
    private void introspect(Class<?> clazz, Method method) {
        LOG.trace("Introspecting class: {}, method: {}", clazz, method);
        String opName = method.getName();

        MethodInfo methodInfo = createMethodInfo(clazz, method);

        // Foster the use of a potentially already registered most specific override
        MethodInfo existingMethodInfo = findMostSpecificOverride(methodInfo);
        if (existingMethodInfo != null) {
            LOG.trace("This method is already overridden in a subclass, so the method from the sub class is preferred: {}",
                    existingMethodInfo);
            return;
        }

        LOG.trace("Adding operation: {} for method: {}", opName, methodInfo);

        List<MethodInfo> existing = getOperations(opName);
        if (existing != null) {
            // we have an overloaded method so add the method info to the same key
            existing.add(methodInfo);
        } else {
            // its a new method we have not seen before so wrap it in a list and add it
            List<MethodInfo> methods = new ArrayList<>();
            methods.add(methodInfo);
            operations.put(opName, methods);
        }

        if (methodInfo.hasCustomAnnotation()) {
            operationsWithCustomAnnotation.add(methodInfo);
        } else if (methodInfo.hasBodyParameter()) {
            operationsWithBody.add(methodInfo);
        } else {
            operationsWithNoBody.add(methodInfo);
        }

        if (methodInfo.hasHandlerAnnotation()) {
            operationsWithHandlerAnnotation.add(methodInfo);
        }

        // must add to method map last otherwise we break stuff
        methodMap.put(method, methodInfo);

    }

    /**
     * Returns the {@link MethodInfo} for the given method if it exists or null if there is no metadata available for
     * the given method
     */
    public MethodInfo getMethodInfo(Method method) {
        MethodInfo answer = methodMap.get(method);
        if (answer == null) {
            // maybe the method overrides, and the method map keeps info of the source override we can use
            for (Map.Entry<Method, MethodInfo> methodEntry : methodMap.entrySet()) {
                Method source = methodEntry.getKey();
                if (isOverridingMethod(source, method)) {
                    answer = methodEntry.getValue();
                    break;
                }
            }
        }

        if (answer == null) {
            // maybe the method is defined on a base class?
            if (type != Object.class) {
                Class<?> superclass = type.getSuperclass();
                if (superclass != null && superclass != Object.class) {
                    BeanInfo superBeanInfo = new BeanInfo(camelContext, superclass, strategy, component);
                    return superBeanInfo.getMethodInfo(method);
                }
            }
        }
        return answer;
    }

    protected MethodInfo createMethodInfo(Class<?> clazz, Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        List<Annotation>[] parametersAnnotations = collectParameterAnnotations(clazz, method);

        List<ParameterInfo> parameters = new ArrayList<>();
        List<ParameterInfo> bodyParameters = new ArrayList<>();

        boolean hasCustomAnnotation = false;
        boolean hasHandlerAnnotation = org.apache.camel.util.ObjectHelper.hasAnnotation(method.getAnnotations(), Handler.class);

        int size = parameterTypes.length;

        if (LOG.isTraceEnabled()) {
            LOG.trace("Creating MethodInfo for class: {} method: {} having {} parameters", clazz, method, size);
        }

        for (int i = 0; i < size; i++) {
            Class<?> parameterType = parameterTypes[i];
            Annotation[] parameterAnnotations
                    = parametersAnnotations[i].toArray(new Annotation[0]);
            Expression expression = createParameterUnmarshalExpression(method, parameterType, parameterAnnotations);
            if (expression == null) {
                expression = strategy.getDefaultParameterTypeExpression(parameterType);
            }
            // this is not entirely correct as the parameter may be a default parameter type and not a custom annotation
            // but we need to keep this logic for backwards compatability
            hasCustomAnnotation |= expression != null;

            // whether this parameter is vararg which must be last parameter
            boolean varargs = method.isVarArgs() && i == size - 1;

            ParameterInfo parameterInfo = new ParameterInfo(i, parameterType, varargs, parameterAnnotations, expression);
            LOG.trace("Parameter #{}: {}", i, parameterInfo);
            parameters.add(parameterInfo);
            if (expression == null) {
                boolean bodyAnnotation = org.apache.camel.util.ObjectHelper.hasAnnotation(parameterAnnotations, Body.class);
                LOG.trace("Parameter #{} has @Body annotation: {}", i, bodyAnnotation);
                hasCustomAnnotation |= bodyAnnotation;
                if (bodyParameters.isEmpty()) {
                    // okay we have not yet set the body parameter and we have found
                    // the candidate now to use as body parameter
                    if (Exchange.class.isAssignableFrom(parameterType)) {
                        // use exchange
                        expression = ExpressionBuilder.exchangeExpression();
                    } else {
                        // assume it's the body and it must be mandatory convertible to the parameter type
                        // but we allow null bodies in case the message really contains a null body
                        expression = ExpressionBuilder.mandatoryBodyExpression(parameterType, true);
                    }
                    LOG.trace("Parameter #{} is the body parameter using expression {}", i, expression);
                    parameterInfo.setExpression(expression);
                    bodyParameters.add(parameterInfo);
                } else {
                    // will ignore the expression for parameter evaluation
                }
            }
            LOG.trace("Parameter #{} has parameter info: {}", i, parameterInfo);
        }

        // now let's add the method to the repository
        return new MethodInfo(
                camelContext, clazz, method, parameters, bodyParameters, hasCustomAnnotation, hasHandlerAnnotation);
    }

    @SuppressWarnings("unchecked")
    protected List<Annotation>[] collectParameterAnnotations(Class<?> c, Method m) {
        List<Annotation>[] annotations = new List[m.getParameterCount()];
        for (int i = 0; i < annotations.length; i++) {
            annotations[i] = new ArrayList<>();
        }
        collectParameterAnnotations(c, m, annotations);
        return annotations;
    }

    protected void collectParameterAnnotations(Class<?> c, Method m, List<Annotation>[] a) {
        // because we are only looking for camel annotations then skip all stuff from JDKs
        if (c.getName().startsWith("java")) {
            return;
        }
        try {
            Annotation[][] pa = c.getDeclaredMethod(m.getName(), m.getParameterTypes()).getParameterAnnotations();
            for (int i = 0; i < pa.length; i++) {
                a[i].addAll(Arrays.asList(pa[i]));
            }
        } catch (NoSuchMethodException e) {
            // ignore no method with signature of m declared on c
        }
        for (Class<?> i : c.getInterfaces()) {
            collectParameterAnnotations(i, m, a);
        }
        if (!c.isInterface() && c.getSuperclass() != null && c.getSuperclass() != Object.class) {
            collectParameterAnnotations(c.getSuperclass(), m, a);
        }
    }

    /**
     * Choose one of the available methods to invoke if we can match the message body to the body parameter
     *
     * @param  pojo                         the bean to invoke a method on
     * @param  exchange                     the message exchange
     * @param  name                         an optional name of the method that must match, use <tt>null</tt> to
     *                                      indicate all methods
     * @return                              the method to invoke or null if no definitive method could be matched
     * @throws AmbiguousMethodCallException is thrown if cannot choose method due to ambiguity
     */
    protected MethodInfo chooseMethod(Object pojo, Exchange exchange, String name) throws AmbiguousMethodCallException {
        // @Handler should be select first
        // then any single method that has a custom @annotation
        // or any single method that has a match parameter type that matches the Exchange payload
        // and last then try to select the best among the rest

        // must use defensive copy, to avoid altering the shared lists
        // and we want to remove unwanted operations from these local lists
        List<MethodInfo> localOperationsWithBody = null;
        if (!operationsWithBody.isEmpty()) {
            localOperationsWithBody = new ArrayList<>(operationsWithBody);
        }
        List<MethodInfo> localOperationsWithNoBody = null;
        if (!operationsWithNoBody.isEmpty()) {
            localOperationsWithNoBody = new ArrayList<>(operationsWithNoBody);
        }
        List<MethodInfo> localOperationsWithCustomAnnotation = null;
        if (!operationsWithCustomAnnotation.isEmpty()) {
            localOperationsWithCustomAnnotation = new ArrayList<>(operationsWithCustomAnnotation);
        }
        List<MethodInfo> localOperationsWithHandlerAnnotation = null;
        if (!operationsWithHandlerAnnotation.isEmpty()) {
            localOperationsWithHandlerAnnotation = new ArrayList<>(operationsWithHandlerAnnotation);
        }

        // remove all abstract methods
        if (localOperationsWithBody != null) {
            removeAllAbstractMethods(localOperationsWithBody);
        }
        if (localOperationsWithNoBody != null) {
            removeAllAbstractMethods(localOperationsWithNoBody);
        }
        if (localOperationsWithCustomAnnotation != null) {
            removeAllAbstractMethods(localOperationsWithCustomAnnotation);
        }
        if (localOperationsWithHandlerAnnotation != null) {
            removeAllAbstractMethods(localOperationsWithHandlerAnnotation);
        }

        if (name != null) {
            // filter all lists to only include methods with this name
            if (localOperationsWithHandlerAnnotation != null) {
                removeNonMatchingMethods(localOperationsWithHandlerAnnotation, name);
            }
            if (localOperationsWithCustomAnnotation != null) {
                removeNonMatchingMethods(localOperationsWithCustomAnnotation, name);
            }
            if (localOperationsWithBody != null) {
                removeNonMatchingMethods(localOperationsWithBody, name);
            }
            if (localOperationsWithNoBody != null) {
                removeNonMatchingMethods(localOperationsWithNoBody, name);
            }
        } else {
            // remove all getter/setter as we do not want to consider these methods
            if (localOperationsWithHandlerAnnotation != null) {
                removeAllSetterOrGetterMethods(localOperationsWithHandlerAnnotation);
            }
            if (localOperationsWithCustomAnnotation != null) {
                removeAllSetterOrGetterMethods(localOperationsWithCustomAnnotation);
            }
            if (localOperationsWithBody != null) {
                removeAllSetterOrGetterMethods(localOperationsWithBody);
            }
            if (localOperationsWithNoBody != null) {
                removeAllSetterOrGetterMethods(localOperationsWithNoBody);
            }
        }

        if (localOperationsWithHandlerAnnotation != null && localOperationsWithHandlerAnnotation.size() > 1) {
            // if we have more than 1 @Handler then its ambiguous
            throw new AmbiguousMethodCallException(exchange, localOperationsWithHandlerAnnotation);
        }

        if (localOperationsWithHandlerAnnotation != null && localOperationsWithHandlerAnnotation.size() == 1) {
            // methods with handler should be preferred
            return localOperationsWithHandlerAnnotation.get(0);
        } else if (localOperationsWithCustomAnnotation != null && localOperationsWithCustomAnnotation.size() == 1) {
            // if there is one method with an annotation then use that one
            return localOperationsWithCustomAnnotation.get(0);
        }

        // named method and with no parameters
        boolean noParameters = name != null && name.endsWith("()");
        if (noParameters && localOperationsWithNoBody != null && localOperationsWithNoBody.size() == 1) {
            // if there was a method name configured and it has no parameters, then use the method with no body (eg no parameters)
            return localOperationsWithNoBody.get(0);
        } else if (!noParameters && localOperationsWithBody != null && localOperationsWithBody.size() == 1
                && localOperationsWithCustomAnnotation == null) {
            // if there is one method with body then use that one
            return localOperationsWithBody.get(0);
        }

        if (localOperationsWithBody != null || localOperationsWithCustomAnnotation != null) {
            Collection<MethodInfo> possibleOperations = new ArrayList<>();
            if (localOperationsWithBody != null) {
                possibleOperations.addAll(localOperationsWithBody);
            }
            if (localOperationsWithCustomAnnotation != null) {
                possibleOperations.addAll(localOperationsWithCustomAnnotation);
            }

            if (!possibleOperations.isEmpty()) {
                MethodInfo answer = null;

                if (name != null) {
                    // do we have hardcoded parameters values provided from the method name then use that for matching
                    String parameters = StringHelper.between(name, "(", ")");
                    if (parameters != null) {
                        // special as we have hardcoded parameters, so we need to choose method that matches those parameters the best
                        LOG.trace("Choosing best matching method matching parameters: {}", parameters);
                        answer = chooseMethodWithMatchingParameters(exchange, parameters, possibleOperations);
                    }
                }
                if (answer == null) {
                    // multiple possible operations so find the best suited if possible
                    answer = chooseMethodWithMatchingBody(exchange, possibleOperations, localOperationsWithCustomAnnotation);
                }
                if (answer == null && possibleOperations.size() > 1) {
                    answer = getSingleCovariantMethod(possibleOperations);
                }

                if (answer == null) {
                    throw new AmbiguousMethodCallException(exchange, possibleOperations);
                } else {
                    return answer;
                }
            }
        }

        // not possible to determine
        return null;
    }

    private MethodInfo chooseMethodWithMatchingParameters(
            Exchange exchange, String parameters, Collection<MethodInfo> operationList)
            throws AmbiguousMethodCallException {
        // we have hardcoded parameters so need to match that with the given operations
        int count = 0;
        for (@SuppressWarnings("unused")
        String o : ObjectHelper.createIterable(parameters)) {
            count++;
        }

        List<MethodInfo> operations = new ArrayList<>();
        for (MethodInfo info : operationList) {
            if (info.getParameters().size() == count) {
                operations.add(info);
            }
        }

        if (operations.isEmpty()) {
            return null;
        } else if (operations.size() == 1) {
            return operations.get(0);
        }

        // okay we still got multiple operations, so need to match the best one
        List<MethodInfo> candidates = new ArrayList<>();
        // look for best method without any type conversion
        MethodInfo fallbackCandidate = chooseBestPossibleMethod(exchange, parameters, false, operations, candidates);
        if (fallbackCandidate == null && candidates.isEmpty()) {
            // okay then look again for best method with type conversion
            fallbackCandidate = chooseBestPossibleMethod(exchange, parameters, true, operations, candidates);
        }
        if (candidates.size() > 1) {
            MethodInfo answer = getSingleCovariantMethod(candidates);
            if (answer != null) {
                return answer;
            }
        }
        return candidates.size() == 1 ? candidates.get(0) : fallbackCandidate;
    }

    private MethodInfo chooseBestPossibleMethod(
            Exchange exchange, String parameters, boolean allowConversion,
            List<MethodInfo> operations, List<MethodInfo> candidates) {
        MethodInfo fallbackCandidate = null;

        for (MethodInfo info : operations) {
            Iterator<?> it = ObjectHelper.createIterator(parameters, ",", false);
            int index = 0;
            boolean matches = true;
            while (it.hasNext()) {
                String parameter = (String) it.next();
                if (parameter != null) {
                    // must trim
                    parameter = parameter.trim();
                }

                Class<?> parameterType = BeanHelper.getValidParameterType(parameter);
                Class<?> expectedType = info.getParameters().get(index).getType();

                if (parameterType != null && expectedType != null) {

                    // if its a simple language then we need to evaluate the expression
                    // so we have the result and can find out what type the parameter actually is
                    if (StringHelper.hasStartToken(parameter, "simple")) {
                        LOG.trace(
                                "Evaluating simple expression for parameter #{}: {} to determine the class type of the parameter",
                                index, parameter);
                        Object out = getCamelContext().resolveLanguage("simple").createExpression(parameter).evaluate(exchange,
                                Object.class);
                        if (out != null) {
                            parameterType = out.getClass();
                        }
                    }

                    // skip java.lang.Object type, when we have multiple possible methods we want to avoid it if possible
                    if (Object.class.equals(expectedType)) {
                        fallbackCandidate = info;
                        matches = false;
                        break;
                    }

                    boolean matchingTypes = isParameterMatchingType(parameterType, expectedType);
                    if (!matchingTypes && allowConversion) {
                        matchingTypes
                                = getCamelContext().getTypeConverterRegistry().lookup(expectedType, parameterType) != null;
                    }
                    if (!matchingTypes) {
                        matches = false;
                        break;
                    }
                }

                index++;
            }

            if (matches) {
                candidates.add(info);
            }
        }
        return fallbackCandidate;
    }

    private boolean isParameterMatchingType(Class<?> parameterType, Class<?> expectedType) {
        if (Number.class.equals(parameterType)) {
            // number should match long/int/etc.
            if (Integer.class.isAssignableFrom(expectedType) || Long.class.isAssignableFrom(expectedType)
                    || int.class.isAssignableFrom(expectedType) || long.class.isAssignableFrom(expectedType)) {
                return true;
            }
        }
        if (Boolean.class.equals(parameterType)) {
            // boolean should match both Boolean and boolean
            if (Boolean.class.isAssignableFrom(expectedType) || boolean.class.isAssignableFrom(expectedType)) {
                return true;
            }
        }
        return expectedType.isAssignableFrom(parameterType);
        //        return parameterType.isAssignableFrom(expectedType);
    }

    private MethodInfo getSingleCovariantMethod(Collection<MethodInfo> candidates) {
        // if all the candidates are actually covariant, it doesn't matter which one we call
        MethodInfo firstCandidate = candidates.iterator().next();
        for (MethodInfo candidate : candidates) {
            if (!firstCandidate.isCovariantWith(candidate)) {
                return null;
            }
        }
        return firstCandidate;
    }

    private MethodInfo chooseMethodWithMatchingBody(
            Exchange exchange, Collection<MethodInfo> operationList,
            List<MethodInfo> operationsWithCustomAnnotation)
            throws AmbiguousMethodCallException {
        // see if we can find a method whose body param type matches the message body
        Message in = exchange.getIn();
        Object body = in.getBody();
        if (body != null) {
            Class<?> bodyType = body.getClass();
            if (LOG.isTraceEnabled()) {
                LOG.trace("Matching for method with a single parameter that matches type: {}", bodyType.getCanonicalName());
            }

            List<MethodInfo> possibles = new ArrayList<>();
            List<MethodInfo> possiblesWithException = null;
            for (MethodInfo methodInfo : operationList) {
                // test for MEP pattern matching
                boolean out = exchange.getPattern().isOutCapable();
                if (out && methodInfo.isReturnTypeVoid()) {
                    // skip this method as the MEP is Out so the method must return something
                    continue;
                }

                // try to match the arguments
                if (methodInfo.bodyParameterMatches(bodyType)) {
                    LOG.trace("Found a possible method: {}", methodInfo);
                    if (methodInfo.hasExceptionParameter()) {
                        // methods with accepts exceptions
                        if (possiblesWithException == null) {
                            possiblesWithException = new ArrayList<>();
                        }
                        possiblesWithException.add(methodInfo);
                    } else {
                        // regular methods with no exceptions
                        possibles.add(methodInfo);
                    }
                }
            }

            // find best suited method to use
            return chooseBestPossibleMethodInfo(exchange, operationList, body, possibles, possiblesWithException,
                    operationsWithCustomAnnotation);
        }

        // no match so return null
        return null;
    }

    private MethodInfo chooseBestPossibleMethodInfo(
            Exchange exchange, Collection<MethodInfo> operationList, Object body,
            List<MethodInfo> possibles, List<MethodInfo> possiblesWithException,
            List<MethodInfo> possibleWithCustomAnnotation)
            throws AmbiguousMethodCallException {

        Exception exception = ExpressionBuilder.exchangeExceptionExpression().evaluate(exchange, Exception.class);
        if (exception != null && possiblesWithException != null && possiblesWithException.size() == 1) {
            LOG.trace("Exchange has exception set so we prefer method that also has exception as parameter");
            // prefer the method that accepts exception in case we have an exception also
            return possiblesWithException.get(0);
        } else if (possibles.size() == 1) {
            return possibles.get(0);
        } else if (possibles.isEmpty()) {
            LOG.trace("No possible methods so now trying to convert body to parameter types");

            // let's try converting
            Object newBody = null;
            MethodInfo matched = null;
            int matchCounter = 0;
            for (MethodInfo methodInfo : operationList) {
                if (methodInfo.getBodyParameterType() != null) {
                    if (methodInfo.getBodyParameterType().isInstance(body)) {
                        return methodInfo;
                    }

                    // we should only try to convert, as we are looking for best match
                    Object value = exchange.getContext().getTypeConverter().tryConvertTo(methodInfo.getBodyParameterType(),
                            exchange, body);
                    if (value != null) {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("Converted body from: {} to: {}",
                                    body.getClass().getCanonicalName(), methodInfo.getBodyParameterType().getCanonicalName());
                        }
                        matchCounter++;
                        newBody = value;
                        matched = methodInfo;
                    }
                }
            }
            if (matchCounter > 1) {
                throw new AmbiguousMethodCallException(exchange, Arrays.asList(matched, matched));
            }
            if (matched != null) {
                LOG.trace("Setting converted body: {}", body);
                Message in = exchange.getIn();
                in.setBody(newBody);
                return matched;
            }
        } else {
            // if we only have a single method with custom annotations, let's use that one
            if (possibleWithCustomAnnotation != null && possibleWithCustomAnnotation.size() == 1) {
                MethodInfo answer = possibleWithCustomAnnotation.get(0);
                LOG.trace("There are only one method with annotations so we choose it: {}", answer);
                return answer;
            }
            // try to choose among multiple methods with annotations
            MethodInfo chosen = chooseMethodWithCustomAnnotations(possibles);
            if (chosen != null) {
                return chosen;
            }
            // just make sure the methods aren't all actually the same
            chosen = getSingleCovariantMethod(possibles);
            if (chosen != null) {
                return chosen;
            }
            throw new AmbiguousMethodCallException(exchange, possibles);
        }

        // cannot find a good method to use
        return null;
    }

    /**
     * Validates whether the given method is a valid candidate for Camel Bean Binding.
     *
     * @param  clazz  the class
     * @param  method the method
     * @return        true if valid, false to skip the method
     */
    protected boolean isValidMethod(Class<?> clazz, Method method) {
        // method name must not be in the excluded list
        String name = method.getName();
        for (String s : EXCLUDED_METHOD_NAMES) {
            if (name.equals(s)) {
                return false;
            }
        }

        // special for Object where clone is not allowed to be called directly
        if (Object.class == clazz && "clone".equals(name)) {
            return false;
        }

        // must not be a private method
        boolean privateMethod = Modifier.isPrivate(method.getModifiers());
        if (privateMethod) {
            return false;
        }

        // return type must not be an Exchange and it should not be a bridge method
        if (Exchange.class.isAssignableFrom(method.getReturnType()) || method.isBridge()) {
            return false;
        }

        // must not be a method added by Mockito (CGLIB or Byte Buddy)
        if (name.contains(CGLIB_METHOD_MARKER) || name.contains(BYTE_BUDDY_METHOD_MARKER)) {
            return false;
        }

        return true;
    }

    /**
     * Gets the most specific override of a given method, if any. Ignores overrides from synthetic classes. Indeed,
     * overrides may have already been found while inspecting sub classes. Or the given method could override an
     * interface extra method.
     *
     * @param  proposedMethodInfo the method for which a more specific override is searched
     * @return                    The already registered most specific override if any, otherwise <code>null</code>
     */
    private MethodInfo findMostSpecificOverride(MethodInfo proposedMethodInfo) {
        for (MethodInfo alreadyRegisteredMethodInfo : methodMap.values()) {
            Method alreadyRegisteredMethod = alreadyRegisteredMethodInfo.getMethod();
            Method proposedMethod = proposedMethodInfo.getMethod();

            if (!alreadyRegisteredMethod.getDeclaringClass().isSynthetic()
                    && isOverridingMethod(proposedMethod, alreadyRegisteredMethod)) {
                return alreadyRegisteredMethodInfo;
            } else if (isOverridingMethod(alreadyRegisteredMethod, proposedMethod)) {
                return proposedMethodInfo;
            }
        }

        return null;
    }

    /**
     * Wrapper loosely checking the bean type for overrides
     *
     * @see org.apache.camel.util.ObjectHelper#isOverridingMethod(Class, Method, Method, boolean)
     */
    private boolean isOverridingMethod(Method source, Method target) {
        return org.apache.camel.util.ObjectHelper.isOverridingMethod(getType(), source, target, false);
    }

    private MethodInfo chooseMethodWithCustomAnnotations(Collection<MethodInfo> possibles) {
        // if we have only one method with custom annotations let's choose that
        MethodInfo chosen = null;
        for (MethodInfo possible : possibles) {
            if (possible.hasCustomAnnotation()) {
                if (chosen != null) {
                    chosen = null;
                    break;
                } else {
                    chosen = possible;
                }
            }
        }
        return chosen;
    }

    /**
     * Creates an expression for the given parameter type if the parameter can be mapped automatically or null if the
     * parameter cannot be mapped due to insufficient annotations or not fitting with the default type conventions.
     */
    private Expression createParameterUnmarshalExpression(
            Method method,
            Class<?> parameterType, Annotation[] parameterAnnotation) {

        // look for a parameter annotation that converts into an expression
        for (Annotation annotation : parameterAnnotation) {
            Expression answer = createParameterUnmarshalExpressionForAnnotation(method, parameterType, annotation);
            if (answer != null) {
                return answer;
            }
        }
        return null;
    }

    private Expression createParameterUnmarshalExpressionForAnnotation(
            Method method,
            Class<?> parameterType, Annotation annotation) {
        if (annotation instanceof ExchangeProperty propertyAnnotation) {
            return ExpressionBuilder.exchangePropertyExpression(propertyAnnotation.value());
        } else if (annotation instanceof ExchangeProperties) {
            return ExpressionBuilder.exchangePropertiesExpression();
        } else if (annotation instanceof Header headerAnnotation) {
            return ExpressionBuilder.headerExpression(headerAnnotation.value());
        } else if (annotation instanceof Headers) {
            return ExpressionBuilder.headersExpression();
        } else if (annotation instanceof Variable variableAnnotation) {
            return ExpressionBuilder.variableExpression(variableAnnotation.value());
        } else if (annotation instanceof Variables) {
            return ExpressionBuilder.variablesExpression();
        } else if (annotation instanceof ExchangeException) {
            return ExpressionBuilder.exchangeExceptionExpression(CastUtils.cast(parameterType, Exception.class));
        } else if (annotation instanceof PropertyInject propertyAnnotation) {
            Expression inject = ExpressionBuilder.propertiesComponentExpression(propertyAnnotation.value(),
                    propertyAnnotation.defaultValue());
            return ExpressionBuilder.convertToExpression(inject, parameterType);
        } else {
            LanguageAnnotation languageAnnotation = annotation.annotationType().getAnnotation(LanguageAnnotation.class);
            if (languageAnnotation != null) {
                Class<?> type = languageAnnotation.factory();
                if (type == Object.class) {
                    // use the default factory
                    type = DefaultAnnotationExpressionFactory.class;
                }
                Object object = camelContext.getInjector().newInstance(type);
                if (object instanceof AnnotationExpressionFactory expressionFactory) {
                    return expressionFactory.createExpression(camelContext, annotation, languageAnnotation, parameterType);
                } else {
                    LOG.warn(
                            "Ignoring bad annotation: {} on method: {} which declares a factory {} which does not implement {}",
                            languageAnnotation, method, type.getName(), AnnotationExpressionFactory.class.getName());
                }
            }
        }

        return null;
    }

    private static void removeAllSetterOrGetterMethods(List<MethodInfo> methods) {
        Iterator<MethodInfo> it = methods.iterator();
        while (it.hasNext()) {
            MethodInfo info = it.next();
            if (isGetter(info.getMethod())) {
                // skip getters
                it.remove();
            } else if (isSetter(info.getMethod())) {
                // skip setters
                it.remove();
            }
        }
    }

    private void removeNonMatchingMethods(List<MethodInfo> methods, String name) {
        // method does not match so remove it
        methods.removeIf(info -> !matchMethod(info.getMethod(), name));
    }

    private void removeAllAbstractMethods(List<MethodInfo> methods) {
        Iterator<MethodInfo> it = methods.iterator();
        while (it.hasNext()) {
            MethodInfo info = it.next();
            // if the class is an interface then keep the method
            boolean isFromInterface = Modifier.isInterface(info.getMethod().getDeclaringClass().getModifiers());
            if (!isFromInterface && Modifier.isAbstract(info.getMethod().getModifiers())) {
                // we cannot invoke an abstract method
                it.remove();
            }
        }
    }

    private boolean matchMethod(Method method, String methodName) {
        if (methodName == null) {
            return true;
        }

        if (methodName.contains("(") && !methodName.endsWith(")")) {
            throw new IllegalArgumentException("Name must have both starting and ending parenthesis, was: " + methodName);
        }

        // do not use qualifier for name matching
        String name = methodName;
        if (name.contains("(")) {
            name = StringHelper.before(name, "(");
        }

        // must match name
        if (name != null && !name.equals(method.getName())) {
            return false;
        }

        // is it a method with no parameters
        boolean noParameters = methodName.endsWith("()");
        if (noParameters) {
            return method.getParameterCount() == 0;
        }

        // match qualifier types which is used to select among overloaded methods
        String types = StringHelper.betweenOuterPair(methodName, '(', ')');
        if (org.apache.camel.util.ObjectHelper.isNotEmpty(types)) {
            // we must qualify based on types to match method
            String[] parameters = StringQuoteHelper.splitSafeQuote(types, ',', true, true);
            Class<?>[] parameterTypes = null;
            Iterator<?> it = ObjectHelper.createIterator(parameters);
            for (int i = 0; i < method.getParameterCount(); i++) {
                if (it.hasNext()) {
                    if (parameterTypes == null) {
                        parameterTypes = method.getParameterTypes();
                    }
                    Class<?> parameterType = parameterTypes[i];

                    String qualifyType = (String) it.next();
                    if (org.apache.camel.util.ObjectHelper.isEmpty(qualifyType)) {
                        continue;
                    }
                    // trim the type
                    qualifyType = qualifyType.trim();
                    String value = qualifyType;
                    int pos1 = qualifyType.indexOf(' ');
                    int pos2 = qualifyType.indexOf(".class");
                    if (pos1 != -1 && pos2 != -1 && pos1 > pos2) {
                        // a parameter can include type in the syntax to help with choosing correct method
                        // therefore we need to check if type is provided in syntax (name.class value, name2.class value2, ...)
                        value = qualifyType.substring(pos1);
                        value = value.trim();
                        qualifyType = qualifyType.substring(0, pos1);
                        qualifyType = qualifyType.trim();
                    }

                    if ("*".equals(qualifyType)) {
                        // * is a wildcard so we accept and match that parameter type
                        continue;
                    }

                    // if qualify type indeed is a class, then it must be assignable with the parameter type
                    Boolean assignable = BeanHelper.isAssignableToExpectedType(getCamelContext().getClassResolver(),
                            qualifyType, parameterType);
                    // the method will return null if the qualifyType is not a class
                    if (assignable != null && !assignable) {
                        return false;
                    }

                    if (!qualifyType.endsWith(".class")
                            && !BeanHelper.isValidParameterValue(value)) {
                        // its a parameter value, so continue to next parameter
                        // as we should only check for FQN/type parameters
                        return false;
                    }

                } else {
                    // there method has more parameters than was specified in the method name qualifiers
                    return false;
                }
            }

            // if the method has no more types then we can only regard it as matched
            // if there are no more qualifiers
            if (it.hasNext()) {
                return false;
            }
        }

        // the method matched
        return true;
    }

    private static Class<?> getTargetClass(Class<?> clazz) {
        if (clazz != null
                && (clazz.getName().contains(CGLIB_CLASS_SEPARATOR) || clazz.getName().endsWith(CLIENT_PROXY_SUFFIX)
                        || clazz.getName().endsWith(SUBCLASS_SUFFIX))) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null && !Object.class.equals(superClass)) {
                return superClass;
            }
        }
        return clazz;
    }

    /**
     * Do we have a method with the given name?
     * <p/>
     * Shorthand method names for getters is supported, so you can pass in eg 'name' and Camel will can find the real
     * 'getName' method instead.
     *
     * @param  methodName the method name
     * @return            <tt>true</tt> if we have such a method.
     */
    public boolean hasMethod(String methodName) {
        return getOperations(methodName) != null;
    }

    /**
     * Do we have a static method with the given name.
     * <p/>
     * Shorthand method names for getters is supported, so you can pass in eg 'name' and Camel will can find the real
     * 'getName' method instead.
     *
     * @param  methodName the method name
     * @return            <tt>true</tt> if we have such a static method.
     */
    public boolean hasStaticMethod(String methodName) {
        List<MethodInfo> methods = getOperations(methodName);
        if (methods == null || methods.isEmpty()) {
            return false;
        }
        for (MethodInfo method : methods) {
            if (method.isStaticMethod()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether the bean class has any public constructors.
     */
    public boolean hasPublicConstructors() {
        return publicConstructors;
    }

    /**
     * Returns whether the bean class has any public no-arg constructors.
     */
    public boolean hasPublicNoArgConstructors() {
        return publicNoArgConstructors;
    }

    /**
     * Gets the list of methods sorted by A..Z method name.
     *
     * @return the methods.
     */
    public List<MethodInfo> getMethods() {
        if (operations.isEmpty()) {
            return Collections.emptyList();
        }

        List<MethodInfo> methods = new ArrayList<>();
        for (Collection<MethodInfo> col : operations.values()) {
            methods.addAll(col);
        }

        if (methods.size() > 1) {
            // sort the methods by name A..Z
            methods.sort(Comparator.comparing(o -> o.getMethod().getName()));
        }
        return methods;
    }

    /**
     * Does any of the methods have a Canel @Handler annotation.
     */
    public boolean hasAnyMethodHandlerAnnotation() {
        return !operationsWithHandlerAnnotation.isEmpty();
    }

    /**
     * Get the operation(s) with the given name. We can have multiple when methods is overloaded.
     * <p/>
     * Shorthand method names for getters is supported, so you can pass in eg 'name' and Camel will can find the real
     * 'getName' method instead.
     *
     * @param  methodName the method name
     * @return            the found method, or <tt>null</tt> if not found
     */
    private List<MethodInfo> getOperations(String methodName) {
        // do not use qualifier for name
        if (methodName.contains("(")) {
            methodName = StringHelper.before(methodName, "(");
        }

        List<MethodInfo> answer = operations.get(methodName);
        if (answer != null) {
            return answer;
        }

        // now try all getters to see if any of those matched the methodName
        for (Method method : methodMap.keySet()) {
            if (isGetter(method)) {
                String shorthandMethodName = getGetterShorthandName(method);
                // if the two names matches then see if we can find it using that name
                if (methodName != null && methodName.equals(shorthandMethodName)) {
                    return operations.get(method.getName());
                }
            }
        }

        return null;
    }

    public static boolean isGetter(Method method) {
        String name = method.getName();
        Class<?> type = method.getReturnType();
        int parameterCount = method.getParameterCount();

        // is it a getXXX method
        if (name.startsWith("get") && name.length() >= 4 && Character.isUpperCase(name.charAt(3))) {
            return parameterCount == 0 && !type.equals(Void.TYPE);
        }

        // special for isXXX boolean
        if (name.startsWith("is") && name.length() >= 3 && Character.isUpperCase(name.charAt(2))) {
            return parameterCount == 0 && type.getSimpleName().equalsIgnoreCase("boolean");
        }

        return false;
    }

    public static boolean isSetter(Method method) {
        String name = method.getName();
        Class<?> type = method.getReturnType();
        int parameterCount = method.getParameterCount();

        // is it a setXXX method
        boolean validName = name.startsWith("set") && name.length() >= 4 && Character.isUpperCase(name.charAt(3));
        if (validName && parameterCount == 1) {
            // a setXXX can also be a builder pattern so check for its return type is itself
            return type.equals(Void.TYPE);
        }

        return false;
    }

    public static String getGetterShorthandName(Method method) {
        if (!isGetter(method)) {
            return method.getName();
        }

        String name = method.getName();
        if (name.startsWith("get")) {
            name = name.substring(3);
            name = name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1);
        } else if (name.startsWith("is")) {
            name = name.substring(2);
            name = name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1);
        }

        return name;
    }

}
