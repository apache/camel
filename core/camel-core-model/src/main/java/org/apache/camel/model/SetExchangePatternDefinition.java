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

import org.apache.camel.ExchangePattern;
import org.apache.camel.spi.Metadata;

/**
 * Sets the exchange pattern on the message exchange
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "setExchangePattern")
@XmlAccessorType(XmlAccessType.FIELD)
public class SetExchangePatternDefinition extends NoOutputDefinition<SetExchangePatternDefinition> {

    @XmlAttribute(required = true)
    @Metadata(required = true, javaType = "org.apache.camel.ExchangePattern", enums = "InOnly,InOut")
    private String pattern;

    public SetExchangePatternDefinition() {
    }

    public SetExchangePatternDefinition(ExchangePattern pattern) {
        this(pattern.name());
    }

    public SetExchangePatternDefinition(String pattern) {
        this.pattern = pattern;
    }

    public SetExchangePatternDefinition pattern(ExchangePattern pattern) {
        return pattern(pattern.name());
    }

    public SetExchangePatternDefinition pattern(String pattern) {
        setPattern(pattern);
        return this;
    }

    public String getPattern() {
        return pattern;
    }

    /**
     * Sets the new exchange pattern of the Exchange to be used from this point forward
     */
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public String toString() {
        return "setExchangePattern[pattern: " + pattern + "]";
    }

    @Override
    public String getShortName() {
        return "setExchangePattern";
    }

    @Override
    public String getLabel() {
        return "setExchangePattern[" + pattern + "]";
    }

}
