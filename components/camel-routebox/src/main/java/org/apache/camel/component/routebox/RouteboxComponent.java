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
package org.apache.camel.component.routebox;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.routebox.direct.RouteboxDirectEndpoint;
import org.apache.camel.component.routebox.seda.RouteboxSedaEndpoint;
import org.apache.camel.impl.UriEndpointComponent;

public class RouteboxComponent extends UriEndpointComponent {
    final RouteboxConfiguration config;
    private final Map<String, BlockingQueue<Exchange>> queues = new HashMap<String, BlockingQueue<Exchange>>();
    
    public RouteboxComponent() {
        super(RouteboxEndpoint.class);
        config = new RouteboxConfiguration();
    }

    public RouteboxComponent(CamelContext context) {
        super(context, RouteboxEndpoint.class);
        config = new RouteboxConfiguration();
    }
    
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
        throws Exception {
        RouteboxEndpoint blackboxRouteEndpoint = null;
        
        config.parseURI(new URI(uri), parameters, this);
        if (config.getInnerProtocol().equalsIgnoreCase("direct")) {
            blackboxRouteEndpoint = new RouteboxDirectEndpoint(uri, this, config);
            setProperties(blackboxRouteEndpoint.getConfig(), parameters);
        } else {
            String baseUri = getQueueKey(uri);
            blackboxRouteEndpoint = new RouteboxSedaEndpoint(uri, this, config, createQueue(baseUri, parameters));
            setProperties(blackboxRouteEndpoint.getConfig(), parameters);
        }
        
        return blackboxRouteEndpoint;
    }

    public synchronized BlockingQueue<Exchange> createQueue(String uri, Map<String, Object> parameters) {
        if (queues.containsKey(uri)) {
            return queues.get(uri);
        }

        // create queue
        BlockingQueue<Exchange> queue;
        Integer size = config.getQueueSize();
        if (size != null && size > 0) {
            queue = new LinkedBlockingQueue<Exchange>(size);
        } else {
            queue = new LinkedBlockingQueue<Exchange>();
        }

        queues.put(uri, queue);
        return queue;
    }
    
    protected String getQueueKey(String uri) {
        if (uri.contains("?")) {
            // strip parameters
            uri = uri.substring(0, uri.indexOf('?'));
        }
        return uri;
    }
    
    @Override
    protected void doStop() throws Exception {
        queues.clear();
        super.doStop();
    }
}
