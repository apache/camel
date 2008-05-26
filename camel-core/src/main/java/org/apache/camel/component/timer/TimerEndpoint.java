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

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultEndpoint;

/**
 * Represents a timer endpoint that can generate periodic inbound PojoExchanges.
 *
 * @version $Revision$
 */
public class TimerEndpoint extends DefaultEndpoint<Exchange> {
    private String timerName;
    private Date time;
    private long period = 1000;
    private long delay;
    private boolean fixedRate;
    private boolean daemon = true;
    private Timer timer;

    public TimerEndpoint(String fullURI, TimerComponent component, String timerName) {
        super(fullURI, component);
        this.timer = component.getTimer(this);
        this.timerName = timerName;
    }

    public TimerEndpoint(String endpointUri, Timer timer) {
        this(endpointUri);
        this.timer = timer;
    }

    public TimerEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public Producer<Exchange> createProducer() throws Exception {
        throw new RuntimeCamelException("Cannot produce to a TimerEndpoint: " + getEndpointUri());
    }

    public Consumer<Exchange> createConsumer(Processor processor) throws Exception {
        return new TimerConsumer(this, processor);
    }

    public String getTimerName() {
        if (timerName == null) {
            timerName = getEndpointUri();
        }
        return timerName;
    }

    public void setTimerName(String timerName) {
        this.timerName = timerName;
    }

    public boolean isDaemon() {
        return daemon;
    }

    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public boolean isFixedRate() {
        return fixedRate;
    }

    public void setFixedRate(boolean fixedRate) {
        this.fixedRate = fixedRate;
    }

    public long getPeriod() {
        return period;
    }

    public void setPeriod(long period) {
        this.period = period;
    }

    public Date getTime() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    public boolean isSingleton() {
        return true;
    }

    public Timer getTimer() {
        if (timer == null) {
            timer = new Timer();
        }
        return timer;
    }

    public void setTimer(Timer timer) {
        this.timer = timer;
    }
}
