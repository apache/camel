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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultConsumer;

/**
 * @version $Revision$
 */
public class PojoConsumer extends DefaultConsumer<PojoExchange> implements InvocationHandler {

    private final PojoEndpoint endpoint;

	public PojoConsumer(PojoEndpoint endpoint, Processor<PojoExchange> processor) {
        super(endpoint, processor);
		this.endpoint = endpoint;
    }
    
    @Override
    protected void doStart() throws Exception {
    	PojoComponent component = endpoint.getComponent();
    	PojoConsumer consumer = component.getConsumer(endpoint.getPojoId());
    	if( consumer != null ) {
    		throw new RuntimeCamelException("There is a consumer already registered for endpoint: "+endpoint.getEndpointUri());
    	}
    	component.addConsumer(endpoint.getPojoId(), this);    	
    }

    @Override
    protected void doStop() throws Exception {
    	PojoComponent component = endpoint.getComponent();
    	component.removeConsumer(endpoint.getPojoId());    	
    }
    
    /**
     * Creates a Proxy which generates inbound exchanges on the consumer.
     */
    public Object createProxy(ClassLoader cl, Class interfaces[]) {
        return Proxy.newProxyInstance(cl, interfaces, this);
    }
    /**
     * Creates a Proxy which generates inbound exchanges on the consumer.
     */
    public Object createProxy(Class interfaces[]) {
    	if( interfaces.length < 1 ) {
    		throw new IllegalArgumentException("You must provide at least 1 interface class.");
    	}
        return createProxy(interfaces[0].getClassLoader(), interfaces);
    }    
    /**
     * Creates a Proxy which generates inbound exchanges on the consumer.
     */
    @SuppressWarnings("unchecked")
	public <T> T createProxy(ClassLoader cl, Class<T> interfaceClass) {
        return (T) createProxy(cl, new Class[]{interfaceClass});
    }
    /**
     * Creates a Proxy which generates inbound exchanges on the consumer.
     */
    @SuppressWarnings("unchecked")
	public <T> T createProxy(Class<T> interfaceClass) {
        return (T) createProxy(new Class[]{interfaceClass});
    }


	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (!isStarted()) {
            throw new IllegalStateException("The endpoint is not active: " + getEndpoint().getEndpointUri());
        }
        PojoInvocation invocation = new PojoInvocation(proxy, method, args);
        PojoExchange exchange = getEndpoint().createExchange();
        exchange.setInvocation(invocation);
        getProcessor().process(exchange);
        Throwable fault = exchange.getException();
        if (fault != null) {
            throw new InvocationTargetException(fault);
        }
        return exchange.getOut().getBody();
	}
}
