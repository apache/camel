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
package org.apache.camel.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.Endpoint;
import org.apache.camel.builder.EndpointConsumerBuilder;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DslArg;
import org.apache.camel.util.URISupport;

/**
 * Polls a message from a static endpoint
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "poll")
@XmlAccessorType(XmlAccessType.FIELD)
public class PollDefinition extends NoOutputDefinition<PollDefinition> implements EndpointRequiredDefinition {

    @XmlTransient
    private String endpointUriToString;
    @XmlTransient
    protected Endpoint endpoint;
    @XmlTransient
    protected EndpointConsumerBuilder endpointConsumerBuilder;

    @XmlAttribute
    @Metadata(description = "To use a variable to store the received message body (only body, not headers). This makes it handy to use variables for user data and to easily control what data to use for sending and receiving.")
    private String variableReceive;
    @XmlAttribute
    @Metadata(required = true,
              description = "The uri of the endpoint to poll a single message from. The result is stored in the original message body (or in a variable if variableReceive is set).")
    @DslArg
    private String uri;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.time.Duration", defaultValue = "20000",
              description = "Timeout in millis when polling from the external service. The default value is 20000 (20 seconds).")
    @DslArg(position = 1, renderType = "long")
    private String timeout;

    public PollDefinition() {
    }

    protected PollDefinition(PollDefinition source) {
        super(source);
        this.endpointUriToString = source.endpointUriToString;
        this.endpoint = source.endpoint;
        this.endpointConsumerBuilder = source.endpointConsumerBuilder;
        this.variableReceive = source.variableReceive;
        this.uri = source.uri;
        this.timeout = source.timeout;
    }

    public PollDefinition(String uri) {
        this();
        setUri(uri);
    }

    public PollDefinition(Endpoint endpoint) {
        this();
        setEndpoint(endpoint);
    }

    public PollDefinition(EndpointConsumerBuilder endpointDefinition) {
        this();
        setEndpointConsumerBuilder(endpointDefinition);
    }

    @Override
    public String getShortName() {
        return "poll";
    }

    @Override
    public String toString() {
        return "Poll[" + getLabel() + "]";
    }

    public String getVariableReceive() {
        return variableReceive;
    }

    public void setVariableReceive(String variableReceive) {
        this.variableReceive = variableReceive;
    }

    public PollDefinition copyDefinition() {
        return new PollDefinition(this);
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        clear();
        this.uri = uri;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(Endpoint endpoint) {
        clear();
        this.endpoint = endpoint;
        this.uri = endpoint != null ? endpoint.getEndpointUri() : null;
    }

    public String getTimeout() {
        return timeout;
    }

    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }

    public EndpointConsumerBuilder getEndpointConsumerBuilder() {
        return endpointConsumerBuilder;
    }

    public void setEndpointConsumerBuilder(EndpointConsumerBuilder endpointConsumerBuilder) {
        clear();
        this.endpointConsumerBuilder = endpointConsumerBuilder;
    }

    public String getEndpointUri() {
        if (endpointConsumerBuilder != null) {
            return endpointConsumerBuilder.getRawUri();
        } else if (endpoint != null) {
            return endpoint.getEndpointUri();
        } else {
            return uri;
        }
    }

    @Override
    public String getLabel() {
        if (endpointUriToString == null) {
            String value = null;
            try {
                value = getEndpointUri();
            } catch (RuntimeException e) {
                // ignore any exception and use null for building the string value
            }
            // ensure to sanitize uri so we do not show sensitive information such as passwords
            endpointUriToString = URISupport.sanitizeUri(value);
        }

        String uri = endpointUriToString;
        return uri != null ? uri : "no uri supplied";
    }

    protected void clear() {
        this.endpointUriToString = null;
        this.endpointConsumerBuilder = null;
        this.endpoint = null;
        this.uri = null;
    }

}
