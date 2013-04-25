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
package org.apache.camel.component.routebox.direct;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.routebox.RouteboxConfiguration;
import org.apache.camel.component.routebox.RouteboxEndpoint;

public class RouteboxDirectEndpoint extends RouteboxEndpoint {
    private final Map<String, RouteboxDirectConsumer> consumers = new HashMap<String, RouteboxDirectConsumer>();

    @Deprecated
    public RouteboxDirectEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public RouteboxDirectEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public RouteboxDirectEndpoint(String uri, Component component, RouteboxConfiguration config) {
        super(uri, component, config);
    }

    public Producer createProducer() throws Exception {
        return new RouteboxDirectProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        RouteboxDirectConsumer answer = new RouteboxDirectConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    public boolean isSingleton() {
        return true;
    }

    public void addConsumer(RouteboxDirectConsumer consumer) {
        String key = consumer.getEndpoint().getEndpointKey();
        consumers.put(key, consumer);
    }

    public void removeConsumer(RouteboxDirectConsumer consumer) {
        String key = consumer.getEndpoint().getEndpointKey();
        consumers.remove(key);
    }

    public boolean hasConsumer(RouteboxDirectConsumer consumer) {
        String key = consumer.getEndpoint().getEndpointKey();
        return consumers.containsKey(key);
    }

    public RouteboxDirectConsumer getConsumer() {
        String key = getEndpointKey();
        return consumers.get(key);
    }

}
