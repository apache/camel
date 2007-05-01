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

import java.lang.reflect.InvocationTargetException;

import org.apache.camel.Consumer;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultProducer;

/**
 * Represents a pojo endpoint that uses reflection
 * to send messages around.
 *
 * @version $Revision: 519973 $
 */
public class PojoEndpoint extends DefaultEndpoint<PojoExchange> {
    private final PojoComponent component;
	private final String pojoId;

    public PojoEndpoint(String uri, PojoComponent component, String pojoId) {
        super(uri, component);
		this.pojoId = pojoId;
        this.component = component;
    }

    public Producer<PojoExchange> createProducer() throws Exception {
        final Object pojo = component.getService(pojoId);
        if( pojo == null )
        	throw new NoSuchEndpointException(getEndpointUri());
        
        return startService(new DefaultProducer<PojoExchange>(this) {
            public void process(PojoExchange exchange) {
                invoke(pojo, exchange);
            }
        });
    }

    public Consumer<PojoExchange> createConsumer(Processor<PojoExchange> processor) throws Exception {    	
    	PojoConsumer consumer = new PojoConsumer(this, processor);
        return startService(consumer);
    }

    /**
     * This causes us to invoke the endpoint Pojo using reflection.
     * @param pojo 
     */
    static public void invoke(Object pojo, PojoExchange exchange) {
        PojoInvocation invocation = exchange.getInvocation();
        try {
            Object response = invocation.getMethod().invoke(pojo, invocation.getArgs());
            exchange.getOut().setBody(response);
        }
        catch (InvocationTargetException e) {
            exchange.setException(e.getCause());
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public PojoExchange createExchange() {
        return new PojoExchange(getContext());
    }

	public PojoComponent getComponent() {
		return component;
	}

	public String getPojoId() {
		return pojoId;
	}

	public boolean isSingleton() {
		return true;
	}

}
