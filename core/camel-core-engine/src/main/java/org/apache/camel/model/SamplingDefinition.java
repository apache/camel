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

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * Extract a sample of the messages passing through a route
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "sample")
@XmlAccessorType(XmlAccessType.FIELD)
public class SamplingDefinition extends NoOutputDefinition<SamplingDefinition> {

    // use Long to let it be optional in JAXB so when using XML the default is 1
    // second

    @XmlAttribute
    @Metadata(defaultValue = "1", javaType = "java.lang.Long")
    private String samplePeriod;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Long")
    private String messageFrequency;
    @XmlAttribute
    @Metadata(defaultValue = "SECONDS", enums = "NANOSECONDS,MICROSECONDS,MILLISECONDS,SECONDS,MINUTES,HOURS,DAYS",
              javaType = "java.util.concurrent.TimeUnit")
    private String units;

    public SamplingDefinition() {
    }

    public SamplingDefinition(long samplePeriod, TimeUnit units) {
        this.samplePeriod = Long.toString(samplePeriod);
        this.units = units.name();
    }

    public SamplingDefinition(long messageFrequency) {
        this.messageFrequency = Long.toString(messageFrequency);
    }

    @Override
    public String getShortName() {
        return "sample";
    }

    @Override
    public String toString() {
        return "Sample[" + description() + " -> " + getOutputs() + "]";
    }

    protected String description() {
        if (messageFrequency != null) {
            return "1 Exchange per " + getMessageFrequency() + " messages received";
        } else {
            String tu = getUnits() != null ? getUnits() : TimeUnit.SECONDS.name();
            return "1 Exchange per " + getSamplePeriod() + " " + tu.toLowerCase(Locale.ENGLISH);
        }
    }

    @Override
    public String getLabel() {
        return "sample[" + description() + "]";
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the sample message count which only a single
     * {@link org.apache.camel.Exchange} will pass through after this many
     * received.
     *
     * @param messageFrequency
     * @return the builder
     */
    public SamplingDefinition sampleMessageFrequency(long messageFrequency) {
        setMessageFrequency(messageFrequency);
        return this;
    }

    /**
     * Sets the sample period during which only a single
     * {@link org.apache.camel.Exchange} will pass through.
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

    public String getSamplePeriod() {
        return samplePeriod;
    }

    /**
     * Sets the sample period during which only a single Exchange will pass
     * through.
     */
    public void setSamplePeriod(String samplePeriod) {
        this.samplePeriod = samplePeriod;
    }

    public void setSamplePeriod(long samplePeriod) {
        this.samplePeriod = Long.toString(samplePeriod);
    }

    public String getMessageFrequency() {
        return messageFrequency;
    }

    /**
     * Sets the sample message count which only a single Exchange will pass
     * through after this many received.
     */
    public void setMessageFrequency(String messageFrequency) {
        this.messageFrequency = messageFrequency;
    }

    public void setMessageFrequency(long messageFrequency) {
        this.messageFrequency = Long.toString(messageFrequency);
    }

    /**
     * Sets the time units for the sample period, defaulting to seconds.
     */
    public void setUnits(String units) {
        this.units = units;
    }

    /**
     * Sets the time units for the sample period, defaulting to seconds.
     */
    public void setUnits(TimeUnit units) {
        this.units = units.name();
    }

    public String getUnits() {
        return units;
    }
}
