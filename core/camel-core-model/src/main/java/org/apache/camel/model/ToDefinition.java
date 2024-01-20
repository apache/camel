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

import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.builder.EndpointProducerBuilder;
import org.apache.camel.spi.Metadata;

/**
 * Sends the message to a static endpoint
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "to")
@XmlAccessorType(XmlAccessType.FIELD)
public class ToDefinition extends SendDefinition<ToDefinition> {

    @XmlAttribute
    private String variableSend;
    @XmlAttribute
    private String variableReceive;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "org.apache.camel.ExchangePattern", enums = "InOnly,InOut")
    private String pattern;

    public ToDefinition() {
    }

    public ToDefinition(String uri) {
        this();
        setUri(uri);
    }

    public ToDefinition(Endpoint endpoint) {
        this();
        setEndpoint(endpoint);
    }

    public ToDefinition(EndpointProducerBuilder endpointDefinition) {
        this();
        setEndpointProducerBuilder(endpointDefinition);
    }

    public ToDefinition(String uri, ExchangePattern pattern) {
        this(uri);
        this.pattern = pattern.name();
    }

    public ToDefinition(Endpoint endpoint, ExchangePattern pattern) {
        this(endpoint);
        this.pattern = pattern.name();
    }

    public ToDefinition(EndpointProducerBuilder endpoint, ExchangePattern pattern) {
        this(endpoint);
        this.pattern = pattern.name();
    }

    @Override
    public String getShortName() {
        return "to";
    }

    @Override
    public String toString() {
        return "To[" + getLabel() + "]";
    }

    @Override
    public String getPattern() {
        return pattern;
    }

    /**
     * Sets the optional {@link ExchangePattern} used to invoke this endpoint
     */
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getVariableSend() {
        return variableSend;
    }

    /**
     * To use a variable as the source for the message body to send. This makes it handy to use variables for user data
     * and to easily control what data to use for sending and receiving.
     *
     * Important: When using send variable then the message body is taken from this variable instead of the current
     * {@link Message}, however the headers from the {@link Message} will still be used as well. In other words, the
     * variable is used instead of the message body, but everything else is as usual.
     */
    public void setVariableSend(String variableSend) {
        this.variableSend = variableSend;
    }

    public String getVariableReceive() {
        return variableReceive;
    }

    /**
     * To use a variable to store the received message body (only body, not headers). This is handy for easy access to
     * the received message body via variables.
     *
     * Important: When using receive variable then the received body is stored only in this variable and <b>not</b> on
     * the current {@link org.apache.camel.Message}.
     */
    public void setVariableReceive(String variableReceive) {
        this.variableReceive = variableReceive;
    }
}
