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
package org.apache.camel.component.spring.ws.bean;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.spring.ws.SpringWebserviceConsumer;
import org.apache.camel.component.spring.ws.SpringWebserviceEndpoint;
import org.apache.camel.component.spring.ws.type.EndpointMappingKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.ws.server.endpoint.MessageEndpoint;
import org.springframework.ws.soap.addressing.core.MessageAddressingProperties;
import org.springframework.ws.soap.addressing.messageid.MessageIdStrategy;
import org.springframework.ws.soap.addressing.server.AbstractAddressingEndpointMapping;
import org.springframework.ws.soap.addressing.server.AnnotationActionEndpointMapping;
import org.springframework.ws.transport.WebServiceMessageSender;

/**
 * Provides support for full WS-Addressing. Supported are faultAction and
 * response action. For more details look at @see
 * {@link AbstractAddressingEndpointMapping}. Implementation of the
 * {@link org.springframework.ws.server.EndpointMapping} consumer interface that
 * uses the camel uri to map to a WS-Addressing {@code Action} header.
 * <p/>
 */
public class WSACamelEndpointMapping extends AbstractAddressingEndpointMapping implements CamelSpringWSEndpointMapping {

    private static final Logger LOG = LoggerFactory.getLogger(WSACamelEndpointMapping.class);

    private Map<EndpointMappingKey, MessageEndpoint> endpoints = new ConcurrentHashMap<EndpointMappingKey, MessageEndpoint>();

    private String outputActionSuffix = AnnotationActionEndpointMapping.DEFAULT_OUTPUT_ACTION_SUFFIX;

    private String faultActionSuffix = AnnotationActionEndpointMapping.DEFAULT_OUTPUT_ACTION_SUFFIX;

    @Override
    protected Object getEndpointInternal(MessageAddressingProperties map) {
        // search the endpoint with compositeKeyFirst
        for (EndpointMappingKey key : endpoints.keySet()) {
            String compositeOrSimpleKey = null;
            switch (key.getType()) {
            case ACTION:
                compositeOrSimpleKey = getActionCompositeLookupKey(map);
                break;
            case TO:
                compositeOrSimpleKey = getToCompositeLookupKey(map);
                break;
            default:
                throw new RuntimeCamelException(
                                                "Invalid mapping type specified. Supported types are: spring-ws:action:<WS-Addressing Action>(optional:<WS-Addressing To>?<params...>\n)"
                                                    + "spring-ws:to:<WS-Addressing To>(optional:<WS-Addressing Action>?<params...>)");
            }
            // lookup for specific endpoint
            if (compositeOrSimpleKey != null && key.getLookupKey().equals(compositeOrSimpleKey)) {
                LOG.debug("Found mapping for key" + key);
                return endpoints.get(key);
            }
        }
        
        // look up for the simple key
        for (EndpointMappingKey key : endpoints.keySet()) {
            String simpleKey = null;
            switch (key.getType()) {
            case ACTION:
                if (map.getAction() != null) {
                    simpleKey = map.getAction().toString();
                }
                break;
            case TO:
                if (map.getTo() != null) {
                    simpleKey = map.getTo().toString();
                }
                break;
            default:
                throw new RuntimeCamelException(
                                                "Invalid mapping type specified. Supported types are: spring-ws:action:<WS-Addressing Action>(optional:<WS-Addressing To>?<params...>\n)"
                                                    + "spring-ws:to:<WS-Addressing To>(optional:<WS-Addressing Action>?<params...>)");
            }
            // look up for less specific endpoint
            if (simpleKey != null && key.getLookupKey().equals(simpleKey)) {
                LOG.debug("Found mapping for key" + key);
                return endpoints.get(key);
            }
        }
        return null;
    }

    /**
     * Generate a lookupKey for a given WS-Addressing message using action
     * property. The possible combination are:
     * <ul>
     * <li>wsaAction</li>
     * <li>wsaAction:wsaGetTo</li>
     * </ul>
     * 
     * @param map
     * @return
     */
    protected String getActionCompositeLookupKey(MessageAddressingProperties map) {
        String key = "";
        if (map.getAction() != null) {
            key = map.getAction().toString();
        }
        if (map.getTo() != null) {
            if (!key.isEmpty()) {
                key += ":";
            }
            key += map.getTo().toString();
        }
        return key;
    }

    /**
     * Generate a lookupKey for a given WS-Addressing message using getTo
     * property. The possible combination are:
     * <ul>
     * <li>wsaGetTo</li>
     * <li>wsaGetTo:wsaAction</li>
     * </ul>
     * 
     * @param map
     * @return
     */
    protected String getToCompositeLookupKey(MessageAddressingProperties map) {
        String key = "";
        if (map.getTo() != null) {
            key = map.getTo().toString();
        }
        if (map.getAction() != null) {
            if (!key.isEmpty()) {
                key += ":";
            }
            key += map.getAction().toString();
        }
        return key;
    }

    /**
     * Return output camel uri param or default action or null
     */
    @Override
    protected URI getResponseAction(Object endpoint, MessageAddressingProperties requestMap) {
        SpringWebserviceEndpoint camelEndpoint = getSpringWebserviceEndpoint(endpoint);

        URI actionUri = camelEndpoint.getConfiguration().getOutputAction();
        if (actionUri == null) {
            actionUri = getDefaultResponseAction(camelEndpoint, requestMap);
        }
        return actionUri;
    }

    /**
     * Configure message sender for wsa:replyTo from a camel route definition.
     * The route definition has priority over this endpoint.
     */
    @Override
    protected WebServiceMessageSender[] getMessageSenders(Object endpoint) {
        SpringWebserviceEndpoint camelEndpoint = getSpringWebserviceEndpoint(endpoint);

        if (camelEndpoint.getConfiguration().getMessageSender() != null) {
            return new WebServiceMessageSender[] {camelEndpoint.getConfiguration().getMessageSender()};
        }

        return super.getMessageSenders(endpoint);
    }

    /**
     * Configure message id strategy for wsa:replyTo The route definition has
     * priority over this endpoint.
     */
    @Override
    protected MessageIdStrategy getMessageIdStrategy(Object endpoint) {
        SpringWebserviceEndpoint camelEndpoint = getSpringWebserviceEndpoint(endpoint);

        if (camelEndpoint.getConfiguration().getMessageIdStrategy() != null) {
            return camelEndpoint.getConfiguration().getMessageIdStrategy();
        }

        return super.getMessageIdStrategy(endpoint);
    }

    /**
     * return fault came uri param or default fault action or null
     */
    @Override
    protected URI getFaultAction(Object endpoint, MessageAddressingProperties requestMap) {
        SpringWebserviceEndpoint camelEndpoint = getSpringWebserviceEndpoint(endpoint);

        URI actionUri = camelEndpoint.getConfiguration().getFaultAction();
        if (actionUri == null) {
            actionUri = getDefaultFaultAction(camelEndpoint, requestMap);
        }
        return actionUri;

    }

    private SpringWebserviceEndpoint getSpringWebserviceEndpoint(Object endpoint) {
        Assert.isInstanceOf(SpringWebserviceConsumer.class, endpoint, "Endpoint needs to be an instance of SpringWebserviceConsumer");

        SpringWebserviceConsumer springWebserviceConsumer = (SpringWebserviceConsumer)endpoint;
        return (SpringWebserviceEndpoint)springWebserviceConsumer.getEndpoint();
    }

    protected URI getDefaultResponseAction(Object endpoint, MessageAddressingProperties requestMap) {
        URI requestAction = requestMap.getAction();
        if (requestAction != null) {
            return URI.create(requestAction.toString() + getOutputActionSuffix());
        } else {
            return null;
        }
    }

    protected URI getDefaultFaultAction(Object endpoint, MessageAddressingProperties requestMap) {
        URI requestAction = requestMap.getAction();
        if (requestAction != null) {
            return URI.create(requestAction.toString() + getFaultActionSuffix());
        } else {
            return null;
        }
    }

    public void addConsumer(EndpointMappingKey key, MessageEndpoint endpoint) {
        endpoints.put(key, endpoint);
    }

    public void removeConsumer(Object key) {
        endpoints.remove(key);
    }

    /**
     * Returns the suffix to add to request <code>Action</code>s for reply
     * messages.
     */
    public String getOutputActionSuffix() {
        return outputActionSuffix;
    }

    /**
     * Sets the suffix to add to request <code>Action</code>s for reply
     * messages.
     * 
     * @see #DEFAULT_OUTPUT_ACTION_SUFFIX
     */
    public void setOutputActionSuffix(String outputActionSuffix) {
        Assert.hasText(outputActionSuffix, "'outputActionSuffix' must not be empty");
        this.outputActionSuffix = outputActionSuffix;
    }

    /**
     * Returns the suffix to add to request <code>Action</code>s for reply fault
     * messages.
     */
    public String getFaultActionSuffix() {
        return faultActionSuffix;
    }

    /**
     * Sets the suffix to add to request <code>Action</code>s for reply fault
     * messages.
     * 
     * @see #DEFAULT_FAULT_ACTION_SUFFIX
     */
    public void setFaultActionSuffix(String faultActionSuffix) {
        Assert.hasText(faultActionSuffix, "'faultActionSuffix' must not be empty");
        this.faultActionSuffix = faultActionSuffix;
    }

}
