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
package org.apache.camel.component.timer;

import java.util.Date;
import java.util.Timer;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Generate messages in specified intervals using <code>java.util.Timer</code>.
 *
 * This component is similar to the scheduler component, but has much less functionality.
 */
@ManagedResource(description = "Managed TimerEndpoint")
@UriEndpoint(firstVersion = "1.0.0", scheme = "timer", title = "Timer", syntax = "timer:timerName", consumerOnly = true,
             category = { Category.CORE, Category.SCHEDULING }, headersClass = TimerConstants.class)
public class TimerEndpoint extends DefaultEndpoint implements MultipleConsumersSupport {
    @UriPath
    @Metadata(required = true)
    private String timerName;
    @UriParam(defaultValue = "1000", description = "If greater than 0, generate periodic events every period.",
              javaType = "java.time.Duration")
    private long period = 1000;
    @UriParam(defaultValue = "1000", description = "Delay before first event is triggered.", javaType = "java.time.Duration")
    private long delay = 1000;
    @UriParam
    private long repeatCount;
    @UriParam
    private boolean fixedRate;
    @UriParam(defaultValue = "true", label = "advanced")
    private boolean daemon = true;
    @UriParam(label = "advanced")
    private Date time;
    @UriParam(label = "advanced")
    private String pattern;
    @UriParam(label = "advanced")
    private Timer timer;
    @UriParam
    private boolean includeMetadata;
    @UriParam(defaultValue = "false", label = "advanced",
              description = "Sets whether synchronous processing should be strictly used")
    private boolean synchronous;

    public TimerEndpoint() {
    }

    public TimerEndpoint(String uri, Component component, String timerName) {
        super(uri, component);
        this.timerName = timerName;
    }

    protected TimerEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public TimerComponent getComponent() {
        return (TimerComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new RuntimeCamelException("Cannot produce to a TimerEndpoint: " + getEndpointUri());
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer answer = new TimerConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        if (timerName == null) {
            timerName = getEndpointUri();
        }
        // do nothing in regards to setTimer, the timer will be set when the first consumer will request it
    }

    @Override
    protected void doStop() throws Exception {
        setTimer(null);
        super.doStop();
    }

    @Override
    @ManagedAttribute
    public boolean isMultipleConsumersSupported() {
        return true;
    }

    @ManagedAttribute(description = "Timer Name")
    public String getTimerName() {
        return timerName;
    }

    /**
     * The name of the timer
     */
    @ManagedAttribute(description = "Timer Name")
    public void setTimerName(String timerName) {
        this.timerName = timerName;
    }

    @ManagedAttribute(description = "Timer Daemon")
    public boolean isDaemon() {
        return daemon;
    }

    /**
     * Specifies whether or not the thread associated with the timer endpoint runs as a daemon.
     * <p/>
     * The default value is true.
     */
    @ManagedAttribute(description = "Timer Daemon")
    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    @ManagedAttribute(description = "Timer Delay")
    public long getDelay() {
        return delay;
    }

    /**
     * The number of milliseconds to wait before the first event is generated. Should not be used in conjunction with
     * the time option.
     * <p/>
     * The default value is 1000.
     */
    @ManagedAttribute(description = "Timer Delay")
    public void setDelay(long delay) {
        this.delay = delay;
    }

    @ManagedAttribute(description = "Timer FixedRate")
    public boolean isFixedRate() {
        return fixedRate;
    }

    /**
     * Events take place at approximately regular intervals, separated by the specified period.
     */
    @ManagedAttribute(description = "Timer FixedRate")
    public void setFixedRate(boolean fixedRate) {
        this.fixedRate = fixedRate;
    }

    @ManagedAttribute(description = "Timer Period")
    public long getPeriod() {
        return period;
    }

    /**
     * If greater than 0, generate periodic events every period milliseconds.
     * <p/>
     * The default value is 1000.
     */
    @ManagedAttribute(description = "Timer Period")
    public void setPeriod(long period) {
        this.period = period;
    }

    @ManagedAttribute(description = "Repeat Count")
    public long getRepeatCount() {
        return repeatCount;
    }

    /**
     * Specifies a maximum limit of number of fires. So if you set it to 1, the timer will only fire once. If you set it
     * to 5, it will only fire five times. A value of zero or negative means fire forever.
     */
    @ManagedAttribute(description = "Repeat Count")
    public void setRepeatCount(long repeatCount) {
        this.repeatCount = repeatCount;
    }

    public Date getTime() {
        return time;
    }

    /**
     * A java.util.Date the first event should be generated. If using the URI, the pattern expected is: yyyy-MM-dd
     * HH:mm:ss or yyyy-MM-dd'T'HH:mm:ss.
     */
    public void setTime(Date time) {
        this.time = time;
    }

    public String getPattern() {
        return pattern;
    }

    /**
     * Allows you to specify a custom Date pattern to use for setting the time option using URI syntax.
     */
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public Timer getTimer() {
        return timer;
    }

    /**
     * To use a custom {@link Timer}
     */
    public void setTimer(Timer timer) {
        this.timer = timer;
    }

    public Timer getTimer(TimerConsumer consumer) {
        if (timer != null) {
            // use custom timer
            return timer;
        }
        return getComponent().getTimer(consumer);
    }

    @ManagedAttribute(description = "Include metadata")
    public boolean isIncludeMetadata() {
        return includeMetadata;
    }

    /**
     * Whether to include metadata in the exchange such as fired time, timer name, timer count etc.
     */
    @ManagedAttribute(description = "Include metadata")
    public void setIncludeMetadata(boolean includeMetadata) {
        this.includeMetadata = includeMetadata;
    }

    public boolean isSynchronous() {
        return synchronous;
    }

    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    public void removeTimer(TimerConsumer consumer) {
        if (timer == null) {
            // only remove timer if we are not using a custom timer
            getComponent().removeTimer(consumer);
        }
    }

}
