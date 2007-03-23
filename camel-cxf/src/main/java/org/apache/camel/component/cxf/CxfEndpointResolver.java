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
package org.apache.camel.component.cxf;

import org.apache.camel.EndpointResolver;
import org.apache.camel.Component;
import org.apache.camel.CamelContext;
import org.apache.camel.util.ObjectHelper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.Callable;

/**
 * An implementation of {@link EndpointResolver} that creates
 * {@link CxfEndpoint} objects.
 *
 * The syntax for a MINA URI looks like:
 *
 * <pre><code>mina:</code></pre>
 *
 * @version $Revision:520964 $
 */
public class CxfEndpointResolver implements EndpointResolver<CxfExchange> {

	public static final String DEFAULT_COMPONENT_NAME = CxfEndpointResolver.class.getName();

	/**
	 * Finds the {@link CxfComponent} specified by the uri.  If the {@link CxfComponent}
	 * object do not exist, it will be created.
	 */
	public Component resolveComponent(CamelContext container, String uri) {
		String[] id = getEndpointId(uri);
		return resolveCxfComponent(container, id[0]);
	}

	/**
	 * Finds the {@link CxfEndpoint} specified by the uri.  If the {@link CxfEndpoint} or it's associated
	 * {@see QueueComponent} object do not exist, they will be created.
	 */
	public CxfEndpoint resolveEndpoint(CamelContext container, String uri) throws IOException, URISyntaxException {
		String[] urlParts = getEndpointId(uri);
    	CxfComponent component = resolveCxfComponent(container, urlParts[0]);
        return component.createEndpoint(uri, urlParts);
    }

	/**
	 * @return an array that looks like: [componentName,endpointName]
	 */
	private String[] getEndpointId(String uri) {
		String rc [] = {CxfEndpointResolver.DEFAULT_COMPONENT_NAME, null};
		String splitURI[] = ObjectHelper.splitOnCharacter(uri, ":", 3);
    	if( splitURI[2] != null ) {
    		rc[0] =  splitURI[1];
    		rc[1] =  splitURI[2];
    	} else {
    		rc[1] =  splitURI[1];
    	}
		return rc;
	}

	@SuppressWarnings("unchecked")
	private CxfComponent resolveCxfComponent(final CamelContext context, final String componentName) {
    	Component rc = context.getOrCreateComponent(componentName, new Callable(){
			public CxfComponent call() throws Exception {
                return new CxfComponent(context);
			}});
    	return (CxfComponent) rc;
	}


}
