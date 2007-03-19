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
package org.apache.camel.queue;

import java.util.Queue;
import java.util.concurrent.Callable;

import org.apache.camel.CamelContainer;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointResolver;
import org.apache.camel.util.ObjectHelper;

/**
 * An implementation of {@link EndpointResolver} that creates 
 * {@link QueueEndpoint} objects.
 *
 * The synatx for a Queue URI looks like:
 * 
 * <pre><code>queue:[component:]queuename</code></pre>
 * the component is optional, and if it is not specified, the default component name
 * is assumed.
 * 
 * @version $Revision: 519901 $
 */
public class QueueEndpointResolver<E> implements EndpointResolver<E> {
	
	public static final String DEFAULT_COMPONENT_NAME = QueueComponent.class.getName();
	
	/**
	 * Finds the {@see QueueComponent} specified by the uri.  If the {@see QueueComponent} 
	 * object do not exist, it will be created.
	 * 
	 * @see org.apache.camel.EndpointResolver#resolveComponent(org.apache.camel.CamelContainer, java.lang.String)
	 */
	public Component resolveComponent(CamelContainer container, String uri) {
		String splitURI[] = ObjectHelper.splitOnCharacter(uri, ":", 3);
		return resolveQueueComponent(container, splitURI);
	}

	/**
	 * Finds the {@see QueueEndpoint} specified by the uri.  If the {@see QueueEndpoint} or it's associated
	 * {@see QueueComponent} object do not exist, they will be created.
	 * 
	 * @see org.apache.camel.EndpointResolver#resolveEndpoint(org.apache.camel.CamelContainer, java.lang.String)
	 */
	public Endpoint<E> resolveEndpoint(CamelContainer container, String uri) {
		String splitURI[] = ObjectHelper.splitOnCharacter(uri, ":", 3);        
    	QueueComponent<E> component = resolveQueueComponent(container, splitURI);        
        Queue<E> queue = component.getOrCreateQueue(uri);
		return new QueueEndpoint<E>(uri, container, queue);
    }

	private QueueComponent<E> resolveQueueComponent(CamelContainer container, String[] splitURI) {
		String componentName = DEFAULT_COMPONENT_NAME;
    	if( splitURI[2] != null ) {
    		componentName =  splitURI[1];
    	}
    	Component rc = container.getOrCreateComponent(componentName, new Callable<Component<E>>(){
			public Component<E> call() throws Exception {
				return new QueueComponent<E>();
			}});
    	return (QueueComponent<E>) rc;
	}

}
