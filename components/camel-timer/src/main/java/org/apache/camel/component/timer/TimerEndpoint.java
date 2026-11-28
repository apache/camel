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
import org.apache.camel.LoggingLevel;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
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
             remote = false, internal = true, category = { Category.CORE, Category.SCHEDULING },
             headersClass = TimerConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.NULL, description = "No body: the message only starts the route"))
public class TimerEndpoint extends DefaultEndpoint implements MultipleConsumersSupport {
    @UriPath(description = "The name of the timer. The consumers with the same timer name and the same daemon option share"
                           + " one java.util.Timer, whose thread is named after the timer.")
    @Metadata(required = true)
    private String timerName;
    @UriParam(defaultValue = "1000", javaType = "java.time.Duration",
              description = "The time in milliseconds between two fires. A value of 0 or less fires only once, except"
                            + " with fixedRate, which needs a positive value.")
    private long period = 1000;
    @UriParam(defaultValue = "1000", javaType = "java.time.Duration",
              description = "The time in milliseconds to wait before the first fire. The time option replaces it. A"
                            + " negative value fires the messages one after the other as fast as possible on a thread of its"
                            + " own, without the timer, and ignores the period, time and fixedRate options.")
    private long delay = 1000;
    @UriParam
    private long repeatCount;
    @UriParam(description = "Whether to schedule the fires at a fixed rate instead of with a fixed delay. At a fixed rate"
                            + " each fire is planned from the time of the first fire, so the fires after a late one come in"
                            + " quick succession to catch up. With a fixed delay each fire is planned one period after the"
                            + " previous fire started.")
    private boolean fixedRate;
    @UriParam
    private boolean includeMetadata;
    @UriParam(defaultValue = "TRACE", label = "consumer,scheduler",
              description = "The logging level of the log lines that the consumer writes when the timer task of a fire"
                            + " starts and completes. Not used when the delay is negative.")
    private LoggingLevel runLoggingLevel = LoggingLevel.TRACE;
    @UriParam(label = "advanced", defaultValue = "true")
    private boolean daemon = true;
    @UriParam(label = "advanced",
              description = "The date and time of the first fire, which replaces the delay option. In a URI the value is"
                            + " parsed in the default time zone of the JVM with the pattern yyyy-MM-dd'T'HH:mm:ss, or"
                            + " yyyy-MM-dd HH:mm:ss if it has no T, unless the pattern option is set.")
    private Date time;
    @UriParam(label = "advanced",
              description = "The java.text.SimpleDateFormat pattern to parse the time option with, instead of the default"
                            + " patterns.")
    private String pattern;
    @UriParam(label = "advanced",
              description = "A custom java.util.Timer to schedule the fires on, instead of the timer that the component"
                            + " shares between the consumers of the same timer name. The daemon option is not used, and Camel"
                            + " does not cancel this timer.")
    private Timer timer;
    @UriParam(label = "advanced",
              description = "Whether the consumer processes each exchange synchronously, so the firing thread waits until the"
                            + " route has completed it. If false, the next fire can start while an asynchronous step of the"
                            + " route still processes the previous exchange.")
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
    public boolean isRemote() {
        return false;
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
        // do nothing in regard to setTimer, the timer will be set when the first consumer requests it
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

    @ManagedAttribute(description = "Timer Name")
    public void setTimerName(String timerName) {
        this.timerName = timerName;
    }

    @ManagedAttribute(description = "Timer Daemon")
    public boolean isDaemon() {
        return daemon;
    }

    /**
     * Specifies whether the thread associated with the timer endpoint runs as a daemon.
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

    @ManagedAttribute(description = "Timer Delay")
    public void setDelay(long delay) {
        this.delay = delay;
    }

    @ManagedAttribute(description = "Timer FixedRate")
    public boolean isFixedRate() {
        return fixedRate;
    }

    @ManagedAttribute(description = "Timer FixedRate")
    public void setFixedRate(boolean fixedRate) {
        this.fixedRate = fixedRate;
    }

    @ManagedAttribute(description = "The consumer logs a start/complete log line when it polls. This option allows you to configure the logging level for that.")
    public LoggingLevel getRunLoggingLevel() {
        return runLoggingLevel;
    }

    @ManagedAttribute(description = "The consumer logs a start/complete log line when it polls. This option allows you to configure the logging level for that.")
    public void setRunLoggingLevel(LoggingLevel runLoggingLevel) {
        this.runLoggingLevel = runLoggingLevel;
    }

    @ManagedAttribute(description = "Timer Period")
    public long getPeriod() {
        return period;
    }

    @ManagedAttribute(description = "Timer Period")
    public void setPeriod(long period) {
        this.period = period;
    }

    @ManagedAttribute(description = "Repeat Count")
    public long getRepeatCount() {
        return repeatCount;
    }

    /**
     * Specifies a maximum limit for the number of fires. Therefore, if you set it to 1, the timer will only fire once.
     * If you set it to 5, it will only fire five times. A value of zero or negative means fire forever.
     */
    @ManagedAttribute(description = "Repeat Count")
    public void setRepeatCount(long repeatCount) {
        this.repeatCount = repeatCount;
    }

    public Date getTime() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public Timer getTimer() {
        return timer;
    }

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
     * Whether to include metadata in the exchange: the exchange properties CamelTimerCounter (the fire count, 1 on the
     * first fire), CamelTimerName, CamelTimerPeriod, CamelTimerFiredTime and CamelTimerTime (when the time option is
     * set), and the headers CamelTimerFiredTime and CamelMessageTimestamp.
     */
    @ManagedAttribute(description = "Include metadata")
    public void setIncludeMetadata(boolean includeMetadata) {
        this.includeMetadata = includeMetadata;
    }

    @ManagedAttribute(description = "Whether synchronous processing should be strictly used")
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
