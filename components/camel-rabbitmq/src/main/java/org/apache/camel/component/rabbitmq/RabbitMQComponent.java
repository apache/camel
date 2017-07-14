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
package org.apache.camel.component.rabbitmq;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.TrustManager;

import com.rabbitmq.client.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.IntrospectionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RabbitMQComponent extends UriEndpointComponent {

    public static final String ARG_PREFIX = "arg.";
    public static final String EXCHANGE_ARG_PREFIX = "exchange.";
    public static final String QUEUE_ARG_PREFIX = "queue.";
    public static final String BINDING_ARG_PREFIX = "binding.";

    private static final Logger LOG = LoggerFactory.getLogger(RabbitMQComponent.class);

    public RabbitMQComponent() {
        super(RabbitMQEndpoint.class);
    }

    public RabbitMQComponent(CamelContext context) {
        super(context, RabbitMQEndpoint.class);
    }

    @Override
    protected RabbitMQEndpoint createEndpoint(String uri,
                                              String remaining,
                                              Map<String, Object> params) throws Exception {
        URI host = new URI("http://" + remaining);
        String hostname = host.getHost();
        int portNumber = host.getPort();
        String exchangeName = ""; // We need to support the exchange to be "" the path is empty
        if (host.getPath().trim().length() > 1) {
            exchangeName = host.getPath().substring(1);
        }

        // ConnectionFactory reference
        ConnectionFactory connectionFactory = resolveAndRemoveReferenceParameter(params, "connectionFactory", ConnectionFactory.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> clientProperties = resolveAndRemoveReferenceParameter(params, "clientProperties", Map.class);
        TrustManager trustManager = resolveAndRemoveReferenceParameter(params, "trustManager", TrustManager.class);
        RabbitMQEndpoint endpoint;
        if (connectionFactory == null) {
            endpoint = new RabbitMQEndpoint(uri, this);
        } else {
            endpoint = new RabbitMQEndpoint(uri, this, connectionFactory);
        }
        endpoint.setHostname(hostname);
        endpoint.setPortNumber(portNumber);
        endpoint.setExchangeName(exchangeName);
        endpoint.setClientProperties(clientProperties);
        endpoint.setTrustManager(trustManager);
        setProperties(endpoint, params);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating RabbitMQEndpoint with host {}:{} and exchangeName: {}",
                    new Object[]{endpoint.getHostname(), endpoint.getPortNumber(), endpoint.getExchangeName()});
        }

        HashMap<String, Object> args = new HashMap<>();
        args.putAll(IntrospectionSupport.extractProperties(params, ARG_PREFIX));
        endpoint.setArgs(args);

        HashMap<String, Object> argsCopy = new HashMap<>(args);
        
        // Combine the three types of rabbit arguments with their individual endpoint properties
        endpoint.getExchangeArgs().putAll(IntrospectionSupport.extractProperties(argsCopy, EXCHANGE_ARG_PREFIX));
        endpoint.getQueueArgs().putAll(IntrospectionSupport.extractProperties(argsCopy, QUEUE_ARG_PREFIX));
        endpoint.getBindingArgs().putAll(IntrospectionSupport.extractProperties(argsCopy, BINDING_ARG_PREFIX));

        return endpoint;
    }
}
