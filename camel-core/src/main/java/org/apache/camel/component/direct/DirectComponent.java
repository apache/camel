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
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ServiceHelper;

/**
 * The <a href="http://camel.apache.org/direct.html">Direct Component</a> manages {@link DirectEndpoint} and holds the list of named direct endpoints.
 *
 * @version
 */
public class DirectComponent extends UriEndpointComponent {

    // must keep a map of consumers on the component to ensure endpoints can lookup old consumers
    // later in case the DirectEndpoint was re-created due the old was evicted from the endpoints LRUCache
    // on DefaultCamelContext
    private final Map<String, DirectConsumer> consumers = new HashMap<String, DirectConsumer>();
    @Metadata(label = "producer", defaultValue = "true")
    private boolean block = true;
    @Metadata(label = "producer", defaultValue = "30000")
    private long timeout = 30000L;

    public DirectComponent() {
        super(DirectEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        DirectEndpoint endpoint = new DirectEndpoint(uri, this, consumers);
        endpoint.setBlock(block);
        endpoint.setTimeout(timeout);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopServices(consumers);
        consumers.clear();
        super.doStop();
    }

    public boolean isBlock() {
        return block;
    }

    /**
     * If sending a message to a direct endpoint which has no active consumer,
     * then we can tell the producer to block and wait for the consumer to become active.
     */
    public void setBlock(boolean block) {
        this.block = block;
    }

    public long getTimeout() {
        return timeout;
    }

    /**
     * The timeout value to use if block is enabled.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
}
