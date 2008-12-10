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

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.processor.ExchangePatternProcessor;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;SetExchangePattern/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "setExchangePattern")
@XmlAccessorType(XmlAccessType.FIELD)
public class ExchangePatternType extends OutputType {    
    @XmlAttribute(name = "pattern", required = true)
    private String pattern;
    @XmlTransient
    private ExchangePattern exchangePattern;
    @XmlTransient
    private ExchangePatternProcessor processor;
    
    public ExchangePatternType() {
    }

    public ExchangePatternType(ExchangePattern ep) {
        exchangePattern = ep;
        pattern = exchangePattern.toString();
    }
    
    public void setPattern(String pattern) {
        this.pattern = pattern;        
        exchangePattern = ExchangePattern.asEnum(pattern);        
    }
    
    public String getPattern() {
        return pattern;
    }

    public ExchangePattern getExchangePattern() {
        if (exchangePattern == null) {
            if (pattern != null) {
                exchangePattern = ExchangePattern.asEnum(pattern);
            } else {
                exchangePattern = ExchangePattern.InOnly;
            }
        }
        return exchangePattern;        
    }

    @Override
    public String getShortName() {
        return "setExchangePattern";
    }

    @Override
    public String toString() {
        return "setExchangePattern["
                + "exchangePattern: " + exchangePattern
                + "]";
    }

    @Override
    public String getLabel() {
        return "exchangePattern: " + exchangePattern;
    }
   
    @Override
    public Processor createProcessor(RouteContext routeContext) {
        if (processor == null) {
            processor = new ExchangePatternProcessor(getExchangePattern());
        }
        return processor;
    }

}
