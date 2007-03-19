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
package org.apache.camel.jms;

import org.apache.camel.EndpointResolver;
import org.apache.camel.CamelContainer;
import org.apache.camel.Component;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.queue.QueueEndpoint;
import org.apache.camel.queue.QueueComponent;

import java.util.Queue;
import java.util.concurrent.Callable;

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
 * @version $Revision$
 */
public class JmsEndpointResolver implements EndpointResolver<JmsExchange> {

	public static final String DEFAULT_COMPONENT_NAME = QueueComponent.class.getName();

	/**
	 * Finds the {@see JmsComponent} specified by the uri.  If the {@see JmsComponent}
	 * object do not exist, it will be created.
	 */
	public Component resolveComponent(CamelContainer container, String uri) {
		String splitURI[] = ObjectHelper.splitOnCharacter(uri, ":", 3);
		return resolveJmsComponent(container, splitURI);
	}

	/**
	 * Finds the {@see QueueEndpoint} specified by the uri.  If the {@see QueueEndpoint} or it's associated
	 * {@see QueueComponent} object do not exist, they will be created.
	 */
	public JmsEndpoint resolveEndpoint(CamelContainer container, String uri) {
		String splitURI[] = ObjectHelper.splitOnCharacter(uri, ":", 3);
    	JmsComponent component = resolveJmsComponent(container, splitURI);

        return component.createEndpoint(uri, splitURI[2]);
    }

	private JmsComponent resolveJmsComponent(final CamelContainer container, String[] splitURI) {
		String componentName = DEFAULT_COMPONENT_NAME;
    	if( splitURI[2] != null ) {
    		componentName =  splitURI[1];
    	}
    	Component rc = container.getOrCreateComponent(componentName, new Callable<JmsComponent>(){
			public JmsComponent call() throws Exception {
				return new JmsComponent(container);
			}});
    	return (JmsComponent) rc;
	}

}
