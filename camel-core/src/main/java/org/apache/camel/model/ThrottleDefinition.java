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
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Processor;
import org.apache.camel.processor.Throttler;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;throttle/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "throttle")
@XmlAccessorType(XmlAccessType.FIELD)
public class ThrottleDefinition extends OutputDefinition<ThrottleDefinition> {
    @XmlAttribute
    private Long maximumRequestsPerPeriod;
    @XmlAttribute
    private long timePeriodMillis = 1000;

    public ThrottleDefinition() {
    }

    public ThrottleDefinition(long maximumRequestsPerPeriod) {
        this.maximumRequestsPerPeriod = maximumRequestsPerPeriod;
    }

    @Override
    public String toString() {
        return "Throttle[" + getMaximumRequestsPerPeriod() + " request per " + getTimePeriodMillis()
               + " millis -> " + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "throttle";
    }

    @Override
    public String getLabel() {
        return "" + getMaximumRequestsPerPeriod() + " per " + getTimePeriodMillis() + " (ms)";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = this.createChildProcessor(routeContext, true);
        return new Throttler(childProcessor, maximumRequestsPerPeriod, timePeriodMillis);
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the time period during which the maximum request count is valid for
     *
     * @param timePeriodMillis  period in millis
     * @return the builder
     */
    public ThrottleDefinition timePeriodMillis(long timePeriodMillis) {
        setTimePeriodMillis(timePeriodMillis);
        return this;
    }
    
    /**
     * Sets the time period during which the maximum request count per period
     *
     * @param maximumRequestsPerPeriod  the maximum request count number per time period
     * @return the builder
     */
    public ThrottleDefinition maximumRequestsPerPeriod(Long maximumRequestsPerPeriod) {
        setMaximumRequestsPerPeriod(maximumRequestsPerPeriod);
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

}
