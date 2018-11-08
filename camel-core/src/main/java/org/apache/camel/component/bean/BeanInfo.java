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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.camel.AttachmentObjects;
import org.apache.camel.Attachments;
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
import org.apache.camel.OutHeaders;
import org.apache.camel.Properties;
import org.apache.camel.Property;
import org.apache.camel.PropertyInject;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.language.LanguageAnnotation;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
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
    private static final List<Method> EXCLUDED_METHODS = new ArrayList<>();
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
            publicConstructors = beanInfo.publicConstructors;
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
            for (List<MethodInfo> infos : operations.values()) {
                for (MethodInfo info : infos) {
                    if (explicitMethod.equals(info.getMethod())) {
                        return info.createMethodInvocation(pojo, info.hasParameters(), exchange);
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
                    List<ParameterInfo> lpi = new ArrayList<>(1);
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

                    // validate that if we want an explicit no-arg method, then that's what we get
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

                    if (methodInfo == null || (name != null && !name.equals(methodInfo.getMethod().getName()))) {
                        throw new AmbiguousMethodCallException(exchange, methods);
                    }
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

    /**
     * Introspects the given class
     *
     * @param clazz the class
     */
    private void introspect(Class<?> clazz) {

        // does the class have any public constructors?
        publicConstructors = clazz.getConstructors().length > 0;

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
        ObjectHelper.notNull(clazz, "clazz", this);

        LOG.trace("Introspecting class: {}", clazz);

        for (Method m : Arrays.asList(clazz.getDeclaredMethods())) {
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
     * @param clazz the class
     * @param method the method
     * @return the method info, is newer <tt>null</tt>
     */
    private MethodInfo introspect(Class<?> clazz, Method method) {
        LOG.trace("Introspecting class: {}, method: {}", clazz, method);
        String opName = method.getName();

        MethodInfo methodInfo = createMethodInfo(clazz, method);

        // Foster the use of a potentially already registered most specific override
        MethodInfo existingMethodInfo = findMostSpecificOverride(methodInfo);
        if (existingMethodInfo != null) {
            LOG.trace("This method is already overridden in a subclass, so the method from the sub class is preferred: {}", existingMethodInfo);
            return existingMethodInfo;
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
            for (Map.Entry<Method, MethodInfo> methodEntry : methodMap.entrySet()) {
                Method source = methodEntry.getKey();
                if (ObjectHelper.isOverridingMethod(getType(), source, method, false)) {
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

        List<ParameterInfo> parameters = new ArrayList<>();
        List<ParameterInfo> bodyParameters = new ArrayList<>();

        boolean hasCustomAnnotation = false;
        boolean hasHandlerAnnotation = ObjectHelper.hasAnnotation(method.getAnnotations(), Handler.class);

        int size = parameterTypes.length;
        if (LOG.isTraceEnabled()) {
            LOG.trace("Creating MethodInfo for class: {} method: {} having {} parameters", clazz, method, size);
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
        } else if (!noParameters && (localOperationsWithBody != null && localOperationsWithBody.size() == 1 && localOperationsWithCustomAnnotation == null)) {
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

    private MethodInfo chooseMethodWithMatchingParameters(Exchange exchange, String parameters, Collection<MethodInfo> operationList)
        throws AmbiguousMethodCallException {
        // we have hardcoded parameters so need to match that with the given operations
        Iterator<?> it = ObjectHelper.createIterator(parameters);
        int count = 0;
        while (it.hasNext()) {
            it.next();
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
        MethodInfo fallbackCandidate = null;
        for (MethodInfo info : operations) {
            it = ObjectHelper.createIterator(parameters, ",", false);
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
                        LOG.trace("Evaluating simple expression for parameter #{}: {} to determine the class type of the parameter", index, parameter);
                        Object out = getCamelContext().resolveLanguage("simple").createExpression(parameter).evaluate(exchange, Object.class);
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

        if (candidates.size() > 1) {
            MethodInfo answer = getSingleCovariantMethod(candidates);
            if (answer != null) {
                return answer;
            }
        }
        return candidates.size() == 1 ? candidates.get(0) : fallbackCandidate;
    }

    private boolean isParameterMatchingType(Class<?> parameterType, Class<?> expectedType) {
        if (Number.class.equals(parameterType)) {
            // number should match long/int/etc.
            if (Integer.class.isAssignableFrom(expectedType) || Long.class.isAssignableFrom(expectedType)
                    || int.class.isAssignableFrom(expectedType) || long.class.isAssignableFrom(expectedType)) {
                return true;
            }
        }
        return parameterType.isAssignableFrom(expectedType);
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
     * Gets the most specific override of a given method, if any. Indeed,
     * overrides may have already been found while inspecting sub classes. Or
     * the given method could override an interface extra method.
     *
     * @param proposedMethodInfo the method for which a more specific override is
     *            searched
     * @return The already registered most specific override if any, otherwise
     *         <code>null</code>
     */
    private MethodInfo findMostSpecificOverride(MethodInfo proposedMethodInfo) {
        for (MethodInfo alreadyRegisteredMethodInfo : methodMap.values()) {
            Method alreadyRegisteredMethod = alreadyRegisteredMethodInfo.getMethod();
            Method proposedMethod = proposedMethodInfo.getMethod();

            if (ObjectHelper.isOverridingMethod(getType(), proposedMethod, alreadyRegisteredMethod, false)) {
                return alreadyRegisteredMethodInfo;
            } else if (ObjectHelper.isOverridingMethod(getType(), alreadyRegisteredMethod, proposedMethod, false)) {
                return proposedMethodInfo;
            }
        }

        return null;
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
        if (annotation instanceof AttachmentObjects) {
            return ExpressionBuilder.attachmentObjectsExpression();
        } else if (annotation instanceof Attachments) {
            return ExpressionBuilder.attachmentsExpression();
        } else if (annotation instanceof Property) {
            Property propertyAnnotation = (Property)annotation;
            return ExpressionBuilder.exchangePropertyExpression(propertyAnnotation.value());
        } else if (annotation instanceof ExchangeProperty) {
            ExchangeProperty propertyAnnotation = (ExchangeProperty)annotation;
            return ExpressionBuilder.exchangePropertyExpression(propertyAnnotation.value());
        } else if (annotation instanceof Properties) {
            return ExpressionBuilder.exchangePropertiesExpression();
        } else if (annotation instanceof ExchangeProperties) {
            return ExpressionBuilder.exchangePropertiesExpression();
        } else if (annotation instanceof Header) {
            Header headerAnnotation = (Header)annotation;
            return ExpressionBuilder.headerExpression(headerAnnotation.value());
        } else if (annotation instanceof Headers) {
            return ExpressionBuilder.headersExpression();
        } else if (annotation instanceof OutHeaders) {
            return ExpressionBuilder.outHeadersExpression();
        } else if (annotation instanceof ExchangeException) {
            return ExpressionBuilder.exchangeExceptionExpression(CastUtils.cast(parameterType, Exception.class));
        } else if (annotation instanceof PropertyInject) {
            PropertyInject propertyAnnotation = (PropertyInject) annotation;
            Expression inject = ExpressionBuilder.propertiesComponentExpression(propertyAnnotation.value(), null, propertyAnnotation.defaultValue());
            return ExpressionBuilder.convertToExpression(inject, parameterType);
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
        String types = StringHelper.between(methodName, "(", ")");
        if (ObjectHelper.isNotEmpty(types)) {
            // we must qualify based on types to match method
            String[] parameters = StringQuoteHelper.splitSafeQuote(types, ',');
            Class<?>[] parameterTypes = null;
            Iterator<?> it = ObjectHelper.createIterator(parameters);
            for (int i = 0; i < method.getParameterCount(); i++) {
                if (it.hasNext()) {
                    if (parameterTypes == null) {
                        parameterTypes = method.getParameterTypes();
                    }
                    Class<?> parameterType = parameterTypes[i];

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
     * Returns whether the bean class has any public constructors.
     */
    public boolean hasPublicConstructors() {
        return publicConstructors;
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
     * Shorthand method names for getters is supported, so you can pass in eg 'name' and Camel
     * will can find the real 'getName' method instead.
     *
     * @param methodName the method name
     * @return the found method, or <tt>null</tt> if not found
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
            if (IntrospectionSupport.isGetter(method)) {
                String shorthandMethodName = IntrospectionSupport.getGetterShorthandName(method);
                // if the two names matches then see if we can find it using that name
                if (methodName != null && methodName.equals(shorthandMethodName)) {
                    return operations.get(method.getName());
                }
            }
        }

        return null;
    }

}
