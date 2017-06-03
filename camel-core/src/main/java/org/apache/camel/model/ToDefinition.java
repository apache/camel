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
package org.apache.camel.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.spi.Metadata;

/**
 * Sends the message to a static endpoint
 *
 * @version 
 */
@Metadata(label = "eip,endpoint,routing")
@XmlRootElement(name = "to")
@XmlAccessorType(XmlAccessType.FIELD)
public class ToDefinition extends SendDefinition<ToDefinition> {
    @XmlAttribute
    private ExchangePattern pattern;

    public ToDefinition() {
    }

    public ToDefinition(String uri) {
        setUri(uri);
    }

    public ToDefinition(Endpoint endpoint) {
        setEndpoint(endpoint);
    }

    public ToDefinition(String uri, ExchangePattern pattern) {
        this(uri);
        this.pattern = pattern;
    }

    public ToDefinition(Endpoint endpoint, ExchangePattern pattern) {
        this(endpoint);
        this.pattern = pattern;
    }

    @Override
    public String toString() {
        return "To[" + getLabel() + "]";
    }

    @Override
    public ExchangePattern getPattern() {
        return pattern;
    }

    /**
     * Sets the optional {@link ExchangePattern} used to invoke this endpoint
     */
    public void setPattern(ExchangePattern pattern) {
        this.pattern = pattern;
    }

    /**
     * Sets the optional {@link ExchangePattern} used to invoke this endpoint
     *
     * @deprecated will be removed in the near future. Instead use {@link ProcessorDefinition#inOnly()}
     * or {@link ProcessorDefinition#inOut()}
     */
    @Deprecated
    public ToDefinition pattern(ExchangePattern pattern) {
        setPattern(pattern);
        return this;
    }

}
