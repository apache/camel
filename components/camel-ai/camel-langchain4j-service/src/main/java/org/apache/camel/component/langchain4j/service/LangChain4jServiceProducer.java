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
package org.apache.camel.component.langchain4j.service;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.component.bean.BeanInfo;
import org.apache.camel.component.bean.MethodInfo;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer that invokes LangChain4j AI Service methods with parameter binding from Camel exchanges.
 * <p>
 * Method resolution is delegated to Camel's bean binding ({@link BeanInfo}), which pools the methods of the service
 * instance and of every interface it implements, and collects each parameter's annotations from wherever they are
 * declared. The producer then binds the parameters from the exchange body and headers according to the LangChain4j
 * annotations:
 * <ul>
 * <li>{@code @UserMessage} - binds from exchange body</li>
 * <li>{@code @V("variableName")} - binds from the {@code "variableName"} key of the
 * {@code CamelLangChain4jServiceVariables} map header</li>
 * <li>{@code @MemoryId} - binds from header {@code CamelLangChain4jServiceMemoryId}</li>
 * </ul>
 * <p>
 * The AI Service is looked up in the registry and its target method resolved when the producer starts, so
 * {@code lazyStartProducer=true} defers both the resolution and its failures to the first message. Parameter binding
 * strategies are computed once at the same time for thread-safe, efficient invocation, and every start recomputes them,
 * so a restarted producer picks up a service that was rebound in the registry meanwhile.
 */
public class LangChain4jServiceProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(LangChain4jServiceProducer.class);

    private Object serviceInstance;
    private MethodInfo targetMethodInfo;
    private Method targetMethod;
    private List<ParameterBinding> parameterBindings;

    /**
     * Creates a new producer.
     *
     * @param endpoint the endpoint
     */
    public LangChain4jServiceProducer(LangChain4jServiceEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public LangChain4jServiceEndpoint getEndpoint() {
        return (LangChain4jServiceEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // Resolve on producer start (not endpoint init) so lazyStartProducer can defer failures
        serviceInstance = lookupServiceInstance();
        targetMethodInfo = resolveTargetMethod();
        targetMethod = targetMethodInfo.getMethod();
        validateReturnType();
        parameterBindings = computeParameterBindings();

        LOG.debug("Started producer for AI Service '{}' method '{}' with {} parameter bindings",
                getEndpoint().getServiceName(), targetMethod.getName(), parameterBindings.size());
    }

    /**
     * Looks up the AI Service instance in the Camel registry.
     *
     * @return                     the AI Service instance
     * @throws NoSuchBeanException if no bean is registered under the configured service name
     */
    private Object lookupServiceInstance() {
        String serviceName = getEndpoint().getServiceName();
        Object instance = getEndpoint().getCamelContext().getRegistry().lookupByName(serviceName);
        if (instance == null) {
            throw new NoSuchBeanException(serviceName);
        }
        return instance;
    }

    /**
     * Resolves the method to invoke on the AI Service.
     * <p>
     * The introspection is delegated to Camel's bean binding, so the method is found whatever shape the registered
     * instance has, and each parameter carries the annotations declared on the AI Service interface.
     *
     * @return                          the resolved method
     * @throws IllegalArgumentException if method resolution fails
     */
    private MethodInfo resolveTargetMethod() {
        String serviceName = getEndpoint().getServiceName();
        BeanInfo beanInfo = new BeanInfo(getEndpoint().getCamelContext(), serviceInstance.getClass());

        List<MethodInfo> businessMethods = beanInfo.getMethods().stream()
                .filter(this::isBusinessMethod)
                .collect(Collectors.toList());

        if (businessMethods.isEmpty()) {
            throw new IllegalArgumentException(
                    "AI Service '" + serviceName + "' (endpoint: " + getEndpoint().getEndpointUri()
                                               + ") has no method to invoke");
        }

        String methodName = getEndpoint().getConfiguration().getMethod();

        if (businessMethods.size() == 1) {
            MethodInfo single = businessMethods.get(0);
            // An explicitly configured method name must still match (never ignore it silently)
            if (methodName != null && !methodName.isEmpty() && !single.getMethod().getName().equals(methodName)) {
                throw new IllegalArgumentException(
                        "Method '" + methodName + "' not found on AI Service '" + serviceName
                                                   + "' (endpoint: " + getEndpoint().getEndpointUri()
                                                   + "). Available methods: " + single.getMethod().getName());
            }
            return single;
        }

        // Multiple methods: require an explicit method parameter. A service instance built by a framework exposes the
        // framework's own methods next to the AI Service ones, so this is the normal case outside plain Java
        if (methodName == null || methodName.isEmpty()) {
            throw new IllegalArgumentException(
                    "AI Service '" + serviceName + "' (endpoint: " + getEndpoint().getEndpointUri()
                                               + ") has multiple methods. Specify method parameter. Available methods: "
                                               + listMethodNames(businessMethods));
        }

        List<MethodInfo> matchingMethods = businessMethods.stream()
                .filter(info -> info.getMethod().getName().equals(methodName))
                .collect(Collectors.toList());

        if (matchingMethods.isEmpty()) {
            throw new IllegalArgumentException(
                    "Method '" + methodName + "' not found on AI Service '" + serviceName
                                               + "' (endpoint: " + getEndpoint().getEndpointUri()
                                               + "). Available methods: " + listMethodNames(businessMethods));
        }

        if (matchingMethods.size() > 1) {
            throw new IllegalArgumentException(
                    "AI Service '" + serviceName + "' (endpoint: " + getEndpoint().getEndpointUri()
                                               + ") has multiple methods named '" + methodName
                                               + "'. Method overloading is not supported: method names must be unique.");
        }

        return matchingMethods.get(0);
    }

    /**
     * Checks if a method is a business method (abstract instance method, not default, static, or Object method).
     *
     * @param  info the method to check, as Camel's bean binding describes it
     * @return      true if the method is a business method
     */
    private boolean isBusinessMethod(MethodInfo info) {
        // A static method is never invoked on the instance. A default method, on the other hand, is a candidate like
        // any other: it is callable, and a service built by a framework implements it as a concrete method anyway, so
        // there would be no way to tell it apart
        if (info.isStaticMethod()) {
            return false;
        }

        // Camel's bean binding already discards equals, hashCode and the other java.lang.Object and java.lang.Proxy
        // method names, but not toString
        Method method = info.getMethod();
        return !(method.getName().equals("toString") && method.getParameterCount() == 0);
    }

    /**
     * Rejects a return type the producer cannot deliver to the route.
     * <p>
     * A method returning {@link TokenStream} hands back a stream nobody subscribes to: the producer would set it as the
     * message body, the route would carry an object that never emits, and the caller would see neither an answer nor an
     * error. The return type is known when the producer starts, so this fails there rather than silently at runtime.
     *
     * @throws IllegalArgumentException if the method returns a streaming type
     */
    private void validateReturnType() {
        if (TokenStream.class.isAssignableFrom(targetMethod.getReturnType())) {
            throw new IllegalArgumentException(
                    "Method '" + targetMethod.getName() + "' of AI Service '" + getEndpoint().getServiceName()
                                               + "' (endpoint: " + getEndpoint().getEndpointUri()
                                               + ") returns " + targetMethod.getReturnType().getName()
                                               + ". Streaming responses are not supported: the method must return a"
                                               + " synchronous result.");
        }
    }

    /**
     * Lists the names of business methods for error messages.
     *
     * @param  methods the list of business methods
     * @return         a comma-separated list of method names
     */
    private String listMethodNames(List<MethodInfo> methods) {
        return methods.stream()
                .map(info -> info.getMethod().getName())
                .sorted()
                .collect(Collectors.joining(", "));
    }

    /**
     * Finds an annotation among the ones Camel's bean binding collected for a parameter.
     *
     * @param  annotations    the annotations collected for the parameter
     * @param  annotationType the annotation to look for
     * @return                the annotation, or null when the parameter does not carry it
     */
    private static <T extends Annotation> T findAnnotation(Annotation[] annotations, Class<T> annotationType) {
        for (Annotation annotation : annotations) {
            if (annotationType.isInstance(annotation)) {
                return annotationType.cast(annotation);
            }
        }
        return null;
    }

    /**
     * Computes parameter binding strategies by analyzing method parameters and their annotations.
     * <p>
     * The annotations come from Camel's bean binding, which collected them across the whole type hierarchy, so they are
     * found even when the registered instance is a class generated by a framework. Validates that parameter bindings
     * are unambiguous and fail-fast on configuration errors.
     *
     * @return                          list of parameter bindings, one per method parameter
     * @throws IllegalArgumentException if parameter binding configuration is invalid
     */
    private List<ParameterBinding> computeParameterBindings() {
        Parameter[] parameters = targetMethod.getParameters();
        Annotation[][] parameterAnnotations = targetMethodInfo.getParameterAnnotations();
        List<ParameterBinding> bindings = new ArrayList<>(parameters.length);

        // Track @UserMessage and @MemoryId parameters for validation
        int userMessageCount = 0;
        int memoryIdCount = 0;

        // Track template variable names for duplicate detection
        Set<String> variableNames = new HashSet<>();

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> paramType = param.getType();
            // A class generated by a framework carries no parameter names, and the synthetic 'arg0' identifies
            // nothing, so fall back to the position, saying "position" so it does not read like a name
            String paramName = param.isNamePresent()
                    ? "'" + param.getName() + "'"
                    : "at position " + (i + 1);

            // Check for annotations
            Annotation[] annotations = parameterAnnotations[i];
            UserMessage userMessageAnnotation = findAnnotation(annotations, UserMessage.class);
            V vAnnotation = findAnnotation(annotations, V.class);
            MemoryId memoryIdAnnotation = findAnnotation(annotations, MemoryId.class);

            // Count how many binding annotations are present
            int annotationCount = 0;
            if (userMessageAnnotation != null) {
                annotationCount++;
            }
            if (vAnnotation != null) {
                annotationCount++;
            }
            if (memoryIdAnnotation != null) {
                annotationCount++;
            }

            // Validate: no conflicting annotations
            if (annotationCount > 1) {
                List<String> conflictingAnnotations = new ArrayList<>();
                if (userMessageAnnotation != null) {
                    conflictingAnnotations.add("@UserMessage");
                }
                if (vAnnotation != null) {
                    conflictingAnnotations.add("@V");
                }
                if (memoryIdAnnotation != null) {
                    conflictingAnnotations.add("@MemoryId");
                }
                throw new IllegalArgumentException(
                        "Parameter " + paramName + " on method '" + targetMethod.getName()
                                                   + "' of AI Service '" + getEndpoint().getServiceName()
                                                   + "' (endpoint: " + getEndpoint().getEndpointUri()
                                                   + ") has conflicting annotations: "
                                                   + String.join(" and ", conflictingAnnotations)
                                                   + ". A parameter can only have one binding annotation.");
            }

            // Create appropriate binding
            if (userMessageAnnotation != null) {
                userMessageCount++;
                bindings.add(new UserMessageBinding(paramType, "the @UserMessage parameter"));
            } else if (vAnnotation != null) {
                String variableName = vAnnotation.value();
                if (variableName.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Parameter " + paramName + " on method '" + targetMethod.getName()
                                                       + "' of AI Service '" + getEndpoint().getServiceName()
                                                       + "' (endpoint: " + getEndpoint().getEndpointUri()
                                                       + ") has an empty @V template variable name.");
                }
                // Two parameters with the same variable name would silently feed from the same map key
                if (!variableNames.add(variableName)) {
                    throw new IllegalArgumentException(
                            "Method '" + targetMethod.getName() + "' of AI Service '"
                                                       + getEndpoint().getServiceName() + "' (endpoint: "
                                                       + getEndpoint().getEndpointUri()
                                                       + ") has multiple parameters annotated with @V(\"" + variableName
                                                       + "\"). Template variable names must be unique.");
                }

                bindings.add(new TemplateVariableBinding(
                        paramType, "the @V(\"" + variableName + "\") parameter", variableName));
            } else if (memoryIdAnnotation != null) {
                memoryIdCount++;
                bindings.add(new MemoryIdBinding(paramType, "the @MemoryId parameter"));
            } else {
                // No annotation: fallback to body binding if it's the only parameter
                if (parameters.length == 1) {
                    bindings.add(new UserMessageBinding(paramType, "the parameter " + paramName));
                } else {
                    throw new IllegalArgumentException(
                            "Parameter " + paramName + " on method '" + targetMethod.getName()
                                                       + "' of AI Service '" + getEndpoint().getServiceName()
                                                       + "' (endpoint: " + getEndpoint().getEndpointUri()
                                                       + ") has no binding annotation. When a method has multiple parameters, "
                                                       + "each parameter must be annotated with @UserMessage, @V, or @MemoryId.");
                }
            }
        }

        // Validate: at most one @UserMessage parameter
        if (userMessageCount > 1) {
            throw new IllegalArgumentException(
                    "Method '" + targetMethod.getName() + "' of AI Service '" + getEndpoint().getServiceName()
                                               + "' (endpoint: " + getEndpoint().getEndpointUri()
                                               + ") has multiple @UserMessage parameters. Only one parameter can be annotated with @UserMessage.");
        }

        // Validate: at most one @MemoryId parameter (they would all silently feed from the same header)
        if (memoryIdCount > 1) {
            throw new IllegalArgumentException(
                    "Method '" + targetMethod.getName() + "' of AI Service '" + getEndpoint().getServiceName()
                                               + "' (endpoint: " + getEndpoint().getEndpointUri()
                                               + ") has multiple @MemoryId parameters. Only one parameter can be annotated with @MemoryId.");
        }

        return bindings;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // 1. Resolve parameter values
        Object[] args = new Object[parameterBindings.size()];
        for (int i = 0; i < parameterBindings.size(); i++) {
            ParameterBinding binding = parameterBindings.get(i);
            try {
                args[i] = binding.resolve(exchange);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Failed to resolve " + binding.describe() + " for method '"
                                                   + targetMethod.getName() + "' of AI Service '"
                                                   + getEndpoint().getServiceName() + "' (endpoint: "
                                                   + getEndpoint().getEndpointUri() + "): " + e.getMessage(),
                        e);
            }
        }

        // 2. Invoke method
        Object result;
        try {
            result = ObjectHelper.invokeMethodSafe(targetMethod, serviceInstance, args);
        } catch (InvocationTargetException e) {
            // Unwrap and propagate the original cause, so onException clauses match its real type
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            } else if (cause instanceof Error) {
                throw (Error) cause;
            } else {
                throw new RuntimeException("Unexpected throwable from AI Service method", cause);
            }
        }

        // 3. Handle return value
        if (targetMethod.getReturnType() != void.class && targetMethod.getReturnType() != Void.class) {
            exchange.getMessage().setBody(result);
        }
        // If void, leave body unchanged
    }

    /**
     * Strategy for resolving a method parameter value from an exchange.
     */
    interface ParameterBinding {
        /**
         * Resolves the parameter value from the exchange.
         *
         * @param  exchange  the exchange
         * @return           the resolved parameter value
         * @throws Exception if resolution fails
         */
        Object resolve(Exchange exchange) throws Exception;

        /**
         * Describes the parameter for error messages, by the role its annotation gives it.
         *
         * @return the description, such as {@code the @UserMessage parameter}
         */
        String describe();
    }

    /**
     * Binds a parameter from the exchange body (for @UserMessage or single un-annotated parameter).
     */
    private static class UserMessageBinding implements ParameterBinding {
        private final Class<?> parameterType;
        private final String description;

        UserMessageBinding(Class<?> parameterType, String description) {
            this.parameterType = parameterType;
            this.description = description;
        }

        @Override
        public Object resolve(Exchange exchange) throws Exception {
            Object body = exchange.getMessage().getBody();
            if (body == null) {
                throw new IllegalArgumentException(
                        "Message body is null but " + description + " expects a "
                                                   + parameterType.getName() + " user message");
            }
            // Use Camel type conversion
            return exchange.getContext().getTypeConverter().mandatoryConvertTo(parameterType, exchange, body);
        }

        @Override
        public String describe() {
            return description;
        }
    }

    /**
     * Binds a parameter from a key of the {@link LangChain4jServiceHeaders#VARIABLES} map header (for @V template
     * variables).
     */
    private static class TemplateVariableBinding implements ParameterBinding {
        private final Class<?> parameterType;
        private final String description;
        private final String variableName;

        TemplateVariableBinding(Class<?> parameterType, String description, String variableName) {
            this.parameterType = parameterType;
            this.description = description;
            this.variableName = variableName;
        }

        @Override
        public Object resolve(Exchange exchange) throws Exception {
            Map<?, ?> variables = exchange.getMessage().getHeader(LangChain4jServiceHeaders.VARIABLES, Map.class);
            if (variables == null) {
                throw new NoSuchHeaderException(exchange, LangChain4jServiceHeaders.VARIABLES, parameterType);
            }
            Object value = variables.get(variableName);
            if (value == null) {
                throw new IllegalArgumentException(
                        "Template variable '" + variableName + "' not found in the '"
                                                   + LangChain4jServiceHeaders.VARIABLES + "' map header");
            }
            // Use Camel type conversion
            return exchange.getContext().getTypeConverter().mandatoryConvertTo(parameterType, exchange, value);
        }

        @Override
        public String describe() {
            return description;
        }
    }

    /**
     * Binds a parameter from the memory ID header (for @MemoryId).
     */
    private static class MemoryIdBinding implements ParameterBinding {
        private final Class<?> parameterType;
        private final String description;

        MemoryIdBinding(Class<?> parameterType, String description) {
            this.parameterType = parameterType;
            this.description = description;
        }

        @Override
        public Object resolve(Exchange exchange) throws Exception {
            Object headerValue = exchange.getMessage().getHeader(LangChain4jServiceHeaders.MEMORY_ID);
            if (headerValue == null) {
                throw new NoSuchHeaderException(exchange, LangChain4jServiceHeaders.MEMORY_ID, parameterType);
            }
            // Use Camel type conversion
            return exchange.getContext().getTypeConverter().mandatoryConvertTo(parameterType, exchange, headerValue);
        }

        @Override
        public String describe() {
            return description;
        }
    }
}
