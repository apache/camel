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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Processor;
import org.apache.camel.processor.Throttler;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;

/**
 * Represents an XML &lt;throttle/&gt; element
 *
 * @version 
 */
@XmlRootElement(name = "throttle")
@XmlAccessorType(XmlAccessType.FIELD)
public class ThrottleDefinition extends OutputDefinition<ThrottleDefinition> implements ExecutorServiceAwareDefinition<ThrottleDefinition> {
    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute
    private String executorServiceRef;
    @XmlAttribute
    private Long maximumRequestsPerPeriod;
    @XmlAttribute
    private Long timePeriodMillis;
    @XmlAttribute
    private Boolean asyncDelayed;
    @XmlAttribute
    private Boolean callerRunsWhenRejected;

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

        ScheduledExecutorService scheduled = null;
        if (getAsyncDelayed() != null && getAsyncDelayed()) {
            scheduled = ExecutorServiceHelper.getConfiguredScheduledExecutorService(routeContext, "Throttle", this);
            if (scheduled == null) {
                scheduled = routeContext.getCamelContext().getExecutorServiceStrategy().newScheduledThreadPool(this, "Throttle");
            }
        }

        // should be default 1000 millis
        long period = getTimePeriodMillis() != null ? getTimePeriodMillis() : 1000L;
        Throttler answer = new Throttler(childProcessor, getMaximumRequestsPerPeriod(), period, scheduled);
        if (getAsyncDelayed() != null) {
            answer.setAsyncDelayed(getAsyncDelayed());
        }
        if (getCallerRunsWhenRejected() == null) {
            // should be true by default
            answer.setCallerRunsWhenRejected(true);
        } else {
            answer.setCallerRunsWhenRejected(getCallerRunsWhenRejected());
        }
        return answer;
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

    /**
     * Whether or not the caller should run the task when it was rejected by the thread pool.
     * <p/>
     * Is by default <tt>true</tt>
     *
     * @param callerRunsWhenRejected whether or not the caller should run
     * @return the builder
     */
    public ThrottleDefinition callerRunsWhenRejected(boolean callerRunsWhenRejected) {
        setCallerRunsWhenRejected(callerRunsWhenRejected);
        return this;
    }

    /**
     * Enables asynchronous delay which means the thread will <b>noy</b> block while delaying.
     *
     * @return the builder
     */
    public ThrottleDefinition asyncDelayed() {
        setAsyncDelayed(true);
        return this;
    }

    public ThrottleDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    public ThrottleDefinition executorServiceRef(String executorServiceRef) {
        setExecutorServiceRef(executorServiceRef);
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

    public Long getTimePeriodMillis() {
        return timePeriodMillis;
    }

    public void setTimePeriodMillis(Long timePeriodMillis) {
        this.timePeriodMillis = timePeriodMillis;
    }

    public Boolean getAsyncDelayed() {
        return asyncDelayed;
    }

    public void setAsyncDelayed(Boolean asyncDelayed) {
        this.asyncDelayed = asyncDelayed;
    }

    public Boolean getCallerRunsWhenRejected() {
        return callerRunsWhenRejected;
    }

    public void setCallerRunsWhenRejected(Boolean callerRunsWhenRejected) {
        this.callerRunsWhenRejected = callerRunsWhenRejected;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public String getExecutorServiceRef() {
        return executorServiceRef;
    }

    public void setExecutorServiceRef(String executorServiceRef) {
        this.executorServiceRef = executorServiceRef;
    }
}
