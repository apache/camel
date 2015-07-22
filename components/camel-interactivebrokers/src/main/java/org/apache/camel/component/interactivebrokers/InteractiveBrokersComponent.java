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
package org.apache.camel.component.interactivebrokers;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InteractiveBrokersComponent extends UriEndpointComponent {

    private final Logger logger = LoggerFactory.getLogger(InteractiveBrokersComponent.class);

    private InteractiveBrokersConfiguration configuration;
    
    private Map<InteractiveBrokersTransportTuple, InteractiveBrokersBinding> bindings
        = new ConcurrentHashMap<InteractiveBrokersTransportTuple, InteractiveBrokersBinding>();

    public InteractiveBrokersComponent() {
        super(InteractiveBrokersEndpoint.class);
        configuration = new InteractiveBrokersConfiguration();
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        URI url = new URI(uri);

        // must use copy as each endpoint can have different options
        ObjectHelper.notNull(configuration, "configuration");
        InteractiveBrokersConfiguration config = configuration.copy();
        config.configure(url);
        setProperties(config, parameters);

        InteractiveBrokersEndpoint endpoint = new InteractiveBrokersEndpoint(uri, this);
        endpoint.setConfiguration(config);
        setProperties(endpoint, parameters);

        return endpoint;
    }

    public InteractiveBrokersConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(InteractiveBrokersConfiguration configuration) {
        this.configuration = configuration;
    }
    
    protected synchronized InteractiveBrokersBinding getBinding(InteractiveBrokersTransportTuple tuple) {
        InteractiveBrokersBinding binding = bindings.get(tuple);
        if (binding == null) {
            binding = new InteractiveBrokersBinding(tuple);
            bindings.put(tuple, binding);
        }
        return binding;
    }
}