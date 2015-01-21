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
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.processor.ExchangePatternProcessor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;

/**
 * Sets the exchange pattern on the message exchange
 *
 * @version 
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "setExchangePattern")
@XmlAccessorType(XmlAccessType.FIELD)
public class SetExchangePatternDefinition extends NoOutputDefinition<SetExchangePatternDefinition> {
    @XmlAttribute(required = true)
    private ExchangePattern pattern;
    @XmlTransient
    private ExchangePatternProcessor processor;
    
    public SetExchangePatternDefinition() {
    }

    public SetExchangePatternDefinition(ExchangePattern pattern) {
        this.pattern = pattern;
    }

    public SetExchangePatternDefinition pattern(ExchangePattern pattern) {
        setPattern(pattern);
        return this;
    }

    public ExchangePattern getPattern() {
        return pattern;
    }

    /**
     * Sets the new exchange pattern of the Exchange to be used from this point forward
     */
    public void setPattern(ExchangePattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public String toString() {
        return "setExchangePattern[pattern: " + pattern + "]";
    }

    @Override
    public String getLabel() {
        return "setExchangePattern[" + pattern + "]";
    }
   
    @Override
    public Processor createProcessor(RouteContext routeContext) {
        if (processor == null) {
            processor = new ExchangePatternProcessor(getPattern());
        }
        return processor;
    }

}
