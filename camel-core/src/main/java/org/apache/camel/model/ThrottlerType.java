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

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Processor;
import org.apache.camel.processor.Throttler;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;throttler/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "throttler")
@XmlAccessorType(XmlAccessType.FIELD)
public class ThrottlerType extends ProcessorType<ProcessorType> {
    @XmlAttribute
    private Long maximumRequestsPerPeriod;
    @XmlAttribute
    private long timePeriodMillis = 1000;
    @XmlElementRef
    private List<ProcessorType<?>> outputs = new ArrayList<ProcessorType<?>>();

    public ThrottlerType() {
    }

    public ThrottlerType(long maximumRequestsPerPeriod) {
        this.maximumRequestsPerPeriod = maximumRequestsPerPeriod;
    }

    @Override
    public String toString() {
        return "Throttler[" + getMaximumRequestsPerPeriod() + " request per " + getTimePeriodMillis()
               + " millis -> " + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "throttler";
    }

    @Override
    public String getLabel() {
        return "" + getMaximumRequestsPerPeriod() + " per " + getTimePeriodMillis() + " (ms)";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = routeContext.createProcessor(this);
        return new Throttler(childProcessor, maximumRequestsPerPeriod, timePeriodMillis);
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the time period during which the maximum request count is valid for
     */
    public ThrottlerType timePeriodMillis(long timePeriodMillis) {
        this.timePeriodMillis = timePeriodMillis;
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    public Long getMaximumRequestsPerPeriod() {
        return maximumRequestsPerPeriod;
    }

    public void setMaximumRequestsPerPeriod(Long maximumRequestsPerPeriod) {
        this.maximumRequestsPerPeriod = maximumRequestsPerPeriod;
    }

    public long getTimePeriodMillis() {
        return timePeriodMillis;
    }

    public void setTimePeriodMillis(long timePeriodMillis) {
        this.timePeriodMillis = timePeriodMillis;
    }

    public List<ProcessorType<?>> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorType<?>> outputs) {
        this.outputs = outputs;
    }
}
