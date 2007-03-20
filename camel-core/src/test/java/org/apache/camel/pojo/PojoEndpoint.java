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
package org.apache.camel.pojo;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.apache.camel.CamelContainer;
import org.apache.camel.impl.DefaultEndpoint;

/**
 * Represents a pojo endpoint that uses reflection
 * to send messages around.
 *
 * @version $Revision: 519973 $
 */
public class PojoEndpoint extends DefaultEndpoint<PojoExchange> {

    private final Object pojo;
	private final PojoComponent component;
    
	public PojoEndpoint(String uri, CamelContainer container, PojoComponent component, Object pojo) {
        super(uri, container);
		this.component = component;
		this.pojo = pojo;
    }

	/**
	 *  This causes us to invoke the endpoint Pojo using reflection.
	 */
    public void onExchange(PojoExchange exchange) {
        PojoInvocation invocation = exchange.getRequest();
        try {
			Object response = invocation.getMethod().invoke(pojo, invocation.getArgs());
			exchange.setResponse(response);
		} catch (InvocationTargetException e) {
			exchange.setFault(e.getCause());
		} catch ( RuntimeException e ) {
			throw e;
		} catch ( Throwable e ) {
			throw new RuntimeException(e);
		}
    }

    public PojoExchange createExchange() {
        return new PojoExchange();
    }

    @Override
    protected void doActivate() {
    	component.registerActivation(getEndpointUri(), this);
    }
    
    @Override
    protected void doDeactivate() {
    	component.unregisterActivation(getEndpointUri());
    }

    /**
     * Creates a Proxy object that can be used to deliver inbound PojoExchanges.
     * 
     * @param interfaces
     * @return
     */
    public Object createInboundProxy(Class interfaces[]) {
    	final PojoEndpoint endpoint = component.lookupActivation(getEndpointUri());
    	if( endpoint == null ) 
			throw new IllegalArgumentException("The endpoint has not been activated yet: "+getEndpointUri());
    	
    	return Proxy.newProxyInstance(pojo.getClass().getClassLoader(), interfaces, new InvocationHandler(){
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				if( !activated.get() ) {
					PojoInvocation invocation = new PojoInvocation(proxy, method, args);
					PojoExchange exchange = new PojoExchange();
					exchange.setRequest(invocation);
					endpoint.getInboundProcessor().onExchange(exchange);
					Throwable fault = exchange.getFault();
					if ( fault != null ) {
						throw new InvocationTargetException(fault);
					}
					return exchange.getResponse();
				}
				throw new IllegalStateException("The endpoint is not active: "+getEndpointUri());
			}
		});
    }
}
