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
package org.apache.camel.management.mbean;

import org.apache.camel.component.timer.TimerEndpoint;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * @version $Revision$
 */

@ManagedResource(description = "Managed Timer Endpoint")
public class ManagedTimerEndpoint extends ManagedEndpoint {

    private TimerEndpoint endpoint;

    public ManagedTimerEndpoint(TimerEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public TimerEndpoint getEndpoint() {
        return endpoint;
    }

    @ManagedAttribute(description = "Timer Name")
    public String getTimerName() {
        return getEndpoint().getTimerName();
    }

    @ManagedAttribute(description = "Timer Name")
    public void setTimerName(String timerName) {
        getEndpoint().setTimerName(timerName);
    }

    @ManagedAttribute(description = "Timer Daemon")
    public boolean getDaemon() {
        return getEndpoint().isDaemon();
    }

    @ManagedAttribute(description = "Timer Daemon")
    public void setDaemon(boolean daemon) {
        getEndpoint().setDaemon(daemon);
    }

    @ManagedAttribute(description = "Timer Delay")
    public long getDelay() {
        return getEndpoint().getDelay();
    }

    @ManagedAttribute(description = "Timer Delay")
    public void setDelay(long delay) {
        getEndpoint().setDelay(delay);
    }

    @ManagedAttribute(description = "Timer FixedRate")
    public boolean getFixedRate() {
        return getEndpoint().isFixedRate();
    }

    @ManagedAttribute(description = "Timer FixedRate")
    public void setFixedRate(boolean fixedRate) {
        getEndpoint().setFixedRate(fixedRate);
    }

    @ManagedAttribute(description = "Timer Period")
    public long getPeriod() {
        return getEndpoint().getPeriod();
    }

    @ManagedAttribute(description = "Timer Period")
    public void setPeriod(long period) {
        getEndpoint().setPeriod(period);
    }

}
