/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.hystrix.metrics;

import com.netflix.hystrix.contrib.metrics.eventstream.HystrixMetricsPoller;
import org.apache.camel.StaticService;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.support.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Managed Hystrix EventStreamService")
public class HystrixEventStreamService extends ServiceSupport implements StaticService, HystrixMetricsPoller.MetricsAsJsonPollerListener {

    // TODO: need for command and thread pool
    // or use some queue to store in backlog

    private static final Logger LOG = LoggerFactory.getLogger(HystrixEventStreamService.class);
    private int delay = 500;
    private HystrixMetricsPoller poller;
    private transient String latest;

    public int getDelay() {
        return delay;
    }

    /**
     * Sets the delay in millis how often the poller runs
     */
    public void setDelay(int delay) {
        this.delay = delay;
    }

    @ManagedOperation(description = "Returns the latest metrics as JSon format")
    public String latestMetricsAsJSon() {
        return latest;
    }

    @ManagedOperation(description = "Starts the metrics poller")
    public void startPoller() {
        poller.start();
    }

    @ManagedOperation(description = "Pauses the metrics poller")
    public void pausePoller() {
        poller.pause();
    }

    @ManagedAttribute(description = "Is the metrics poller running")
    public boolean isPollerRunning() {
        return poller.isRunning();
    }

    @ManagedAttribute(description = "The delay in millis the poller is running")
    public int getPollerDelay() {
        return delay;
    }

    @Override
    protected void doStart() throws Exception {
        LOG.info("Starting HystrixMetricsPoller with delay: {}", delay);
        poller = new HystrixMetricsPoller(this, delay);
        poller.start();
    }

    @Override
    protected void doStop() throws Exception {
        if (poller != null) {
            LOG.info("Shutting down HystrixMetricsPoller");
            poller.shutdown();
        }
    }

    @Override
    public void handleJsonMetric(String json) {
        LOG.debug("handleJsonMetric: {}", json);
        this.latest = json;
    }
}
