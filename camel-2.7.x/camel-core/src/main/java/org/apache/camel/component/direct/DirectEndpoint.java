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

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;

/**
 * Represents a direct endpoint that synchronously invokes the consumer of the
 * endpoint when a producer sends a message to it.
 *
 * @version 
 */
public class DirectEndpoint extends DefaultEndpoint {

    private volatile Map<String, DirectConsumer> consumers;

    public DirectEndpoint() {
        this.consumers = new HashMap<String, DirectConsumer>();
    }

    public DirectEndpoint(String endpointUri) {
        super(endpointUri);
        this.consumers = new HashMap<String, DirectConsumer>();
    }

    public DirectEndpoint(String endpointUri, Component component) {
        this(endpointUri, component, new HashMap<String, DirectConsumer>());
    }

    public DirectEndpoint(String uri, Component component, Map<String, DirectConsumer> consumers) {
        super(uri, component);
        this.consumers = consumers;
    }

    public Producer createProducer() throws Exception {
        return new DirectProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new DirectConsumer(this, processor);
    }

    public boolean isSingleton() {
        return true;
    }

    public void addConsumer(DirectConsumer consumer) {
        String key = consumer.getEndpoint().getEndpointKey();
        consumers.put(key, consumer);
    }

    public void removeConsumer(DirectConsumer consumer) {
        String key = consumer.getEndpoint().getEndpointKey();
        consumers.remove(key);
    }

    public boolean hasConsumer(DirectConsumer consumer) {
        String key = consumer.getEndpoint().getEndpointKey();
        return consumers.containsKey(key);
    }

    public DirectConsumer getConsumer() {
        String key = getEndpointKey();
        return consumers.get(key);
    }

}
