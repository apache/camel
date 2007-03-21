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

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.EndpointResolver;
import org.apache.camel.util.ObjectHelper;

/**
 * An implementation of {@link EndpointResolver} that creates 
 * {@link PojoEndpoint} objects.
 *
 * The synatax for a Pojo URI looks like:
 * 
 * <pre><code>pojo:component:queuename</code></pre>
 * 
 * @version $Revision: 519901 $
 */
public class PojoEndpointResolver implements EndpointResolver<PojoExchange> {
		
	/**
	 * Finds the {@see QueueComponent} specified by the uri.  If the {@see QueueComponent} 
	 * object do not exist, it will be created.
	 * 
	 * @see org.apache.camel.EndpointResolver#resolveComponent(org.apache.camel.CamelContext, java.lang.String)
	 */
	public Component resolveComponent(CamelContext container, String uri) {
		String id[] = getEndpointId(uri);        
		return resolveQueueComponent(container, id[0]);
	}

	/**
	 * Finds the {@see QueueEndpoint} specified by the uri.  If the {@see QueueEndpoint} or it's associated
	 * {@see QueueComponent} object do not exist, they will be created.
	 * 
	 * @see org.apache.camel.EndpointResolver#resolveEndpoint(org.apache.camel.CamelContext, java.lang.String)
	 */
	public PojoEndpoint resolveEndpoint(CamelContext container, String uri) {
		String id[] = getEndpointId(uri);        
    	PojoComponent component = resolveQueueComponent(container, id[0]);        
        Object pojo = component.lookupRegisteredPojo(id[1]);
		return new PojoEndpoint(uri, container, component, pojo);
    }

	private PojoComponent resolveQueueComponent(CamelContext container, String componentName) {
    	Component rc = container.getComponent(componentName);
    	if( rc == null ) {
    		throw new IllegalArgumentException("Invalid URI, pojo component does not exist: "+componentName);
    	}
    	return (PojoComponent) rc;
	}

	/**
	 * @return an array that looks like: [componentName,endpointName] 
	 */
	private String[] getEndpointId(String uri) {
		String rc [] = {null, null};
		String splitURI[] = ObjectHelper.splitOnCharacter(uri, ":", 3);        
    	if( splitURI[2] != null ) {
    		rc[0] =  splitURI[1];
    		rc[1] =  splitURI[2];
    	} else {
    		throw new IllegalArgumentException("Invalid URI, component not specified in URI: "+uri);
    	}
		return rc;
	}

}
