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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractCamelInvocationHandler implements InvocationHandler {

    private static final transient Logger LOG = LoggerFactory.getLogger(CamelInvocationHandler.class);
    private static ExecutorService executorService;
    protected final Endpoint endpoint;
    protected final Producer producer;

    public AbstractCamelInvocationHandler(Endpoint endpoint, Producer producer) {
        this.endpoint = endpoint;
        this.producer = producer;
    }

    private static Object getBody(Exchange exchange, Class<?> type) throws InvalidPayloadException {
        // get the body from the Exchange from either OUT or IN
        if (exchange.hasOut()) {
            if (exchange.getOut().getBody() != null) {
                return exchange.getOut().getMandatoryBody(type);
            } else {
                return null;
            }
        } else {
            if (exchange.getIn().getBody() != null) {
                return exchange.getIn().getMandatoryBody(type);
            } else {
                return null;
            }
        }
    }

    protected Object invokeWithbody(final Method method, Object body, final ExchangePattern pattern) throws InterruptedException, Throwable {
        final Exchange exchange = new DefaultExchange(endpoint, pattern);
        exchange.getIn().setBody(body);

        // is the return type a future
        final boolean isFuture = method.getReturnType() == Future.class;

        // create task to execute the proxy and gather the reply
        FutureTask<Object> task = new FutureTask<Object>(new Callable<Object>() {
            public Object call() throws Exception {
                // process the exchange
                LOG.trace("Proxied method call {} invoking producer: {}", method.getName(), producer);
                producer.process(exchange);

                Object answer = afterInvoke(method, exchange, pattern, isFuture);
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

    protected Object afterInvoke(Method method, Exchange exchange, ExchangePattern pattern, boolean isFuture) throws Exception {
        // check if we had an exception
        Throwable cause = exchange.getException();
        if (cause != null) {
            Throwable found = findSuitableException(cause, method);
            if (found != null) {
                if (found instanceof Exception) {
                    throw (Exception)found;
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
                    throw (RuntimeException)((RuntimeCamelException)cause).getCause();
                }
                throw (RuntimeCamelException)cause;
            }
            // okay just throw the exception as is
            if (cause instanceof Exception) {
                throw (Exception)cause;
            } else {
                // wrap as exception
                throw new CamelExchangeException("Error processing exchange", exchange, cause);
            }
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
        String name = ObjectHelper.between(type.toString(), "<", ">");
        if (name != null) {
            if (name.contains("<")) {
                // we only need the outer type
                name = ObjectHelper.before(name, "<");
            }
            return context.getClassResolver().resolveMandatoryClass(name);
        } else {
            // fallback and use object
            return Object.class;
        }
    }

    @SuppressWarnings("deprecation")
    protected static synchronized ExecutorService getExecutorService(CamelContext context) {
        // CamelContext will shutdown thread pool when it shutdown so we can
        // lazy create it on demand
        // but in case of hot-deploy or the likes we need to be able to
        // re-create it (its a shared static instance)
        if (executorService == null || executorService.isTerminated() || executorService.isShutdown()) {
            // try to lookup a pool first based on id/profile
            executorService = context.getExecutorServiceStrategy().lookup(CamelInvocationHandler.class, "CamelInvocationHandler", "CamelInvocationHandler");
            if (executorService == null) {
                executorService = context.getExecutorServiceStrategy().newDefaultThreadPool(CamelInvocationHandler.class, "CamelInvocationHandler");
            }
        }
        return executorService;
    }

    /**
     * Tries to find the best suited exception to throw.
     * <p/>
     * It looks in the exception hierarchy from the caused exception and matches
     * this against the declared exceptions being thrown on the method.
     * 
     * @param cause the caused exception
     * @param method the method
     * @return the exception to throw, or <tt>null</tt> if not possible to find
     *         a suitable exception
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

}
