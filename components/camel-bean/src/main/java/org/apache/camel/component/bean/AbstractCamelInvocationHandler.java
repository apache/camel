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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeProperty;
import org.apache.camel.Header;
import org.apache.camel.Headers;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Variable;
import org.apache.camel.Variables;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractCamelInvocationHandler implements InvocationHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractCamelInvocationHandler.class);
    private static final List<Method> EXCLUDED_METHODS = new ArrayList<>();
    public static final String CAMEL_INVOCATION_HANDLER = "CamelInvocationHandler";
    private static ExecutorService executorService;
    protected final Endpoint endpoint;
    protected final Producer producer;

    static {
        // exclude all java.lang.Object methods as we dont want to invoke them
        EXCLUDED_METHODS.addAll(Arrays.asList(Object.class.getMethods()));
    }

    protected AbstractCamelInvocationHandler(Endpoint endpoint, Producer producer) {
        this.endpoint = endpoint;
        this.producer = producer;
    }

    private static Object getBody(Exchange exchange, Class<?> type) throws InvalidPayloadException {
        if (exchange.getMessage().getBody() != null) {
            return exchange.getMessage().getMandatoryBody(type);
        } else {
            return null;
        }
    }

    @Override
    public final Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        if (isValidMethod(method)) {
            return doInvokeProxy(proxy, method, args);
        } else {
            // invalid method then invoke methods on this instead
            if ("toString".equals(method.getName())) {
                return this.toString();
            } else if ("hashCode".equals(method.getName())) {
                return this.hashCode();
            } else if ("equals".equals(method.getName())) {
                return Boolean.FALSE;
            }
            return null;
        }
    }

    public abstract Object doInvokeProxy(Object proxy, Method method, Object[] args) throws Throwable;

    @SuppressWarnings("unchecked")
    protected Object invokeProxy(final Method method, final ExchangePattern pattern, Object[] args, boolean binding)
            throws Throwable {
        final Exchange exchange = DefaultExchange.newFromEndpoint(endpoint, pattern);

        //Need to check if there are mutiple arguments and the parameters have no annotations for binding,
        //then use the original bean invocation.

        boolean canUseBinding = method.getParameterCount() == 1;

        if (!canUseBinding) {
            for (Parameter parameter : method.getParameters()) {
                if (parameter.isAnnotationPresent(Header.class)
                        || parameter.isAnnotationPresent(Headers.class)
                        || parameter.isAnnotationPresent(Variable.class)
                        || parameter.isAnnotationPresent(Variables.class)
                        || parameter.isAnnotationPresent(ExchangeProperty.class)
                        || parameter.isAnnotationPresent(Body.class)) {
                    canUseBinding = true;
                    break;
                }
            }
        }

        if (binding && canUseBinding) {
            // in binding mode we bind the passed in arguments (args) to the created exchange
            // using the existing Camel @Body, @Header, @Headers, @ExchangeProperty annotations
            // if no annotation then its bound as the message body
            int index = 0;
            for (Annotation[] row : method.getParameterAnnotations()) {
                Object value = args[index];
                if (row == null || row.length == 0) {
                    // assume its message body when there is no annotations
                    exchange.getIn().setBody(value);
                } else {
                    for (Annotation ann : row) {
                        if (ann.annotationType().isAssignableFrom(Header.class)) {
                            Header header = (Header) ann;
                            String name = header.value();
                            exchange.getIn().setHeader(name, value);
                        } else if (ann.annotationType().isAssignableFrom(Headers.class)) {
                            Map<String, Object> map
                                    = exchange.getContext().getTypeConverter().tryConvertTo(Map.class, exchange, value);
                            if (map != null) {
                                exchange.getIn().getHeaders().putAll(map);
                            }
                        } else if (ann.annotationType().isAssignableFrom(Variable.class)) {
                            Variable variable = (Variable) ann;
                            String name = variable.value();
                            exchange.setVariable(name, value);
                        } else if (ann.annotationType().isAssignableFrom(Variables.class)) {
                            Map<String, Object> map
                                    = exchange.getContext().getTypeConverter().tryConvertTo(Map.class, exchange, value);
                            if (map != null) {
                                exchange.getVariables().putAll(map);
                            }
                        } else if (ann.annotationType().isAssignableFrom(ExchangeProperty.class)) {
                            ExchangeProperty ep = (ExchangeProperty) ann;
                            String name = ep.value();
                            exchange.setProperty(name, value);
                        } else {
                            exchange.getIn().setBody(value);
                        }
                    }
                }
                index++;
            }
        } else {
            if (args != null) {
                if (args.length == 1) {
                    exchange.getIn().setBody(args[0]);
                } else {
                    exchange.getIn().setBody(args);
                }
            }
        }

        if (binding) {
            LOG.trace("Binding to service interface as @Body,@Header,@ExchangeProperty detected when calling proxy method: {}",
                    method);
        } else {
            LOG.trace(
                    "No binding to service interface as @Body,@Header,@ExchangeProperty not detected when calling proxy method: {}",
                    method);
        }

        return doInvoke(method, exchange);
    }

    protected Object invokeWithBody(final Method method, Object body, final ExchangePattern pattern) throws Throwable {
        final Exchange exchange = DefaultExchange.newFromEndpoint(endpoint, pattern);
        exchange.getIn().setBody(body);

        return doInvoke(method, exchange);
    }

    protected Object doInvoke(final Method method, final Exchange exchange) throws Throwable {

        // is the return type a future
        final boolean isFuture = method.getReturnType() == Future.class;

        // create task to execute the proxy and gather the reply
        FutureTask<Object> task = new FutureTask<>(new Callable<Object>() {
            public Object call() throws Exception {
                // process the exchange
                LOG.trace("Proxied method call {} invoking producer: {}", method.getName(), producer);
                producer.process(exchange);

                Object answer = afterInvoke(method, exchange, isFuture);
                LOG.trace("Proxied method call {} returning: {}", method.getName(), answer);
                return answer;
            }
        });

        if (isFuture) {
            // submit task and return future
            if (LOG.isTraceEnabled()) {
                LOG.trace("Submitting task for exchange id {}", exchange.getExchangeId());
            }
            getExecutorService(exchange.getContext()).submit(task);
            return task;
        } else {
            // execute task now
            try {
                task.run();
                return task.get();
            } catch (ExecutionException e) {
                // we don't want the wrapped exception from JDK
                throw e.getCause();
            }
        }
    }

    protected Object afterInvoke(Method method, Exchange exchange, boolean isFuture) throws Exception {
        // check if we had an exception
        Exception cause = exchange.getException();
        if (cause != null) {
            Throwable found = findSuitableException(cause, method);
            if (found != null) {
                if (found instanceof Exception) {
                    throw (Exception) found;
                } else {
                    // wrap as exception
                    throw new CamelExchangeException("Error processing exchange", exchange, cause);
                }
            }
            // special for runtime camel exceptions as they can be nested
            if (cause instanceof RuntimeCamelException) {
                // if the inner cause is a runtime exception we can throw it
                // directly
                if (cause.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) cause.getCause();
                }
                throw cause;
            }
            // okay just throw the exception as is
            throw cause;
        }

        Class<?> to = isFuture ? getGenericType(exchange.getContext(), method.getGenericReturnType()) : method.getReturnType();

        // do not return a reply if the method is VOID
        if (to == Void.TYPE) {
            return null;
        }

        return getBody(exchange, to);
    }

    protected static Class<?> getGenericType(CamelContext context, Type type) throws ClassNotFoundException {
        if (type == null) {
            // fallback and use object
            return Object.class;
        }

        // unfortunately java dont provide a nice api for getting the generic
        // type of the return type
        // due type erasure, so we have to gather it based on a String
        // representation
        String name = StringHelper.between(type.toString(), "<", ">");
        if (name != null) {
            if (name.contains("<")) {
                // we only need the outer type
                name = StringHelper.before(name, "<");
            }
            return context.getClassResolver().resolveMandatoryClass(name);
        } else {
            // fallback and use object
            return Object.class;
        }
    }

    protected static synchronized ExecutorService getExecutorService(CamelContext context) {
        // CamelContext will shutdown thread pool when it shutdown so we can
        // lazy create it on demand
        // but in case of hot-deploy or the likes we need to be able to
        // re-create it (its a shared static instance)
        if (executorService == null || executorService.isTerminated() || executorService.isShutdown()) {
            // try to lookup a pool first based on id/profile
            executorService = context.getRegistry().lookupByNameAndType(CAMEL_INVOCATION_HANDLER, ExecutorService.class);
            if (executorService == null) {
                executorService = context.getExecutorServiceManager().newThreadPool(CamelInvocationHandler.class,
                        CAMEL_INVOCATION_HANDLER, CAMEL_INVOCATION_HANDLER);
            }
            if (executorService == null) {
                executorService = context.getExecutorServiceManager().newDefaultThreadPool(CamelInvocationHandler.class,
                        CAMEL_INVOCATION_HANDLER);
            }
        }
        return executorService;
    }

    /**
     * Tries to find the best suited exception to throw.
     * <p/>
     * It looks in the exception hierarchy from the caused exception and matches this against the declared exceptions
     * being thrown on the method.
     *
     * @param  cause  the caused exception
     * @param  method the method
     * @return        the exception to throw, or <tt>null</tt> if not possible to find a suitable exception
     */
    protected Throwable findSuitableException(Throwable cause, Method method) {
        if (method.getExceptionTypes() == null || method.getExceptionTypes().length == 0) {
            return null;
        }

        // see if there is any exception which matches the declared exception on
        // the method
        for (Class<?> type : method.getExceptionTypes()) {
            Object fault = ObjectHelper.getException(type, cause);
            if (fault != null) {
                return Throwable.class.cast(fault);
            }
        }

        return null;
    }

    protected boolean isValidMethod(Method method) {
        // must not be in the excluded list
        for (Method excluded : EXCLUDED_METHODS) {
            if (ObjectHelper.isOverridingMethod(excluded, method)) {
                // the method is overriding an excluded method so its not valid
                return false;
            }
        }
        return true;
    }

}
