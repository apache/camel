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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Attachments;
import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeException;
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
    private static final String CGLIB_CLASS_SEPARATOR = "$$";
    private static final List<Method> EXCLUDED_METHODS = new ArrayList<Method>();
    private final CamelContext camelContext;
    private final Class<?> type;
    private final ParameterMappingStrategy strategy;
    private final Map<String, List<MethodInfo>> operations = new ConcurrentHashMap<String, List<MethodInfo>>();
    private final List<MethodInfo> operationsWithBody = new ArrayList<MethodInfo>();
    private final List<MethodInfo> operationsWithCustomAnnotation = new ArrayList<MethodInfo>();
    private final List<MethodInfo> operationsWithHandlerAnnotation = new ArrayList<MethodInfo>();
    private final Map<Method, MethodInfo> methodMap = new ConcurrentHashMap<Method, MethodInfo>();
    private MethodInfo defaultMethod;
    private BeanInfo superBeanInfo;

    static {
        // exclude all java.lang.Object methods as we dont want to invoke them
        EXCLUDED_METHODS.addAll(Arrays.asList(Object.class.getMethods()));
        // exclude all java.lang.reflect.Proxy methods as we dont want to invoke them
        EXCLUDED_METHODS.addAll(Arrays.asList(Proxy.class.getMethods()));
    }

    public BeanInfo(CamelContext camelContext, Class<?> type) {
        this(camelContext, type, createParameterMappingStrategy(camelContext));
    }

    public BeanInfo(CamelContext camelContext, Class<?> type, ParameterMappingStrategy strategy) {
        this.camelContext = camelContext;
        this.type = type;
        this.strategy = strategy;

        introspect(getType());
        // if there are only 1 method with 1 operation then select it as a default/fallback method
        if (operations.size() == 1) {
            List<MethodInfo> methods = operations.values().iterator().next();
            if (methods.size() == 1) {
                defaultMethod = methods.get(0);
            }
        }
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
        ParameterMappingStrategy answer = registry.lookup(BeanConstants.BEAN_PARAMETER_MAPPING_STRATEGY, ParameterMappingStrategy.class);
        if (answer == null) {
            // no then use the default one
            answer = new DefaultParameterMappingStrategy();
        }

        return answer;
    }

    public MethodInvocation createInvocation(Method method, Object pojo, Exchange exchange) {
        MethodInfo methodInfo = introspect(type, method);
        if (methodInfo != null) {
            return methodInfo.createMethodInvocation(pojo, exchange);
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    public MethodInvocation createInvocation(Object pojo, Exchange exchange) throws AmbiguousMethodCallException, MethodNotFoundException {
        MethodInfo methodInfo = null;

        String name = exchange.getIn().getHeader(Exchange.BEAN_METHOD_NAME, String.class);
        Class type = exchange.getIn().getHeader(Exchange.BEAN_TYPE_NAME, Class.class);
        if (name != null) {
            if (hasMethod(name)) {
                List<MethodInfo> methods = getOperations(name);
                if (methods != null && methods.size() == 1) {
                    // only one method then choose it
                    methodInfo = methods.get(0);
                } else {
                    // there are more methods with that name so we cannot decide which to use

                    // but first lets try to choose a method and see if that comply with the name
                    methodInfo = chooseMethod(pojo, exchange, name, type);
                    if (methodInfo == null || !name.equals(methodInfo.getMethod().getName())) {
                        throw new AmbiguousMethodCallException(exchange, methods);
                    }
                }
            } else {
                // a specific method was given to invoke but not found
                throw new MethodNotFoundException(exchange, pojo, name);
            }
        }
        if (methodInfo == null) {
            methodInfo = chooseMethod(pojo, exchange, name, null);
        }
        if (methodInfo == null) {
            methodInfo = defaultMethod;
        }
        if (methodInfo != null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Chosen method to invoke: " + methodInfo + " on bean: " + pojo);
            }
            return methodInfo.createMethodInvocation(pojo, exchange);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Cannot find suitable method to invoke on bean: " + pojo);
        }
        return null;
    }

    /**
     * Introspects the given class
     *
     * @param clazz the class
     */
    protected void introspect(Class<?> clazz) {
        // get the target clazz as it could potentially have been enhanced by CGLIB etc.
        clazz = getTargetClass(clazz);

        if (LOG.isTraceEnabled()) {
            LOG.trace("Introspecting class: " + clazz);
        }

        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            boolean valid = isValidMethod(clazz, method);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Method:  " + method + " is valid: " + valid);
            }
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
    protected MethodInfo introspect(Class<?> clazz, Method method) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Introspecting class: " + clazz + ", method: " + method);
        }
        String opName = method.getName();

        MethodInfo methodInfo = createMethodInfo(clazz, method);

        // methods already registered should be preferred to use instead of super classes of existing methods
        // we want to us the method from the sub class over super classes, so if we have already registered
        // the method then use it (we are traversing upwards: sub (child) -> super (farther) )
        MethodInfo existingMethodInfo = overridesExistingMethod(methodInfo);
        if (existingMethodInfo != null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("This method is already overridden in a subclass, so the method from the sub class is preferred: " + existingMethodInfo);
            }

            return existingMethodInfo;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Adding operation: " + opName + " for method: " + methodInfo);
        }

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
            // maybe the method is defined on a base class?
            if (superBeanInfo == null && type != Object.class) {
                Class<?> superclass = type.getSuperclass();
                if (superclass != null && superclass != Object.class) {
                    superBeanInfo = new BeanInfo(camelContext, superclass, strategy);
                    return superBeanInfo.getMethodInfo(method);
                }
            }
        }
        return answer;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected MethodInfo createMethodInfo(Class clazz, Method method) {
        Class[] parameterTypes = method.getParameterTypes();
        Annotation[][] parametersAnnotations = method.getParameterAnnotations();

        List<ParameterInfo> parameters = new ArrayList<ParameterInfo>();
        List<ParameterInfo> bodyParameters = new ArrayList<ParameterInfo>();

        boolean hasCustomAnnotation = false;
        boolean hasHandlerAnnotation = ObjectHelper.hasAnnotation(method.getAnnotations(), Handler.class);

        int size = parameterTypes.length;
        if (LOG.isTraceEnabled()) {
            LOG.trace("Creating MethodInfo for class: " + clazz + " method: " + method + " having " + size + " parameters");
        }

        for (int i = 0; i < size; i++) {
            Class parameterType = parameterTypes[i];
            Annotation[] parameterAnnotations = parametersAnnotations[i];
            Expression expression = createParameterUnmarshalExpression(clazz, method, parameterType, parameterAnnotations);
            hasCustomAnnotation |= expression != null;

            ParameterInfo parameterInfo = new ParameterInfo(i, parameterType, parameterAnnotations, expression);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Parameter #" + i + ": " + parameterInfo);
            }
            parameters.add(parameterInfo);
            if (expression == null) {
                boolean bodyAnnotation = ObjectHelper.hasAnnotation(parameterAnnotations, Body.class);
                if (LOG.isTraceEnabled() && bodyAnnotation) {
                    LOG.trace("Parameter #" + i + " has @Body annotation");
                }
                hasCustomAnnotation |= bodyAnnotation;
                if (bodyParameters.isEmpty()) {
                    // okay we have not yet set the body parameter and we have found
                    // the candidate now to use as body parameter
                    if (Exchange.class.isAssignableFrom(parameterType)) {
                        // use exchange
                        expression = ExpressionBuilder.exchangeExpression();
                    } else {
                        // lets assume its the body and it must be mandatory convertable to the parameter type
                        // but we allow null bodies in case the message really contains a null body
                        expression = ExpressionBuilder.mandatoryBodyExpression(parameterType, true);
                    }
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Parameter #" + i + " is the body parameter using expression " + expression);
                    }
                    parameterInfo.setExpression(expression);
                    bodyParameters.add(parameterInfo);
                } else {
                    // will ignore the expression for parameter evaluation
                }
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("Parameter #" + i + " has parameter info: " + parameterInfo);
            }
        }

        // now lets add the method to the repository
        return new MethodInfo(camelContext, clazz, method, parameters, bodyParameters, hasCustomAnnotation, hasHandlerAnnotation);
    }

    /**
     * Lets try choose one of the available methods to invoke if we can match
     * the message body to the body parameter
     *
     * @param pojo the bean to invoke a method on
     * @param exchange the message exchange
     * @param name an optional name of the method that must match, use <tt>null</tt> to indicate all methods
     * @param type an optional type of the method parameter that must match, use <tt>null</tt> to indicate all types
     * @return the method to invoke or null if no definitive method could be matched
     * @throws AmbiguousMethodCallException is thrown if cannot chose method due to ambiguous
     */
    @SuppressWarnings("rawtypes")
    protected MethodInfo chooseMethod(Object pojo, Exchange exchange, String name, Class type) throws AmbiguousMethodCallException {
        // @Handler should be select first
        // then any single method that has a custom @annotation
        // or any single method that has a match parameter type that matches the Exchange payload
        // and last then try to select the best among the rest
        
        if (name != null) {
            // filter all lists to only include methods with this name
            removeNonMatchingMethods(operationsWithHandlerAnnotation, name);
            removeNonMatchingMethods(operationsWithCustomAnnotation, name);
            removeNonMatchingMethods(operationsWithBody, name);
        } else {
            // remove all getter/setter as we do not want to consider these methods
            removeAllSetterOrGetterMethods(operationsWithHandlerAnnotation);
            removeAllSetterOrGetterMethods(operationsWithCustomAnnotation);
            removeAllSetterOrGetterMethods(operationsWithBody);
        }
        
        if (type != null) {
            // filter all lists to only include methods with this argument type
            removeNonMatchingMethods(operationsWithHandlerAnnotation, type);
            removeNonMatchingMethods(operationsWithCustomAnnotation, type);
            removeNonMatchingMethods(operationsWithBody, type);
        }

        if (operationsWithHandlerAnnotation.size() > 1) {
            // if we have more than 1 @Handler then its ambiguous
            throw new AmbiguousMethodCallException(exchange, operationsWithHandlerAnnotation);
        }

        if (operationsWithHandlerAnnotation.size() == 1) {
            // methods with handler should be preferred
            return operationsWithHandlerAnnotation.get(0);
        } else if (operationsWithCustomAnnotation.size() == 1) {
            // if there is one method with an annotation then use that one
            return operationsWithCustomAnnotation.get(0);
        } else if (operationsWithBody.size() == 1) {
            // if there is one method with body then use that one
            return operationsWithBody.get(0);
        }

        Collection<MethodInfo> possibleOperations = new ArrayList<MethodInfo>();
        possibleOperations.addAll(operationsWithBody);
        possibleOperations.addAll(operationsWithCustomAnnotation);

        if (!possibleOperations.isEmpty()) {
             // multiple possible operations so find the best suited if possible
            MethodInfo answer = chooseMethodWithMatchingBody(exchange, possibleOperations);
            if (answer == null) {
                throw new AmbiguousMethodCallException(exchange, possibleOperations);
            } else {
                return answer;
            }
        }

        // not possible to determine
        return null;
    }
    
    @SuppressWarnings("rawtypes")
    private MethodInfo chooseMethodWithMatchingBody(Exchange exchange, Collection<MethodInfo> operationList)
        throws AmbiguousMethodCallException {
        // lets see if we can find a method who's body param type matches the message body
        Message in = exchange.getIn();
        Object body = in.getBody();
        if (body != null) {
            Class bodyType = body.getClass();
            if (LOG.isTraceEnabled()) {
                LOG.trace("Matching for method with a single parameter that matches type: " + bodyType.getCanonicalName());
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
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Found a possible method: " + methodInfo);
                    }
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
            return chooseBestPossibleMethodInfo(exchange, operationList, body, possibles, possiblesWithException);
        }

        // no match so return null
        return null;
    }

    private MethodInfo chooseBestPossibleMethodInfo(Exchange exchange, Collection<MethodInfo> operationList, Object body,
                                                    List<MethodInfo> possibles, List<MethodInfo> possiblesWithException)
        throws AmbiguousMethodCallException {

        Exception exception = ExpressionBuilder.exchangeExceptionExpression().evaluate(exchange, Exception.class);
        if (exception != null && possiblesWithException.size() == 1) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Exchange has exception set so we prefer method that also has exception as parameter");
            }
            // prefer the method that accepts exception in case we have an exception also
            return possiblesWithException.get(0);
        } else if (possibles.size() == 1) {
            return possibles.get(0);
        } else if (possibles.isEmpty()) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("No possible methods so now trying to convert body to parameter types");
            }

            // lets try converting
            Object newBody = null;
            MethodInfo matched = null;
            int matchCounter = 0;
            for (MethodInfo methodInfo : operationList) {
                if (methodInfo.getBodyParameterType().isInstance(body)) {
                    return methodInfo;
                }
                
                Object value = convertToType(exchange, methodInfo.getBodyParameterType(), body);
                if (value != null) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Converted body from: " + body.getClass().getCanonicalName()
                                + "to: " + methodInfo.getBodyParameterType().getCanonicalName());
                    }
                    matchCounter++;
                    newBody = value;
                    matched = methodInfo;
                }
            }
            if (matchCounter > 1) {
                throw new AmbiguousMethodCallException(exchange, Arrays.asList(matched, matched));
            }
            if (matched != null) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Setting converted body: " + body);
                }
                Message in = exchange.getIn();
                in.setBody(newBody);
                return matched;
            }
        } else {
            // if we only have a single method with custom annotations, lets use that one
            if (operationsWithCustomAnnotation.size() == 1) {
                MethodInfo answer = operationsWithCustomAnnotation.get(0);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("There are only one method with annotations so we choose it: " + answer);
                }
                return answer;
            }
            // phew try to choose among multiple methods with annotations
            return chooseMethodWithCustomAnnotations(exchange, possibles);
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
        // if we have only one method with custom annotations lets choose that
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
        if (chosen != null) {
            return chosen;
        }
        throw new AmbiguousMethodCallException(exchange, possibles);
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
            return ExpressionBuilder.propertyExpression(propertyAnnotation.value());
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

    private static void removeNonMatchingMethods(List<MethodInfo> methods, String name) {
        Iterator<MethodInfo> it = methods.iterator();
        while (it.hasNext()) {
            MethodInfo info = it.next();
            if (!name.equals(info.getMethod().getName())) {
                // name does not match so remove it
                it.remove();
            }
        }
    }
    
    @SuppressWarnings("rawtypes")
    private static void removeNonMatchingMethods(List<MethodInfo> methods, Class type) {
        Iterator<MethodInfo> it = methods.iterator();
        while (it.hasNext()) {
            MethodInfo info = it.next();
            Class<?>[] parameterTypes = info.getMethod().getParameterTypes();
            if (!(parameterTypes.length > 0 && parameterTypes[0].isAssignableFrom(type))) {
                // type does not match so remove it
                it.remove();
            }
        }
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
     * Gets the list of methods sorted by A..Z method name.
     *
     * @return the methods.
     */
    @SuppressWarnings("unchecked")
    public List<MethodInfo> getMethods() {
        if (operations.isEmpty()) {
            return Collections.EMPTY_LIST;
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
