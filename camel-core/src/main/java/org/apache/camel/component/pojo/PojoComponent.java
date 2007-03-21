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

import java.util.HashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;

/**
 * Represents the component that manages {@link PojoEndpoint}.  It holds the 
 * list of named pojos that queue endpoints reference.
 *
 * @version $Revision: 519973 $
 */
public class PojoComponent implements Component<PojoExchange> {
	
    private final HashMap<String, Object> registry = new HashMap<String, Object>();
    private final HashMap<String, PojoEndpoint> activatedEndpoints = new HashMap<String, PojoEndpoint>();
    
	private CamelContext container;

	public void registerPojo(String uri, Object pojo) {
		registry.put(uri, pojo);
	}
	public Object lookupRegisteredPojo(String uri) {
		return registry.get(uri);
	}
	
	public void registerActivation(String uri, PojoEndpoint endpoint) {
		activatedEndpoints.put(uri, endpoint);
	}
	public void unregisterActivation(String uri) {
		activatedEndpoints.remove(uri);
	}
	public PojoEndpoint lookupActivation(String uri) {
		return activatedEndpoints.get(uri);
	}
	
	
	public void setContext(CamelContext container) {
		this.container = container;
	}
	public CamelContext getContainer() {
		return container;
	}
    
}
