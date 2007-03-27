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
package org.apache.camel.component.pojo.timer;

import java.net.URISyntaxException;
import java.util.concurrent.Callable;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.EndpointResolver;
import org.apache.camel.component.pojo.PojoExchange;
import org.apache.camel.util.ObjectHelper;

/**
 * An implementation of {@link EndpointResolver} that creates 
 * {@link TimerEndpoint} objects.  TimerEndpoint objects can only 
 * be consumed.
 *
 * The synatax for a Timer URI looks like:
 * 
 * <pre><code>timer:[component:]timer-name?options</code></pre>
 * 
 * 
 * @version $Revision: 519901 $
 */
public class TimerEndpointResolver implements EndpointResolver<PojoExchange> {

	public static final String DEFAULT_COMPONENT_NAME = TimerComponent.class.getName();
		
	/**
	 * Finds the {@see QueueComponent} specified by the uri.  If the {@see QueueComponent} 
	 * object do not exist, it will be created.
	 * 
	 * @see org.apache.camel.EndpointResolver#resolveComponent(org.apache.camel.CamelContext, java.lang.String)
	 */
	public Component resolveComponent(CamelContext container, String uri) {
		String id[] = getEndpointId(uri);        
		return resolveTimerComponent(container, id[0]);
	}

	/**
	 * Finds the {@see QueueEndpoint} specified by the uri.  If the {@see QueueEndpoint} or it's associated
	 * {@see QueueComponent} object do not exist, they will be created.
	 * @throws URISyntaxException 
	 * 
	 * @see org.apache.camel.EndpointResolver#resolveEndpoint(org.apache.camel.CamelContext, java.lang.String)
	 */
	public TimerEndpoint resolveEndpoint(CamelContext container, String uri) throws URISyntaxException {
		String id[] = getEndpointId(uri);        
    	TimerComponent component = resolveTimerComponent(container, id[0]);
		return createEndpoint(uri, id[1], component);
    }

	/**
	 * PojoEndpointResolver subclasses can override to provide a custom PojoEndpoint implementation.
	 * @throws URISyntaxException 
	 */
	protected TimerEndpoint createEndpoint(String uri, String pojoId, TimerComponent component) throws URISyntaxException {
		return new TimerEndpoint(uri, pojoId, component);
	}

	private TimerComponent resolveTimerComponent(CamelContext container, String componentName) {
    	Component rc = container.getOrCreateComponent(componentName, new Callable<Component>(){
			public Component call() throws Exception {
				return new TimerComponent();
			}
		});
    	return (TimerComponent) rc;
	}

	/**
	 * @return an array that looks like: [componentName,endpointName] 
	 */
	private String[] getEndpointId(String uri) {
		String rc [] = {DEFAULT_COMPONENT_NAME, null};
		String splitURI[] = ObjectHelper.splitOnCharacter(uri, ":", 3);        
    	if( splitURI[2] != null ) {
    		rc[0] =  splitURI[1];
    		rc[1] =  splitURI[2];
    	} else {
    		rc[1] =  splitURI[1];
    	}
		return rc;
	}

}
