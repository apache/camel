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

import java.util.concurrent.TimeUnit;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.camel.Processor;
import org.apache.camel.builder.xml.TimeUnitAdapter;
import org.apache.camel.processor.SamplingThrottler;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;sample/&gt; element
 *
 * @version 
 */
@XmlRootElement(name = "sample")
@XmlAccessorType(XmlAccessType.FIELD)
public class SamplingDefinition extends OutputDefinition<SamplingDefinition> {

    // use Long to let it be optional in JAXB so when using XML the default is 1 second

    // TODO: Camel 3.0 Should extend NoOutputDefinition

    @XmlAttribute
    private Long samplePeriod;

    @XmlAttribute
    private Long messageFrequency;

    @XmlAttribute
    @XmlJavaTypeAdapter(TimeUnitAdapter.class)
    private TimeUnit units;

    public SamplingDefinition() {
    }

    public SamplingDefinition(long samplePeriod, TimeUnit units) {
        this.samplePeriod = samplePeriod;
        this.units = units;
    }

    public SamplingDefinition(long messageFrequency) {
        this.messageFrequency = messageFrequency;
    }
    
    @Override
    public String toString() {
        if (messageFrequency != null) {
            return "Sample[1 Exchange per " + getMessageFrequency() + " messages received -> " + getOutputs() + "]";
        } else {
            TimeUnit tu = getUnits() != null ? getUnits() : TimeUnit.SECONDS;
            return "Sample[1 Exchange per " + getSamplePeriod() + " " + tu.toString().toLowerCase() + " -> " + getOutputs() + "]";
        }
    }

    @Override
    public String getShortName() {
        return "sample";
    }

    @Override
    public String getLabel() {
        if (messageFrequency != null) {
            return "sample[1 Exchange per " + getMessageFrequency() + " messages received]";
        } else {
            TimeUnit tu = getUnits() != null ? getUnits() : TimeUnit.SECONDS;
            return "sample[1 Exchange per " + getSamplePeriod() + " " + tu.name().toLowerCase() + "]";
        }
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = this.createChildProcessor(routeContext, true);
        
        if (messageFrequency != null) {
            return new SamplingThrottler(childProcessor, messageFrequency);
        } else {
            // should default be 1 sample period
            long time = getSamplePeriod() != null ? getSamplePeriod() : 1L;
            // should default be in seconds
            TimeUnit tu = getUnits() != null ? getUnits() : TimeUnit.SECONDS;
            return new SamplingThrottler(childProcessor, time, tu);
        }
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the sample message count which only a single {@link org.apache.camel.Exchange} will pass through after this many received.
     *
     * @param messageFrequency 
     * @return the builder
     */
    public SamplingDefinition sampleMessageFrequency(long messageFrequency) {
        setMessageFrequency(messageFrequency);
        return this;
    }
    
    /**
     * Sets the sample period during which only a single {@link org.apache.camel.Exchange} will pass through.
     *
     * @param samplePeriod the period
     * @return the builder
     */
    public SamplingDefinition samplePeriod(long samplePeriod) {
        setSamplePeriod(samplePeriod);
        return this;
    }

    /**
     * Sets the time units for the sample period, defaulting to seconds.
     *
     * @param units the time unit of the sample period.
     * @return the builder
     */
    public SamplingDefinition timeUnits(TimeUnit units) {
        setUnits(units);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    public Long getSamplePeriod() {
        return samplePeriod;
    }

    public void setSamplePeriod(Long samplePeriod) {
        this.samplePeriod = samplePeriod;
    }

    public Long getMessageFrequency() {
        return messageFrequency;
    }

    public void setMessageFrequency(Long messageFrequency) {
        this.messageFrequency = messageFrequency;
    }
    
    public void setUnits(String units) {
        this.units = TimeUnit.valueOf(units);
    }

    public void setUnits(TimeUnit units) {
        this.units = units;
    }

    public TimeUnit getUnits() {
        return units;
    }
}
