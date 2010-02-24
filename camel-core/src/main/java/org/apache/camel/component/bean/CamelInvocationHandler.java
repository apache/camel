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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * An {@link java.lang.reflect.InvocationHandler} which invokes a
 * message exchange on a camel {@link Endpoint}
 *
 * @version $Revision$
 */
public class CamelInvocationHandler implements InvocationHandler {
    private static final transient Log LOG = LogFactory.getLog(CamelInvocationHandler.class);

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
        Throwable fault = exchange.getException();
        if (fault != null) {
            if (fault instanceof RuntimeCamelException) {
                // if the inner cause is a runtime exception we can throw it directly
                if (fault.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) ((RuntimeCamelException) fault).getCause();
                }
                throw (RuntimeCamelException) fault;
            }
            throw fault;
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
}

