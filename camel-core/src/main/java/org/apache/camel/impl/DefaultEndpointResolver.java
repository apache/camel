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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointResolver;
import org.apache.camel.util.FactoryFinder;
import org.apache.camel.util.ObjectHelper;

/**
 * An implementation of {@link org.apache.camel.EndpointResolver} that delegates to 
 * other {@link EndpointResolver} which are selected based on the uri prefix.
 * 
 * The delegate {@link EndpointResolver} are associated with uri prefixes by 
 * adding a property file with the same uri prefix in the
 * META-INF/services/org/apache/camel/EndpointResolver/
 * directory on the classpath.
 *
 * @version $Revision$
 */
public class DefaultEndpointResolver<E> implements EndpointResolver<E> {
    static final private FactoryFinder endpointResolverFactory = new FactoryFinder("META-INF/services/org/apache/camel/EndpointResolver/");
    
    public Endpoint<E> resolveEndpoint(CamelContext container, String uri) throws Exception {
    	EndpointResolver resolver = getDelegate(uri);
		return resolver.resolveEndpoint(container, uri);
    }

	public Component resolveComponent(CamelContext container, String uri) throws Exception {
    	EndpointResolver resolver = getDelegate(uri);
		return resolver.resolveComponent(container, uri);
	}

	private EndpointResolver getDelegate(String uri) {
		String splitURI[] = ObjectHelper.splitOnCharacter(uri, ":", 2);
    	if( splitURI[1] == null )
    		throw new IllegalArgumentException("Invalid URI, it did not contain a scheme: "+uri);
    	EndpointResolver resolver;
		try {
			resolver = (EndpointResolver) endpointResolverFactory.newInstance(splitURI[0]);
		} catch (Throwable e) {
			throw new IllegalArgumentException("Invalid URI, no EndpointResolver registered for scheme : "+splitURI[0], e);
		}
		return resolver;
	}

}
