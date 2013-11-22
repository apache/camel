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
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * Represents a timer endpoint that can generate periodic inbound exchanges triggered by a timer.
 *
 * @version 
 */
@ManagedResource(description = "Managed TimerEndpoint")
@UriEndpoint(scheme = "timer", consumerClass = TimerConsumer.class)
public class TimerEndpoint extends DefaultEndpoint implements MultipleConsumersSupport {
    @UriParam
    private String timerName;
    @UriParam
    private Date time;
    @UriParam
    private long period = 1000;
    @UriParam
    private long delay = 1000;
    @UriParam
    private boolean fixedRate;
    @UriParam
    private boolean daemon = true;
    @UriParam
    private Timer timer;
    @UriParam
    private long repeatCount;

    public TimerEndpoint() {
    }

    public TimerEndpoint(String uri, Component component, String timerName) {
        super(uri, component);
        this.timerName = timerName;
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
    protected void doStart() throws Exception {
        super.doStart();
        // do nothing, the timer will be set when the first consumer will request it
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
        if (timerName == null) {
            timerName = getEndpointUri();
        }
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

    @ManagedAttribute(description = "Singleton")
    public boolean isSingleton() {
        return true;
    }

    public synchronized Timer getTimer() {
        if (timer == null) {
            TimerComponent tc = (TimerComponent)getComponent();
            timer = tc.getTimer(this);
        }
        return timer;
    }

    public synchronized void setTimer(Timer timer) {
        this.timer = timer;
    }

    @ManagedAttribute(description = "Camel id")
    public String getCamelId() {
        return this.getCamelContext().getName();
    }

    @ManagedAttribute(description = "Camel ManagementName")
    public String getCamelManagementName() {
        return this.getCamelContext().getManagementName();
    }

    @ManagedAttribute(description = "Endpoint Uri")
    public String getEndpointUri() {
        return super.getEndpointUri();
    }

    @ManagedAttribute(description = "Endpoint State")
    public String getState() {
        return getStatus().name();
    }
}
