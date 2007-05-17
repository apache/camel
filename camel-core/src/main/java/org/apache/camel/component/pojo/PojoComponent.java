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
package org.apache.camel.component.pojo;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Producer;
import org.apache.camel.spi.Provider;
import org.apache.camel.impl.DefaultComponent;

/**
 * Represents the component that manages {@link PojoEndpoint}.  It holds the
 * list of named pojos that queue endpoints reference.
 *
 * @version $Revision: 519973 $
 */
public class PojoComponent extends DefaultComponent<PojoExchange> {
    protected final HashMap<String, Object> services = new HashMap<String, Object>();

    public void addService(String uri, Object pojo) {
        services.put(uri, pojo);
    }

    public void removeService(String uri) {
        services.remove(uri);
    }

    public Object getService(String uri) {
        return services.get(uri);
    }

    @Override
    protected Endpoint<PojoExchange> createEndpoint(String uri, final String remaining, Map parameters) throws Exception {
        Object pojo = getService(remaining);
        return new PojoEndpoint(uri, this, pojo);
    }
    
    /**
     * Creates a Proxy which sends PojoExchange to the endpoint.
     * @throws Exception 
     */
    static public Object createProxy(final Endpoint endpoint, ClassLoader cl, Class interfaces[]) throws Exception {
    	final Producer producer = endpoint.createProducer();
        return Proxy.newProxyInstance(cl, interfaces, new InvocationHandler() {        	
        	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                PojoInvocation invocation = new PojoInvocation(proxy, method, args);
                PojoExchange exchange = new PojoExchange(endpoint.getContext());                
                exchange.setInvocation(invocation);
                producer.process(exchange);                
                Throwable fault = exchange.getException();
                if (fault != null) {
                    throw new InvocationTargetException(fault);
                }
                return exchange.getOut().getBody();
        	}
        });
    }
    
    /**
     * Creates a Proxy which sends PojoExchange to the endpoint.
     * @throws Exception 
     */
    static public Object createProxy(Endpoint endpoint, Class interfaces[]) throws Exception {
    	if( interfaces.length < 1 ) {
    		throw new IllegalArgumentException("You must provide at least 1 interface class.");
    	}
        return createProxy(endpoint, interfaces[0].getClassLoader(), interfaces);
    }    
    /**
     * Creates a Proxy which sends PojoExchange to the endpoint.
     * @throws Exception 
     */
    @SuppressWarnings("unchecked")
	static public <T> T createProxy(Endpoint endpoint, ClassLoader cl, Class<T> interfaceClass) throws Exception {
        return (T) createProxy(endpoint, cl, new Class[]{interfaceClass});
    }
    
    /**
     * Creates a Proxy which sends PojoExchange to the endpoint.
     * @throws Exception 
     */
    @SuppressWarnings("unchecked")
	static public <T> T createProxy(Endpoint endpoint, Class<T> interfaceClass) throws Exception {
        return (T) createProxy(endpoint, new Class[]{interfaceClass});
    }



}
