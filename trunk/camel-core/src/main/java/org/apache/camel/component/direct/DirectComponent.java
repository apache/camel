/**
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
package org.apache.camel.component.direct;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.ServiceHelper;

/**
 * Represents the component that manages {@link DirectEndpoint}. It holds the
 * list of named direct endpoints.
 *
 * @version 
 */
public class DirectComponent extends DefaultComponent {

    // must keep a map of consumers on the component to ensure endpoints can lookup old consumers
    // later in case the DirectEndpoint was re-created due the old was evicted from the endpoints LRUCache
    // on DefaultCamelContext
    private final Map<String, DirectConsumer> consumers = new HashMap<String, DirectConsumer>();

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Endpoint endpoint = new DirectEndpoint(uri, this, consumers);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopServices(consumers);
        consumers.clear();
        super.doStop();
    }
}
