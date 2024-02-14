/*
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
package org.apache.camel.component.avro;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.URISupport;

@Component("avro")
public class AvroComponent extends DefaultComponent {

    private final ConcurrentMap<String, AvroListener> listenerRegistry = new ConcurrentHashMap<>();

    @Metadata(label = "advanced")
    private AvroConfiguration configuration;

    public AvroComponent() {
    }

    public AvroComponent(CamelContext context) {
        super(context);
    }

    /**
     * A factory method allowing derived components to create a new endpoint from the given URI, remaining path and
     * optional parameters
     *
     * @param  uri        the full URI of the endpoint
     * @param  remaining  the remaining part of the URI without the query parameters or component prefix
     * @param  parameters the optional parameters passed in
     * @return            a newly created endpoint or null if the endpoint cannot be created based on the inputs
     */
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        AvroConfiguration config;
        if (configuration != null) {
            config = configuration.copy();
        } else {
            config = new AvroConfiguration();
        }

        URI endpointUri = new URI(URISupport.normalizeUri(remaining));
        config.parseURI(endpointUri);

        Endpoint answer;
        if (AvroConstants.AVRO_NETTY_TRANSPORT.equals(endpointUri.getScheme())) {
            answer = new AvroNettyEndpoint(remaining, this, config);
        } else if (AvroConstants.AVRO_HTTP_TRANSPORT.equals(endpointUri.getScheme())) {
            answer = new AvroHttpEndpoint(remaining, this, config);
        } else {
            throw new IllegalArgumentException("Unknown avro scheme. Should use either netty or http.");
        }
        setProperties(answer, parameters);
        return answer;
    }

    /**
     * Registers new responder with uri as a key. Registers consumer in responder. In case if responder is already
     * registered by this uri, then register consumer.
     *
     * @param  uri         URI of the endpoint without message name
     * @param  messageName message name
     * @param  consumer    consumer that will be registered in providers` registry
     * @throws Exception
     */
    public void register(String uri, String messageName, AvroConsumer consumer) throws Exception {
        AvroListener listener = listenerRegistry.get(uri);
        if (listener == null) {
            listener = new AvroListener(consumer.getEndpoint());
            listenerRegistry.put(uri, listener);
        }
        listener.register(messageName, consumer);
    }

    /**
     * Calls unregister of consumer by the appropriate message name. In case if all consumers are unregistered, then it
     * removes responder from the registry.
     *
     * @param uri         URI of the endpoint without message name
     * @param messageName message name
     */
    public void unregister(String uri, String messageName) {
        if (listenerRegistry.get(uri).unregister(messageName)) {
            listenerRegistry.remove(uri);
        }
    }

    public AvroConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use a shared {@link AvroConfiguration} to configure options once
     */
    public void setConfiguration(AvroConfiguration configuration) {
        this.configuration = configuration;
    }
}
