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
package org.apache.camel.component.avro;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.avro.Protocol;
import org.apache.avro.reflect.ReflectData;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.URISupport;

public class AvroComponent extends UriEndpointComponent {

    private ConcurrentMap<String, AvroListener> listenerRegistry = new ConcurrentHashMap<String, AvroListener>();

    @Metadata(label = "advanced")
    private AvroConfiguration configuration;

    public AvroComponent() {
        super(AvroEndpoint.class);
    }

    public AvroComponent(CamelContext context) {
        super(context, AvroEndpoint.class);
    }


    /**
     * A factory method allowing derived components to create a new endpoint
     * from the given URI, remaining path and optional parameters
     *
     * @param uri        the full URI of the endpoint
     * @param remaining  the remaining part of the URI without the query
     *                   parameters or component prefix
     * @param parameters the optional parameters passed in
     * @return a newly created endpoint or null if the endpoint cannot be
     *         created based on the inputs
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
        applyToConfiguration(config, endpointUri, parameters);

        if (AvroConstants.AVRO_NETTY_TRANSPORT.equals(endpointUri.getScheme())) {
            return new AvroNettyEndpoint(remaining, this, config);
        } else if (AvroConstants.AVRO_HTTP_TRANSPORT.equals(endpointUri.getScheme())) {
            return new AvroHttpEndpoint(remaining, this, config);
        } else {
            throw new IllegalArgumentException("Unknown avro scheme. Should use either netty or http.");
        }
    }

    /**
     * Applies endpoint parameters to configuration & resolves protocol and other required configuration properties.
     */
    private void applyToConfiguration(AvroConfiguration config, URI endpointUri, Map<String, Object> parameters) throws Exception {
        config.parseURI(endpointUri, parameters, this);
        setProperties(config, parameters);

        if (config.getProtocol() == null && config.getProtocolClassName() != null) {
            Class<?> protocolClass = getCamelContext().getClassResolver().resolveClass(config.getProtocolClassName());
            if (protocolClass != null) {
                try {
                    Field f = protocolClass.getField("PROTOCOL");
                    if (f != null) {
                        Protocol protocol = (Protocol)f.get(null);
                        config.setProtocol(protocol);
                    }
                } catch (NoSuchFieldException e) {
                    ReflectData reflectData = ReflectData.get();
                    config.setProtocol(reflectData.getProtocol(protocolClass));
                    config.setReflectionProtocol(true);
                }
            }
        }

        if (config.getProtocol() == null) {
            throw new IllegalArgumentException("Avro configuration does not contain protocol");
        }

        if (config.getMessageName() != null && !config.getProtocol().getMessages().containsKey(config.getMessageName())) {
            throw new IllegalArgumentException("Message " + config.getMessageName() + " is not defined in protocol");
        }

        if (config.isSingleParameter()) {
            Map<String, Protocol.Message> messageMap = config.getProtocol().getMessages();
            Iterable<Protocol.Message> messagesToCheck = config.getMessageName() == null 
                ? messageMap.values() 
                : Collections.singleton(messageMap.get(config.getMessageName()));
            for (Protocol.Message message : messagesToCheck) {
                if (message.getRequest().getFields().size() != 1) {
                    throw new IllegalArgumentException("Single parameter option can't be used with message "
                            + message.getName() + " because it has " + message.getRequest().getFields().size()
                            + " parameters defined"
                    );
                }
            }
        }
    }
    
    /**
     * Registers new responder with uri as key. Registers consumer in responder.
     * In case if responder is already registered by this uri then just
     * registers consumer.
     * 
     * @param uri URI of the endpoint without message name
     * @param messageName message name
     * @param consumer consumer that will be registered in providers` registry
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
     * Calls unregister of consumer by appropriate message name.
     * In case if all consumers are unregistered then it removes responder from the registry.
     *
     * @param uri URI of the endpoint without message name
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
