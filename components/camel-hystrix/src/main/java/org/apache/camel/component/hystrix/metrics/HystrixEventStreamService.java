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
package org.apache.camel.component.hystrix.metrics;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;

import com.netflix.hystrix.contrib.metrics.eventstream.HystrixMetricsPoller;
import org.apache.camel.StaticService;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.hystrix.metrics.servlet.HystrixEventStreamServlet;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To gather hystrix metrics and offer the metrics over JMX and Java APIs.
 * <p/>
 * If you want to expose the metrics over HTTP then you can use the {@link HystrixEventStreamServlet} servlet which
 * provides such functionality.
 */
@ManagedResource(description = "Managed Hystrix EventStreamService")
public class HystrixEventStreamService extends ServiceSupport implements StaticService, HystrixMetricsPoller.MetricsAsJsonPollerListener {

    public static final int METRICS_QUEUE_SIZE = 1000;

    private static final Logger LOG = LoggerFactory.getLogger(HystrixEventStreamService.class);
    private int delay = 500;
    private int queueSize = METRICS_QUEUE_SIZE;
    private HystrixMetricsPoller poller;
    // use a queue with a upper limit to avoid storing too many metrics
    private Queue<String> queue;


    public int getDelay() {
        return delay;
    }

    /**
     * Sets the delay in millis how often the poller runs
     */
    public void setDelay(int delay) {
        this.delay = delay;
    }

    public int getQueueSize() {
        return queueSize;
    }

    /**
     * Sets the queue size for how many metrics collected are stored in-memory in a backlog
     */
    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    /**
     * Return a stream of the JSon metrics.
     */
    public Stream<String> streamMetrics() {
        if (queue != null) {
            return queue.stream();
        } else {
            return null;
        }
    }

    @ManagedOperation(description = "Returns the oldest metrics as JSon format")
    public String oldestMetricsAsJSon() {
        if (queue != null) {
            return queue.peek();
        } else {
            return null;
        }
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
        LOG.info("Starting HystrixMetricsPoller with delay: {} and queue size: {}", delay, queueSize);
        queue = new LinkedBlockingQueue<>(queueSize);
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

        // ensure there is space on the queue by polling until at least single slot is free
        int drain = queue.size() - queueSize + 1;
        if (drain > 0) {
            LOG.debug("Draining queue to make room: {}", drain);
            for (int i = 0; i < drain; i++) {
                queue.poll();
            }
        }

        queue.add(json);
    }
}
