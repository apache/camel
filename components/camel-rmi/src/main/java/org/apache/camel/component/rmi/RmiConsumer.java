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
package org.apache.camel.component.rmi;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.bean.BeanInvocation;
import org.apache.camel.impl.DefaultConsumer;

/**
 * A {@link org.apache.camel.Consumer Consumer} which uses RMI's {@link UnicastRemoteObject} to consume
 * method invocations.
 * 
 * @version 
 */
public class RmiConsumer extends DefaultConsumer implements InvocationHandler {

    private final RmiEndpoint endpoint;
    private Remote stub;
    private Remote proxy;

    public RmiConsumer(RmiEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        Class<?>[] interfaces = new Class[endpoint.getRemoteInterfaces().size()];
        endpoint.getRemoteInterfaces().toArray(interfaces);
        proxy = (Remote)Proxy.newProxyInstance(endpoint.getClassLoader(), interfaces, this);
        stub = UnicastRemoteObject.exportObject(proxy, endpoint.getPort());

        try {
            Registry registry = endpoint.getRegistry();
            String name = endpoint.getName();
            registry.bind(name, stub);

        } catch (Exception e) {
            // Registration might fail.. clean up..
            try {
                UnicastRemoteObject.unexportObject(stub, true);
            } catch (Throwable ignore) {
            }
            stub = null;
            throw e;
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        try {
            Registry registry = endpoint.getRegistry();
            registry.unbind(endpoint.getName());
        } catch (Throwable e) {
            // do our best to unregister
        }
        UnicastRemoteObject.unexportObject(proxy, true);
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (!isStarted()) {
            throw new IllegalStateException("The endpoint is not active: " + getEndpoint().getEndpointUri());
        }
        BeanInvocation invocation = new BeanInvocation(method, args);
        Exchange exchange = getEndpoint().createExchange();
        exchange.getIn().setBody(invocation);
        try {
            log.debug("Invoking {} with args {}", method, args);
            getProcessor().process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }

        // is there a matching exception from the signature, then throw that
        // or fallback and ensure the exception is thrown as a RemoteException
        Throwable fault = exchange.getException();
        if (fault != null) {
            Object match = null;
            for (Class<?> type : method.getExceptionTypes()) {
                Object found = exchange.getException(type);
                if (found != null) {
                    match = found;
                    break;
                }
            }
            if (match instanceof Throwable) {
                // we have a match
                throw (Throwable) match;
            } else {
                throw new RemoteException("Error invoking " + method, fault);
            }
        }
        return exchange.getOut().getBody();
    }

    public Remote getProxy() {
        return proxy;
    }

    public Remote getStub() {
        return stub;
    }
}
