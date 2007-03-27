/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.pojo;

import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;

import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

/**
 * @version $Revision$
 */
public class PojoConsumer extends DefaultConsumer<PojoExchange> {
    private final Object pojo;

    public PojoConsumer(Endpoint<PojoExchange> endpoint, Processor<PojoExchange> processor, Object pojo) {
        super(endpoint, processor);
        this.pojo = pojo;
    }


    /**
     * Creates a Proxy object that can be used to deliver inbound PojoExchanges.
     *
     * @param interfaces
     * @return
     */
    public Object createInboundProxy(Class interfaces[]) {
        return Proxy.newProxyInstance(pojo.getClass().getClassLoader(), interfaces, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (!isStarted()) {
                    throw new IllegalStateException("The endpoint is not active: " + getEndpoint().getEndpointUri());
                }
                PojoInvocation invocation = new PojoInvocation(proxy, method, args);
                PojoExchange exchange = getEndpoint().createExchange();
                exchange.setInvocation(invocation);
                getProcessor().onExchange(exchange);
                Throwable fault = exchange.getException();
                if (fault != null) {
                    throw new InvocationTargetException(fault);
                }
                return exchange.getOut().getBody();
            }
        });
    }
}
