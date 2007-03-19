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

import org.apache.camel.CamelContainer;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointResolver;

/**
 * An implementation of {@link EndpointResolver} that creates 
 * {@link QueueEndpoint} objects.
 *
 * @version $Revision: 519901 $
 */
public class QueueEndpointResolver<E> implements EndpointResolver<E> {
	
	static QueueComponent defaultComponent = new QueueComponent();
	
    public Endpoint<E> resolve(CamelContainer container, String uri) {
        
    	// TODO: we could look at the uri scheme to look for a named
    	// component registered on the container
    	QueueComponent<E> component = defaultComponent;
        
        Queue<E> queue = component.getOrCreateQueue(uri);
		return new QueueEndpoint<E>(uri, container, queue);
    }

}
