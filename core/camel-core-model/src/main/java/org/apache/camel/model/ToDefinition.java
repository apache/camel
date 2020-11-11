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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.EndpointProducerBuilder;
import org.apache.camel.spi.Metadata;

/**
 * Sends the message to a static endpoint
 */
@Metadata(label = "eip,endpoint,routing")
@XmlRootElement(name = "to")
@XmlAccessorType(XmlAccessType.FIELD)
public class ToDefinition extends SendDefinition<ToDefinition> {
    @XmlAttribute
    @Metadata(javaType = "org.apache.camel.ExchangePattern", enums = "InOnly,InOut,InOptionalOut")
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

}
