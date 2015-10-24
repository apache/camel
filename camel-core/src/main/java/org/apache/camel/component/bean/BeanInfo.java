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
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Attachments;
import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeException;
import org.apache.camel.ExchangeProperty;
import org.apache.camel.Expression;
import org.apache.camel.Handler;
import org.apache.camel.Header;
import org.apache.camel.Headers;
import org.apache.camel.Message;
import org.apache.camel.OutHeaders;
import org.apache.camel.Properties;
import org.apache.camel.Property;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.language.LanguageAnnotation;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringQuoteHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the metadata about a bean type created via a combination of
 * introspection and annotations together with some useful sensible defaults
 */
public class BeanInfo {
    private static final Logger LOG = LoggerFactory.getLogger(BeanInfo.class);
    private static final String CGLIB_CLASS_SEPARATOR = "$$";
    private static final List<Method> EXCLUDED_METHODS = new ArrayList<Method>();
    private final CamelContext camelContext;
    private final BeanComponent component;
    private final Class<?> type;
    private final ParameterMappingStrategy strategy;
    private final MethodInfo defaultMethod;
    // shared state with details of operations introspected from the bean, created during the constructor
    private Map<String, List<MethodInfo>> operations = new HashMap<String, List<MethodInfo>>();
    private List<MethodInfo> operationsWithBody = new ArrayList<MethodInfo>();
    private List<MethodInfo> operationsWithNoBody = new ArrayList<MethodInfo>();
    private List<MethodInfo> operationsWithCustomAnnotation = new ArrayList<MethodInfo>();
    private List<MethodInfo> operationsWithHandlerAnnotation = new ArrayList<MethodInfo>();
    private Map<Method, MethodInfo> methodMap = new HashMap<Method, MethodInfo>();

    static {
        // exclude all java.lang.Object methods as we dont want to invoke them
        EXCLUDED_METHODS.addAll(Arrays.asList(Object.class.getMethods()));
        // exclude all java.lang.reflect.Proxy methods as we dont want to invoke them
        EXCLUDED_METHODS.addAll(Arrays.asList(Proxy.class.getMethods()));
        try {
            // but keep toString as this method is okay
            EXCLUDED_METHODS.remove(Object.class.getMethod("toString"));
            EXCLUDED_METHODS.remove(Proxy.class.getMethod("toString"));
        } catch (Throwable e) {
            // ignore
        }
    }

    public BeanInfo(CamelContext camelContext, Class<?> type) {
        this(camelContext, type, createParameterMappingStrategy(camelContext));
    }

    public BeanInfo(CamelContext camelContext, Method explicitMethod) {
        this(camelContext, explicitMethod.getDeclaringClass(), explicitMethod, createParameterMappingStrategy(camelContext));
    }

    public BeanInfo(CamelContext camelContext, Class<?> type, ParameterMappingStrategy strategy) {
        this(camelContext, type, null, strategy);
    }

    public BeanInfo(CamelContext camelContext, Class<?> type, Method explicitMethod, ParameterMappingStrategy strategy) {
        this.camelContext = camelContext;
        this.type = type;
        this.strategy = strategy;
        this.component = camelContext.getComponent("bean", BeanComponent.class);

        final BeanInfoCacheKey key = new BeanInfoCacheKey(type, explicitMethod);

        // lookup if we have a bean info cache
        BeanInfo beanInfo = component.getBeanInfoFromCache(key);
        if (beanInfo != null) {
            // copy the values from the cache we need
            defaultMethod = beanInfo.defaultMethod;
            operations = beanInfo.operations;
            operationsWithBody = beanInfo.operationsWithBody;
            operationsWithNoBody = beanInfo.operationsWithNoBody;
            operationsWithCustomAnnotation = beanInfo.operationsWithCustomAnnotation;
            operationsWithHandlerAnnotation = beanInfo.operationsWithHandlerAnnotation;
            methodMap = beanInfo.methodMap;
            return;
        }

        if (explicitMethod != null) {
            // must be a valid method
            if (!isValidMethod(type, explicitMethod)) {
                throw new IllegalArgumentException("The method " + explicitMethod + " is not valid (for example the method must be public)");
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

        // add new bean info to cache
        component.addBeanInfoToCache(key, this);
    }

    public Class<?> getType() {
        return type;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public static ParameterMappingStrategy createParameterMappingStrategy(CamelContext camelContext) {
        // lookup in registry first if there is a user define strategy
        Registry registry = camelContext.getRegistry();
        ParameterMappingStrategy answer = registry.lookupByNameAndType(BeanConstants.BEAN_PARAMETER_MAPPING_STRATEGY, ParameterMappingStrategy.class);
        if (answer == null) {
            // no then use the default one
            answer = new DefaultParameterMappingStrategy();
        }

        return answer;
    }

    public MethodInvocation createInvocation(Object pojo, Exchange exchange)
        throws AmbiguousMethodCallException, MethodNotFoundException {
        return createInvocation(pojo, exchange, null);
    }

    private MethodInvocation createInvocation(Object pojo, Exchange exchange, Method explicitMethod)
        throws AmbiguousMethodCallException, MethodNotFoundException {
        MethodInfo methodInfo = null;
        
        // find the explicit method to invoke
        if (explicitMethod != null) {
            Iterator<List<MethodInfo>> it = operations.values().iterator();
            while (it.hasNext()) {
                List<MethodInfo> infos = it.next();
                for (MethodInfo info : infos) {
                    if (explicitMethod.equals(info.getMethod())) {
                        return info.createMethodInvocation(pojo, exchange);
                    }
                }
            }
            throw new MethodNotFoundException(exchange, pojo, explicitMethod.getName());
        }

        String methodName = exchange.getIn().getHeader(Exchange.BEAN_METHOD_NAME, String.class);
        if (methodName != null) {

            // do not use qualifier for name
            String name = methodName;
            if (methodName.contains("(")) {
                name = ObjectHelper.before(methodName, "(");
            }
            boolean emptyParameters = methodName.endsWith("()");

            // special for getClass, as we want the user to be able to invoke this method
            // for example to log the class type or the likes
            if ("class".equals(name) || "getClass".equals(name)) {
                try {
                    Method method = pojo.getClass().getMethod("getClass");
                    methodInfo = new MethodInfo(exchange.getContext(), pojo.getClass(), method, Collections.<ParameterInfo>emptyList(), Collections.<ParameterInfo>emptyList(), false, false);
                } catch (NoSuchMethodException e) {
                    throw new MethodNotFoundException(exchange, pojo, "getClass");
                }
            // special for length on an array type
            } else if ("length".equals(name) && pojo.getClass().isArray()) {
                try {
                    // need to use arrayLength method from ObjectHelper as Camel's bean OGNL support is method invocation based
                    // and not for accessing fields. And hence we need to create a MethodInfo instance with a method to call
                    // and therefore use arrayLength from ObjectHelper to return the array length field.
                    Method method = ObjectHelper.class.getMethod("arrayLength", Object[].class);
                    ParameterInfo pi = new ParameterInfo(0, Object[].class, null, ExpressionBuilder.mandatoryBodyExpression(Object[].class, true));
                    List<ParameterInfo> lpi = new ArrayList<ParameterInfo>(1);
                    lpi.add(pi);
                    methodInfo = new MethodInfo(exchange.getContext(), pojo.getClass(), method, lpi, lpi, false, false);
                    // Need to update the message body to be pojo for the invocation
                    exchange.getIn().setBody(pojo);
                } catch (NoSuchMethodException e) {
                    throw new MethodNotFoundException(exchange, pojo, "getClass");
                }
            } else {
                List<MethodInfo> methods = getOperations(name);
                if (methods != null && methods.size() == 1) {
                    // only one method then choose it
                    methodInfo = methods.get(0);

                    // validate that if we want an explict no-arg method, then that's what we get
                    if (emptyParameters && methodInfo.hasParameters()) {
                        throw new MethodNotFoundException(exchange, pojo, methodName, "(with no parameters)");
                    }
                } else if (methods != null) {
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
                } else {
                    // a specific method was given to invoke but not found
                    throw new MethodNotFoundException(exchange, pojo, methodName);
                }
            }
        }

        if (methodInfo == null) {
            // no name or type
            methodInfo = chooseMethod(pojo, exchange, null);
        }
        if (methodInfo == null) {
            methodInfo = defaultMethod;
        }
        if (methodInfo != null) {
            LOG.trace("Chosen method to invoke: {} on bean: {}", methodInfo, pojo);
            return methodInfo.createMethodInvocation(pojo, exchange);
        }

        LOG.debug("Cannot find suitable method to invoke on bean: {}", pojo);
        return null;
    }

    /**
     * Introspects the given class
     *
     * @param clazz the class
     */
    private void introspect(Class<?> clazz) {
        // get the target clazz as it could potentially have been enhanced by CGLIB etc.
        clazz = getTargetClass(clazz);
        ObjectHelper.notNull(clazz, "clazz", this);

        LOG.trace("Introspecting class: {}", clazz);

        // favor declared methods, and then filter out duplicate interface methods
        List<Method> methods;
        if (Modifier.isPublic(clazz.getModifiers())) {
            LOG.trace("Preferring class methods as class: {} is public accessible", clazz);
            methods = new ArrayList<Method>(Arrays.asList(clazz.getDeclaredMethods()));
        } else {
            LOG.trace("Preferring interface methods as class: {} is not public accessible", clazz);
            methods = getInterfaceMethods(clazz);
            // and then we must add its declared methods as well
            List<Method> extraMethods = Arrays.asList(clazz.getDeclaredMethods());
            methods.addAll(extraMethods);
        }

        Set<Method> overrides = new HashSet<Method>();

        // do not remove duplicates form class from the Java itself as they have some "duplicates" we need
        boolean javaClass = clazz.getName().startsWith("java.") || clazz.getName().startsWith("javax.");
        if (!javaClass) {
            // it may have duplicate methods already, even from declared or from interfaces + declared
            for (Method source : methods) {

                // skip bridge methods in duplicate checks (as the bridge method is inserted by the compiler due to type erasure)
                if (source.isBridge()) {
                    continue;
                }

                for (Method target : methods) {
                    // skip ourselves
                    if (ObjectHelper.isOverridingMethod(source, target, true)) {
                        continue;
                    }
                    // skip duplicates which may be assign compatible (favor keep first added method when duplicate)
                    if (ObjectHelper.isOverridingMethod(source, target, false)) {
                        overrides.add(target);
                    }
                }
            }
            methods.removeAll(overrides);
            overrides.clear();
        }

        // if we are a public class, then add non duplicate interface classes also
        if (Modifier.isPublic(clazz.getModifiers())) {
            // add additional interface methods
            List<Method> extraMethods = getInterfaceMethods(clazz);
            for (Method target : extraMethods) {
                for (Method source : methods) {
                    if (ObjectHelper.isOverridingMethod(source, target, false)) {
                        overrides.add(target);
                    }
                }
            }
            // remove all the overrides methods
            extraMethods.removeAll(overrides);
            methods.addAll(extraMethods);
        }

        // now introspect the methods and filter non valid methods
        for (Method method : methods) {
            boolean valid = isValidMethod(clazz, method);
            LOG.trace("Method: {} is valid: {}", method, valid);
            if (valid) {
                introspect(clazz, method);
            }
        }

        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && !superclass.equals(Object.class)) {
            introspect(superclass);
        }
    }

    /**
     * Introspects the given method
     *
     * @param clazz the class
     * @param method the method
     * @return the method info, is newer <tt>null</tt>
     */
    private MethodInfo introspect(Class<?> clazz, Method method) {
        LOG.trace("Introspecting class: {}, method: {}", clazz, method);
        String opName = method.getName();

        MethodInfo methodInfo = createMethodInfo(clazz, method);

        // methods already registered should be preferred to use instead of super classes of existing methods
        // we want to us the method from the sub class over super classes, so if we have already registered
        // the method then use it (we are traversing upwards: sub (child) -> super (farther) )
        MethodInfo existingMethodInfo = overridesExistingMethod(methodInfo);
        if (existingMethodInfo != null) {
            LOG.trace("This method is already overridden in a subclass, so the method from the sub class is preferred: {}", existingMethodInfo);
            return existingMethodInfo;
        }

        LOG.trace("Adding operation: {} for method: {}", opName, methodInfo);

        if (hasMethod(opName)) {
            // we have an overloaded method so add the method info to the same key
            List<MethodInfo> existing = getOperations(opName);
            existing.add(methodInfo);
        } else {
            // its a new method we have not seen before so wrap it in a list and add it
            List<MethodInfo> methods = new ArrayList<MethodInfo>();
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

        return methodInfo;
    }


    /**
     * Returns the {@link MethodInfo} for the given method if it exists or null
     * if there is no metadata available for the given method
     */
    public MethodInfo getMethodInfo(Method method) {
        MethodInfo answer = methodMap.get(method);
        if (answer == null) {
            // maybe the method overrides, and the method map keeps info of the source override we can use
            for (Method source : methodMap.keySet()) {
                if (ObjectHelper.isOverridingMethod(source, method, false)) {
                    answer = methodMap.get(source);
                    break;
                }
            }
        }

        if (answer == null) {
            // maybe the method is defined on a base class?
            if (type != Object.class) {
                Class<?> superclass = type.getSuperclass();
                if (superclass != null && superclass != Object.class) {
                    BeanInfo superBeanInfo = new BeanInfo(camelContext, superclass, strategy);
                    return superBeanInfo.getMethodInfo(method);
                }
            }
        }
        return answer;
    }

    protected MethodInfo createMethodInfo(Class<?> clazz, Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        List<Annotation>[] parametersAnnotations = collectParameterAnnotations(clazz, method);

        List<ParameterInfo> parameters = new ArrayList<ParameterInfo>();
        List<ParameterInfo> bodyParameters = new ArrayList<ParameterInfo>();

        boolean hasCustomAnnotation = false;
        boolean hasHandlerAnnotation = ObjectHelper.hasAnnotation(method.getAnnotations(), Handler.class);

        int size = parameterTypes.length;
        if (LOG.isTraceEnabled()) {
            LOG.trace("Creating MethodInfo for class: {} method: {} having {} parameters", new Object[]{clazz, method, size});
        }

        for (int i = 0; i < size; i++) {
            Class<?> parameterType = parameterTypes[i];
            Annotation[] parameterAnnotations = parametersAnnotations[i].toArray(new Annotation[parametersAnnotations[i].size()]);
            Expression expression = createParameterUnmarshalExpression(clazz, method, parameterType, parameterAnnotations);
            hasCustomAnnotation |= expression != null;

            ParameterInfo parameterInfo = new ParameterInfo(i, parameterType, parameterAnnotations, expression);
            LOG.trace("Parameter #{}: {}", i, parameterInfo);
            parameters.add(parameterInfo);
            if (expression == null) {
                boolean bodyAnnotation = ObjectHelper.hasAnnotation(parameterAnnotations, Body.class);
                LOG.trace("Parameter #{} has @Body annotation", i);
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
            LOG.trace("Parameter #{} has parameter info: ", i, parameterInfo);
        }

        // now let's add the method to the repository
        return new MethodInfo(camelContext, clazz, method, parameters, bodyParameters, hasCustomAnnotation, hasHandlerAnnotation);
    }

    protected List<Annotation>[] collectParameterAnnotations(Class<?> c, Method m) {
        @SuppressWarnings("unchecked")
        List<Annotation>[] annotations = new List[m.getParameterTypes().length];
        for (int i = 0; i < annotations.length; i++) {
            annotations[i] = new ArrayList<Annotation>();
        }
        collectParameterAnnotations(c, m, annotations);
        return annotations;
    }

    protected void collectParameterAnnotations(Class<?> c, Method m, List<Annotation>[] a) {
        try {
            Annotation[][] pa = c.getDeclaredMethod(m.getName(), m.getParameterTypes()).getParameterAnnotations();
            for (int i = 0; i < pa.length; i++) {
                a[i].addAll(Arrays.asList(pa[i]));
            }
        } catch (NoSuchMethodException e) {
            // no method with signature of m declared on c
        }
        for (Class<?> i : c.getInterfaces()) {
            collectParameterAnnotations(i, m, a);
        }
        if (!c.isInterface() && c.getSuperclass() != null) {
            collectParameterAnnotations(c.getSuperclass(), m, a);
        }
    }

    /**
     * Choose one of the available methods to invoke if we can match
     * the message body to the body parameter
     *
     * @param pojo the bean to invoke a method on
     * @param exchange the message exchange
     * @param name an optional name of the method that must match, use <tt>null</tt> to indicate all methods
     * @return the method to invoke or null if no definitive method could be matched
     * @throws AmbiguousMethodCallException is thrown if cannot choose method due to ambiguity
     */
    protected MethodInfo chooseMethod(Object pojo, Exchange exchange, String name) throws AmbiguousMethodCallException {
        // @Handler should be select first
        // then any single method that has a custom @annotation
        // or any single method that has a match parameter type that matches the Exchange payload
        // and last then try to select the best among the rest

        // must use defensive copy, to avoid altering the shared lists
        // and we want to remove unwanted operations from these local lists
        final List<MethodInfo> localOperationsWithBody = new ArrayList<MethodInfo>(operationsWithBody);
        final List<MethodInfo> localOperationsWithNoBody = new ArrayList<MethodInfo>(operationsWithNoBody);
        final List<MethodInfo> localOperationsWithCustomAnnotation = new ArrayList<MethodInfo>(operationsWithCustomAnnotation);
        final List<MethodInfo> localOperationsWithHandlerAnnotation = new ArrayList<MethodInfo>(operationsWithHandlerAnnotation);

        // remove all abstract methods
        removeAllAbstractMethods(localOperationsWithBody);
        removeAllAbstractMethods(localOperationsWithNoBody);
        removeAllAbstractMethods(localOperationsWithCustomAnnotation);
        removeAllAbstractMethods(localOperationsWithHandlerAnnotation);

        if (name != null) {
            // filter all lists to only include methods with this name
            removeNonMatchingMethods(localOperationsWithHandlerAnnotation, name);
            removeNonMatchingMethods(localOperationsWithCustomAnnotation, name);
            removeNonMatchingMethods(localOperationsWithBody, name);
            removeNonMatchingMethods(localOperationsWithNoBody, name);
        } else {
            // remove all getter/setter as we do not want to consider these methods
            removeAllSetterOrGetterMethods(localOperationsWithHandlerAnnotation);
            removeAllSetterOrGetterMethods(localOperationsWithCustomAnnotation);
            removeAllSetterOrGetterMethods(localOperationsWithBody);
            removeAllSetterOrGetterMethods(localOperationsWithNoBody);
        }

        if (localOperationsWithHandlerAnnotation.size() > 1) {
            // if we have more than 1 @Handler then its ambiguous
            throw new AmbiguousMethodCallException(exchange, localOperationsWithHandlerAnnotation);
        }

        if (localOperationsWithHandlerAnnotation.size() == 1) {
            // methods with handler should be preferred
            return localOperationsWithHandlerAnnotation.get(0);
        } else if (localOperationsWithCustomAnnotation.size() == 1) {
            // if there is one method with an annotation then use that one
            return localOperationsWithCustomAnnotation.get(0);
        }

        // named method and with no parameters
        boolean noParameters = name != null && name.endsWith("()");
        if (noParameters && localOperationsWithNoBody.size() == 1) {
            // if there was a method name configured and it has no parameters, then use the method with no body (eg no parameters)
            return localOperationsWithNoBody.get(0);
        } else if (!noParameters && localOperationsWithBody.size() == 1 && localOperationsWithCustomAnnotation.isEmpty()) {
            // if there is one method with body then use that one
            return localOperationsWithBody.get(0);
        }

        Collection<MethodInfo> possibleOperations = new ArrayList<MethodInfo>();
        possibleOperations.addAll(localOperationsWithBody);
        possibleOperations.addAll(localOperationsWithCustomAnnotation);

        if (!possibleOperations.isEmpty()) {
            // multiple possible operations so find the best suited if possible
            MethodInfo answer = chooseMethodWithMatchingBody(exchange, possibleOperations, localOperationsWithCustomAnnotation);

            if (answer == null && name != null) {
                // do we have hardcoded parameters values provided from the method name then fallback and try that
                String parameters = ObjectHelper.between(name, "(", ")");
                if (parameters != null) {
                    // special as we have hardcoded parameters, so we need to choose method that matches those parameters the best
                    answer = chooseMethodWithMatchingParameters(exchange, parameters, possibleOperations);
                }
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

        // not possible to determine
        return null;
    }

    private MethodInfo chooseMethodWithMatchingParameters(Exchange exchange, String parameters, Collection<MethodInfo> operationList)
        throws AmbiguousMethodCallException {
        // we have hardcoded parameters so need to match that with the given operations
        Iterator<?> it = ObjectHelper.createIterator(parameters);
        int count = 0;
        while (it.hasNext()) {
            it.next();
            count++;
        }

        List<MethodInfo> operations = new ArrayList<MethodInfo>();
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
        List<MethodInfo> candidates = new ArrayList<MethodInfo>();
        for (MethodInfo info : operations) {
            it = ObjectHelper.createIterator(parameters);
            int index = 0;
            boolean matches = true;
            while (it.hasNext()) {
                String parameter = (String) it.next();
                Class<?> parameterType = BeanHelper.getValidParameterType(parameter);
                Class<?> expectedType = info.getParameters().get(index).getType();

                if (parameterType != null && expectedType != null) {
                    if (!parameterType.isAssignableFrom(expectedType)) {
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

        if (candidates.size() > 1) {
            MethodInfo answer = getSingleCovariantMethod(candidates);
            if (answer == null) {
                throw new AmbiguousMethodCallException(exchange, candidates);
            }
            return answer;
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
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

    private MethodInfo chooseMethodWithMatchingBody(Exchange exchange, Collection<MethodInfo> operationList,
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

            List<MethodInfo> possibles = new ArrayList<MethodInfo>();
            List<MethodInfo> possiblesWithException = new ArrayList<MethodInfo>();
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
                        possiblesWithException.add(methodInfo);
                    } else {
                        // regular methods with no exceptions
                        possibles.add(methodInfo);
                    }
                }
            }

            // find best suited method to use
            return chooseBestPossibleMethodInfo(exchange, operationList, body, possibles, possiblesWithException, operationsWithCustomAnnotation);
        }

        // no match so return null
        return null;
    }

    private MethodInfo chooseBestPossibleMethodInfo(Exchange exchange, Collection<MethodInfo> operationList, Object body,
                                                    List<MethodInfo> possibles, List<MethodInfo> possiblesWithException,
                                                    List<MethodInfo> possibleWithCustomAnnotation)
        throws AmbiguousMethodCallException {

        Exception exception = ExpressionBuilder.exchangeExceptionExpression().evaluate(exchange, Exception.class);
        if (exception != null && possiblesWithException.size() == 1) {
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
                    Object value = exchange.getContext().getTypeConverter().tryConvertTo(methodInfo.getBodyParameterType(), exchange, body);
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
            if (possibleWithCustomAnnotation.size() == 1) {
                MethodInfo answer = possibleWithCustomAnnotation.get(0);
                LOG.trace("There are only one method with annotations so we choose it: {}", answer);
                return answer;
            }
            // try to choose among multiple methods with annotations
            MethodInfo chosen = chooseMethodWithCustomAnnotations(exchange, possibles);
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
     * @param clazz   the class
     * @param method  the method
     * @return true if valid, false to skip the method
     */
    protected boolean isValidMethod(Class<?> clazz, Method method) {
        // must not be in the excluded list
        for (Method excluded : EXCLUDED_METHODS) {
            if (ObjectHelper.isOverridingMethod(excluded, method)) {
                // the method is overriding an excluded method so its not valid
                return false;
            }
        }

        // must be a public method
        if (!Modifier.isPublic(method.getModifiers())) {
            return false;
        }

        // return type must not be an Exchange and it should not be a bridge method
        if ((method.getReturnType() != null && Exchange.class.isAssignableFrom(method.getReturnType())) || method.isBridge()) {
            return false;
        }

        return true;
    }

    /**
     * Does the given method info override an existing method registered before (from a subclass)
     *
     * @param methodInfo  the method to test
     * @return the already registered method to use, null if not overriding any
     */
    private MethodInfo overridesExistingMethod(MethodInfo methodInfo) {
        for (MethodInfo info : methodMap.values()) {
            Method source = info.getMethod();
            Method target = methodInfo.getMethod();

            boolean override = ObjectHelper.isOverridingMethod(source, target);
            if (override) {
                // same name, same parameters, then its overrides an existing class
                return info;
            }
        }

        return null;
    }

    private MethodInfo chooseMethodWithCustomAnnotations(Exchange exchange, Collection<MethodInfo> possibles)
        throws AmbiguousMethodCallException {
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
     * Creates an expression for the given parameter type if the parameter can
     * be mapped automatically or null if the parameter cannot be mapped due to
     * insufficient annotations or not fitting with the default type
     * conventions.
     */
    private Expression createParameterUnmarshalExpression(Class<?> clazz, Method method, 
            Class<?> parameterType, Annotation[] parameterAnnotation) {

        // look for a parameter annotation that converts into an expression
        for (Annotation annotation : parameterAnnotation) {
            Expression answer = createParameterUnmarshalExpressionForAnnotation(clazz, method, parameterType, annotation);
            if (answer != null) {
                return answer;
            }
        }
        // no annotations then try the default parameter mappings
        return strategy.getDefaultParameterTypeExpression(parameterType);
    }

    private Expression createParameterUnmarshalExpressionForAnnotation(Class<?> clazz, Method method, 
            Class<?> parameterType, Annotation annotation) {
        if (annotation instanceof Attachments) {
            return ExpressionBuilder.attachmentsExpression();
        } else if (annotation instanceof Property) {
            Property propertyAnnotation = (Property)annotation;
            return ExpressionBuilder.exchangePropertyExpression(propertyAnnotation.value());
        } else if (annotation instanceof ExchangeProperty) {
            ExchangeProperty propertyAnnotation = (ExchangeProperty)annotation;
            return ExpressionBuilder.exchangePropertyExpression(propertyAnnotation.value());
        } else if (annotation instanceof Properties) {
            return ExpressionBuilder.propertiesExpression();
        } else if (annotation instanceof Header) {
            Header headerAnnotation = (Header)annotation;
            return ExpressionBuilder.headerExpression(headerAnnotation.value());
        } else if (annotation instanceof Headers) {
            return ExpressionBuilder.headersExpression();
        } else if (annotation instanceof OutHeaders) {
            return ExpressionBuilder.outHeadersExpression();
        } else if (annotation instanceof ExchangeException) {
            return ExpressionBuilder.exchangeExceptionExpression(CastUtils.cast(parameterType, Exception.class));
        } else {
            LanguageAnnotation languageAnnotation = annotation.annotationType().getAnnotation(LanguageAnnotation.class);
            if (languageAnnotation != null) {
                Class<?> type = languageAnnotation.factory();
                Object object = camelContext.getInjector().newInstance(type);
                if (object instanceof AnnotationExpressionFactory) {
                    AnnotationExpressionFactory expressionFactory = (AnnotationExpressionFactory) object;
                    return expressionFactory.createExpression(camelContext, annotation, languageAnnotation, parameterType);
                } else {
                    LOG.warn("Ignoring bad annotation: " + languageAnnotation + "on method: " + method
                            + " which declares a factory: " + type.getName()
                            + " which does not implement " + AnnotationExpressionFactory.class.getName());
                }
            }
        }

        return null;
    }
    
    private static List<Method> getInterfaceMethods(Class<?> clazz) {
        final List<Method> answer = new ArrayList<Method>();

        while (clazz != null && !clazz.equals(Object.class)) {
            for (Class<?> interfaceClazz : clazz.getInterfaces()) {
                for (Method interfaceMethod : interfaceClazz.getDeclaredMethods()) {
                    answer.add(interfaceMethod);
                }
            }
            clazz = clazz.getSuperclass();
        }

        return answer;
    }

    private static void removeAllSetterOrGetterMethods(List<MethodInfo> methods) {
        Iterator<MethodInfo> it = methods.iterator();
        while (it.hasNext()) {
            MethodInfo info = it.next();
            if (IntrospectionSupport.isGetter(info.getMethod())) {
                // skip getters
                it.remove();
            } else if (IntrospectionSupport.isSetter(info.getMethod())) {
                // skip setters
                it.remove();
            }
        }
    }

    private void removeNonMatchingMethods(List<MethodInfo> methods, String name) {
        Iterator<MethodInfo> it = methods.iterator();
        while (it.hasNext()) {
            MethodInfo info = it.next();
            if (!matchMethod(info.getMethod(), name)) {
                // method does not match so remove it
                it.remove();
            }
        }
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
            name = ObjectHelper.before(name, "(");
        }

        // must match name
        if (!name.equals(method.getName())) {
            return false;
        }

        // is it a method with no parameters
        boolean noParameters = methodName.endsWith("()");
        if (noParameters) {
            return method.getParameterTypes().length == 0;
        }

        // match qualifier types which is used to select among overloaded methods
        String types = ObjectHelper.between(methodName, "(", ")");
        if (ObjectHelper.isNotEmpty(types)) {
            // we must qualify based on types to match method
            String[] parameters = StringQuoteHelper.splitSafeQuote(types, ',');
            Iterator<?> it = ObjectHelper.createIterator(parameters);
            for (int i = 0; i < method.getParameterTypes().length; i++) {
                if (it.hasNext()) {
                    Class<?> parameterType = method.getParameterTypes()[i];

                    String qualifyType = (String) it.next();
                    if (ObjectHelper.isEmpty(qualifyType)) {
                        continue;
                    }
                    // trim the type
                    qualifyType = qualifyType.trim();

                    if ("*".equals(qualifyType)) {
                        // * is a wildcard so we accept and match that parameter type
                        continue;
                    }

                    if (BeanHelper.isValidParameterValue(qualifyType)) {
                        // its a parameter value, so continue to next parameter
                        // as we should only check for FQN/type parameters
                        continue;
                    }

                    // if qualify type indeed is a class, then it must be assignable with the parameter type
                    Boolean assignable = BeanHelper.isAssignableToExpectedType(getCamelContext().getClassResolver(), qualifyType, parameterType);
                    // the method will return null if the qualifyType is not a class
                    if (assignable != null && !assignable) {
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
        if (clazz != null && clazz.getName().contains(CGLIB_CLASS_SEPARATOR)) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null && !Object.class.equals(superClass)) {
                return superClass;
            }
        }
        return clazz;
    }

    /**
     * Do we have a method with the given name.
     * <p/>
     * Shorthand method names for getters is supported, so you can pass in eg 'name' and Camel
     * will can find the real 'getName' method instead.
     *
     * @param methodName the method name
     * @return <tt>true</tt> if we have such a method.
     */
    public boolean hasMethod(String methodName) {
        return getOperations(methodName) != null;
    }

    /**
     * Do we have a static method with the given name.
     * <p/>
     * Shorthand method names for getters is supported, so you can pass in eg 'name' and Camel
     * will can find the real 'getName' method instead.
     *
     * @param methodName the method name
     * @return <tt>true</tt> if we have such a static method.
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
     * Gets the list of methods sorted by A..Z method name.
     *
     * @return the methods.
     */
    public List<MethodInfo> getMethods() {
        if (operations.isEmpty()) {
            return Collections.emptyList();
        }

        List<MethodInfo> methods = new ArrayList<MethodInfo>();
        for (Collection<MethodInfo> col : operations.values()) {
            methods.addAll(col);
        }

        // sort the methods by name A..Z
        Collections.sort(methods, new Comparator<MethodInfo>() {
            public int compare(MethodInfo o1, MethodInfo o2) {
                return o1.getMethod().getName().compareTo(o2.getMethod().getName());
            }
        });
        return methods;
    }

    /**
     * Get the operation(s) with the given name. We can have multiple when methods is overloaded.
     * <p/>
     * Shorthand method names for getters is supported, so you can pass in eg 'name' and Camel
     * will can find the real 'getName' method instead.
     *
     * @param methodName the method name
     * @return the found method, or <tt>null</tt> if not found
     */
    private List<MethodInfo> getOperations(String methodName) {
        // do not use qualifier for name
        if (methodName.contains("(")) {
            methodName = ObjectHelper.before(methodName, "(");
        }

        List<MethodInfo> answer = operations.get(methodName);
        if (answer != null) {
            return answer;
        }

        // now try all getters to see if any of those matched the methodName
        for (Method method : methodMap.keySet()) {
            if (IntrospectionSupport.isGetter(method)) {
                String shorthandMethodName = IntrospectionSupport.getGetterShorthandName(method);
                // if the two names matches then see if we can find it using that name
                if (methodName.equals(shorthandMethodName)) {
                    return operations.get(method.getName());
                }
            }
        }

        return null;
    }

}
