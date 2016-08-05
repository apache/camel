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
import org.apache.camel.component.consul.enpoint.ConsulAgentProducer;
import org.apache.camel.component.consul.enpoint.ConsulEventConsumer;
import org.apache.camel.component.consul.enpoint.ConsulEventProducer;
import org.apache.camel.component.consul.enpoint.ConsulKeyValueConsumer;
import org.apache.camel.component.consul.enpoint.ConsulKeyValueProducer;
import org.apache.camel.impl.UriEndpointComponent;

/**
 * Represents the component that manages {@link ConsulEndpoint}.
 */
public class ConsulComponent extends UriEndpointComponent {
    
    public ConsulComponent() {
        super(ConsulEndpoint.class);
    }

    public ConsulComponent(CamelContext context) {
        super(context, ConsulEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        return ConsulApiEndpoint.valueOf(remaining).create(
            remaining,
            uri,
            this,
            createConfiguration(parameters)
        );
    }

    private ConsulConfiguration createConfiguration(Map<String, Object> parameters) throws Exception {
        ConsulConfiguration configuration = new ConsulConfiguration(getCamelContext());
        setProperties(configuration, parameters);

        return configuration;
    }

    // *************************************************************************
    // Consul Api Enpoints (see https://www.consul.io/docs/agent/http.html)
    // *************************************************************************

    private enum ConsulApiEndpoint {
        kv(ConsulKeyValueProducer::new, ConsulKeyValueConsumer::new),
        event(ConsulEventProducer::new, ConsulEventConsumer::new),
        agent(ConsulAgentProducer::new, null);

        private final ConsulEndpoint.ProducerFactory producerFactory;
        private final ConsulEndpoint.ConsumerFactory consumerFactory;

        ConsulApiEndpoint(ConsulEndpoint.ProducerFactory producerFactory, ConsulEndpoint.ConsumerFactory consumerFactory) {
            this.producerFactory = producerFactory;
            this.consumerFactory = consumerFactory;
        }

        public Endpoint create(String apiEndpoint, String uri, ConsulComponent component, ConsulConfiguration configuration) throws Exception {
            return new ConsulEndpoint(
                apiEndpoint,
                uri,
                component,
                configuration,
                producerFactory,
                consumerFactory);
        }
    }
}
