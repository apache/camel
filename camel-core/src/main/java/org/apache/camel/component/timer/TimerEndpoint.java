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
package org.apache.camel.component.timer;

import java.util.Date;
import java.util.Timer;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * The timer component is used for generating message exchanges when a timer fires.
 *
 * This component is similar to the scheduler component, but has much less functionality.
 */
@ManagedResource(description = "Managed TimerEndpoint")
@UriEndpoint(firstVersion = "1.0.0", scheme = "timer", title = "Timer", syntax = "timer:timerName", consumerOnly = true, consumerClass = TimerConsumer.class, label = "core,scheduling")
public class TimerEndpoint extends DefaultEndpoint implements MultipleConsumersSupport {
    @UriPath @Metadata(required = "true")
    private String timerName;
    @UriParam(defaultValue = "1000", description = "If greater than 0, generate periodic events every period milliseconds."
            + " You can also specify time values using units, such as 60s (60 seconds), 5m30s (5 minutes and 30 seconds), and 1h (1 hour).")
    private long period = 1000;
    @UriParam(defaultValue = "1000", description = "Miliseconds before first event is triggered."
            + " You can also specify time values using units, such as 60s (60 seconds), 5m30s (5 minutes and 30 seconds), and 1h (1 hour).")
    private long delay = 1000;
    @UriParam(defaultValue = "0")
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

    public Producer createProducer() throws Exception {
        throw new RuntimeCamelException("Cannot produce to a TimerEndpoint: " + getEndpointUri());
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer answer = new TimerConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
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
     * The number of milliseconds to wait before the first event is generated. Should not be used in conjunction with the time option.
     * <p/>
     * The default value is 1000.
     * You can also specify time values using units, such as 60s (60 seconds), 5m30s (5 minutes and 30 seconds), and 1h (1 hour).
     * @see <a href="http://camel.apache.org/how-do-i-specify-time-period-in-a-human-friendly-syntax.html">human friendly syntax</a>
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
     * You can also specify time values using units, such as 60s (60 seconds), 5m30s (5 minutes and 30 seconds), and 1h (1 hour).
     * @see <a href="http://camel.apache.org/how-do-i-specify-time-period-in-a-human-friendly-syntax.html">human friendly syntax</a>
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
     * Specifies a maximum limit of number of fires.
     * So if you set it to 1, the timer will only fire once.
     * If you set it to 5, it will only fire five times.
     * A value of zero or negative means fire forever.
     */
    @ManagedAttribute(description = "Repeat Count")
    public void setRepeatCount(long repeatCount) {
        this.repeatCount = repeatCount;
    }

    public Date getTime() {
        return time;
    }

    /**
     * A java.util.Date the first event should be generated. If using the URI, the pattern expected is: yyyy-MM-dd HH:mm:ss or yyyy-MM-dd'T'HH:mm:ss.
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

    public Timer getTimer(TimerConsumer consumer) {
        if (timer != null) {
            // use custom timer
            return timer;
        }
        return getComponent().getTimer(consumer);
    }

    /**
     * To use a custom {@link Timer}
     */
    public void setTimer(Timer timer) {
        this.timer = timer;
    }

    public void removeTimer(TimerConsumer consumer) {
        if (timer == null) {
            // only remove timer if we are not using a custom timer
            getComponent().removeTimer(consumer);
        }
    }

}
