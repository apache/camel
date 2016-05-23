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
package org.apache.camel.component.consul;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.consul.enpoint.ConsulAgentEndpoint;
import org.apache.camel.component.consul.enpoint.ConsulEventEndpoint;
import org.apache.camel.component.consul.enpoint.ConsulKeyValueEndpoint;
import org.apache.camel.impl.UriEndpointComponent;

/**
 * Represents the component that manages {@link AbstractConsulEndpoint}.
 */
public class ConsulComponent extends UriEndpointComponent {
    
    public ConsulComponent() {
        super(AbstractConsulEndpoint.class);
    }

    public ConsulComponent(CamelContext context) {
        super(context, AbstractConsulEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ConsulConfiguration configuration = new ConsulConfiguration();
        setProperties(configuration, parameters);

        return ConsulApiEndpoint.valueOf(remaining).create(uri, this, configuration);
    }

    private enum ConsulApiEndpoint implements ConsulEndpointFactory {
        kv(ConsulKeyValueEndpoint::new),
        event(ConsulEventEndpoint::new),
        agent(ConsulAgentEndpoint::new);

        private final ConsulEndpointFactory factory;

        ConsulApiEndpoint(ConsulEndpointFactory factory) {
            this.factory = factory;
        }

        @Override
        public Endpoint create(String uri, ConsulComponent component, ConsulConfiguration configuration) throws Exception {
            return factory.create(uri, component, configuration);
        }
    }
}
