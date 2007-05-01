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
package org.apache.camel;

import java.util.HashMap;
import java.util.Map;

/**
 * A <a href="http://activemq.apache.org/camel/routes.html">Route</a>
 * defines the processing used on an inbound message exchange
 * from a specific {@see Endpoint} within a {@link CamelContext}
 * 
 * @version $Revision$
 */
public class Route<E extends Exchange> {

	private final Map<String, Object> properties = new HashMap<String, Object>(16);
	private Endpoint<E> endpoint;
	private Processor processor;

	public Route(Endpoint<E> endpoint, Processor processor) {
		this.endpoint = endpoint;
		this.processor = processor;
	}

    @Override
    public String toString() {
        return "Route[" + endpoint + " -> " + processor + "]";
    }

    public Endpoint<E> getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(Endpoint<E> endpoint) {
		this.endpoint = endpoint;
	}

	public Processor getProcessor() {
		return processor;
	}

	public void setProcessor(Processor processor) {
		this.processor = processor;
	}

	/**
	 * This property map is used to associate information about
	 * the route.
	 * 
	 * @return
	 */
	public Map<String, Object> getProperties() {
		return properties;
	}
}
