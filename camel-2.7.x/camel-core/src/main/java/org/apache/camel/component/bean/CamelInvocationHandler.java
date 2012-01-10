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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link java.lang.reflect.InvocationHandler} which invokes a
 * message exchange on a camel {@link Endpoint}
 *
 * @version 
 */
public class CamelInvocationHandler implements InvocationHandler {
    private static final transient Logger LOG = LoggerFactory.getLogger(CamelInvocationHandler.class);

    private final Endpoint endpoint;
    private final Producer producer;
    private final MethodInfoCache methodInfoCache;

    public CamelInvocationHandler(Endpoint endpoint, Producer producer, MethodInfoCache methodInfoCache) {
        this.endpoint = endpoint;
        this.producer = producer;
        this.methodInfoCache = methodInfoCache;
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        BeanInvocation invocation = new BeanInvocation(method, args);
        ExchangePattern pattern = ExchangePattern.InOut;
        MethodInfo methodInfo = methodInfoCache.getMethodInfo(method);
        if (methodInfo != null) {
            pattern = methodInfo.getPattern();
        }
        Exchange exchange = new DefaultExchange(endpoint, pattern);
        exchange.getIn().setBody(invocation);

        // process the exchange
        if (LOG.isTraceEnabled()) {
            LOG.trace("Proxied method call " + method.getName() + " invoking producer: " + producer);
        }
        producer.process(exchange);

        // check if we had an exception
        Throwable cause = exchange.getException();
        if (cause != null) {
            Throwable found = findSuitableException(cause, method);
            if (found != null) {
                throw found;
            }
            // special for runtime camel exceptions as they can be nested
            if (cause instanceof RuntimeCamelException) {
                // if the inner cause is a runtime exception we can throw it directly
                if (cause.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) ((RuntimeCamelException) cause).getCause();
                }
                throw (RuntimeCamelException) cause;
            }
            // okay just throw the exception as is
            throw cause;
        }

        // do not return a reply if the method is VOID or the MEP is not OUT capable
        Class<?> to = method.getReturnType();
        if (to == Void.TYPE || !pattern.isOutCapable()) {
            return null;
        }

        // only convert if there is a body
        if (!exchange.hasOut() || exchange.getOut().getBody() == null) {
            // there is no body so return null
            return null;
        }

        // use type converter so we can convert output in the desired type defined by the method
        // and let it be mandatory so we know wont return null if we cant convert it to the defined type
        Object answer = exchange.getOut().getMandatoryBody(to);
        if (LOG.isTraceEnabled()) {
            LOG.trace("Proxied method call " + method.getName() + " returning: " + answer);
        }
        return answer;
    }

    /**
     * Tries to find the best suited exception to throw.
     * <p/>
     * It looks in the exception hierarchy from the caused exception and matches this against the declared exceptions
     * being thrown on the method.
     *
     * @param cause   the caused exception
     * @param method  the method
     * @return the exception to throw, or <tt>null</tt> if not possible to find a suitable exception
     */
    protected Throwable findSuitableException(Throwable cause, Method method) {
        if (method.getExceptionTypes() == null || method.getExceptionTypes().length == 0) {
            return null;
        }

        // see if there is any exception which matches the declared exception on the method
        for (Class<?> type : method.getExceptionTypes()) {
            Object fault = ObjectHelper.getException(type, cause);
            if (fault != null) {
                return Throwable.class.cast(fault);
            }
        }

        return null;
    }

}

