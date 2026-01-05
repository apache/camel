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
package org.apache.camel.component.file.cluster;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import org.apache.camel.CamelContext;
import org.apache.camel.support.cluster.AbstractCamelClusterService;
import org.apache.camel.util.ObjectHelper;

public class FileLockClusterService extends AbstractCamelClusterService<FileLockClusterView> {
    private String root;
    private long acquireLockDelay;
    private TimeUnit acquireLockDelayUnit;
    private long acquireLockInterval;
    private TimeUnit acquireLockIntervalUnit;
    private ScheduledExecutorService executor;
    private int heartbeatTimeoutMultiplier;
    private int clusterDataTaskMaxAttempts;
    private long clusterDataTaskTimeout;
    private TimeUnit clusterDataTaskTimeoutUnit;
    private ExecutorService clusterDataTaskExecutor;

    public FileLockClusterService() {
        this.acquireLockDelay = 1;
        this.acquireLockDelayUnit = TimeUnit.SECONDS;
        this.acquireLockInterval = 10;
        this.acquireLockIntervalUnit = TimeUnit.SECONDS;
        this.heartbeatTimeoutMultiplier = 5;
        this.clusterDataTaskMaxAttempts = 5;
        this.clusterDataTaskTimeout = 10;
        this.clusterDataTaskTimeoutUnit = TimeUnit.SECONDS;
    }

    @Override
    protected FileLockClusterView createView(String namespace) throws Exception {
        return new FileLockClusterView(this, namespace);
    }

    public String getRoot() {
        return root;
    }

    /**
     * Sets the root path.
     */
    public void setRoot(String root) {
        this.root = root;
    }

    public long getAcquireLockDelay() {
        return acquireLockDelay;
    }

    /**
     * The time to wait before starting to try to acquire the cluster lock. Note that if FileLockClusterService
     * determines no cluster members are running or cannot reliably determine the cluster state, the initial delay is
     * computed from the acquireLockInterval, default 1.
     */
    public void setAcquireLockDelay(long acquireLockDelay) {
        if (acquireLockDelay <= 0) {
            throw new IllegalArgumentException("acquireLockDelay must be greater than 0");
        }
        this.acquireLockDelay = acquireLockDelay;
    }

    public void setAcquireLockDelay(long pollDelay, TimeUnit pollDelayUnit) {
        setAcquireLockDelay(pollDelay);
        setAcquireLockDelayUnit(pollDelayUnit);
    }

    public TimeUnit getAcquireLockDelayUnit() {
        return acquireLockDelayUnit;
    }

    /**
     * The time unit for the acquireLockDelay, default to TimeUnit.SECONDS.
     */
    public void setAcquireLockDelayUnit(TimeUnit acquireLockDelayUnit) {
        this.acquireLockDelayUnit = acquireLockDelayUnit;
    }

    public long getAcquireLockInterval() {
        return acquireLockInterval;
    }

    /**
     * The time to wait between attempts to try to acquire the cluster lock evaluated using wall-clock time. All cluster
     * members must use the same value so leadership checks and leader liveness detection remain consistent, default 10.
     */
    public void setAcquireLockInterval(long acquireLockInterval) {
        if (acquireLockInterval <= 0) {
            throw new IllegalArgumentException("acquireLockInterval must be greater than 0");
        }
        this.acquireLockInterval = acquireLockInterval;
    }

    public void setAcquireLockInterval(long pollInterval, TimeUnit pollIntervalUnit) {
        setAcquireLockInterval(pollInterval);
        setAcquireLockIntervalUnit(pollIntervalUnit);
    }

    public TimeUnit getAcquireLockIntervalUnit() {
        return acquireLockIntervalUnit;
    }

    /**
     * The time unit for the acquireLockInterval, default to TimeUnit.SECONDS.
     */
    public void setAcquireLockIntervalUnit(TimeUnit acquireLockIntervalUnit) {
        this.acquireLockIntervalUnit = acquireLockIntervalUnit;
    }

    /**
     * Multiplier applied to the cluster leader {@code acquireLockInterval} to determine how long followers should wait
     * before considering the leader "stale".
     * <p>
     * For example, if the leader updates its heartbeat every 2 seconds and the {@code heartbeatTimeoutMultiplier} is 3,
     * followers will tolerate up to {@code 2s * 3 = 6s} of silence before declaring the leader unavailable.
     * <p>
     */
    public void setHeartbeatTimeoutMultiplier(int heartbeatTimeoutMultiplier) {
        if (heartbeatTimeoutMultiplier <= 0) {
            throw new IllegalArgumentException("HeartbeatTimeoutMultiplier must be greater than 0");
        }
        this.heartbeatTimeoutMultiplier = heartbeatTimeoutMultiplier;
    }

    public int getHeartbeatTimeoutMultiplier() {
        return heartbeatTimeoutMultiplier;
    }

    /**
     * Sets how many times a cluster data task will run, counting both the first execution and subsequent retries in
     * case of failure or timeout. The default is 5 attempts.
     * <p>
     * This can be useful when the cluster data root is on network based file storage, where I/O operations may
     * occasionally block for long or unpredictable periods.
     */
    public void setClusterDataTaskMaxAttempts(int clusterDataTaskMaxAttempts) {
        if (clusterDataTaskMaxAttempts <= 0) {
            throw new IllegalArgumentException("clusterDataTaskMaxRetries must be greater than 0");
        }
        this.clusterDataTaskMaxAttempts = clusterDataTaskMaxAttempts;
    }

    public int getClusterDataTaskMaxAttempts() {
        return clusterDataTaskMaxAttempts;
    }

    /**
     * Sets the timeout for a cluster data task (reading or writing cluster data). The default is 10 seconds.
     * <p>
     * Timeouts are useful when the cluster data root is on network storage, where I/O operations may occasionally block
     * for long or unpredictable periods.
     */
    public void setClusterDataTaskTimeout(long clusterDataTaskTimeout) {
        if (clusterDataTaskTimeout <= 0) {
            throw new IllegalArgumentException("clusterDataTaskMaxRetries must be greater than 0");
        }
        this.clusterDataTaskTimeout = clusterDataTaskTimeout;
    }

    public long getClusterDataTaskTimeout() {
        return clusterDataTaskTimeout;
    }

    /**
     * The time unit for the clusterDataTaskTimeoutUnit, default to TimeUnit.SECONDS.
     */
    public void setClusterDataTaskTimeoutUnit(TimeUnit clusterDataTaskTimeoutUnit) {
        this.clusterDataTaskTimeoutUnit = clusterDataTaskTimeoutUnit;
    }

    public TimeUnit getClusterDataTaskTimeoutUnit() {
        return clusterDataTaskTimeoutUnit;
    }

    /**
     * Sets the timeout for a cluster data task (reading or writing cluster data). The default is 10 seconds.
     * <p>
     * Timeouts are useful when the cluster data root is on network storage, where I/O operations may occasionally block
     * for long or unpredictable periods.
     * <p>
     */
    public void setClusterDataTaskTimeout(long clusterDataTaskTimeout, TimeUnit clusterDataTaskTimeoutUnit) {
        setClusterDataTaskTimeout(clusterDataTaskTimeout);
        setClusterDataTaskTimeoutUnit(clusterDataTaskTimeoutUnit);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        CamelContext context = getCamelContext();

        if (executor != null) {
            if (context != null) {
                context.getExecutorServiceManager().shutdown(executor);
            } else {
                executor.shutdown();
            }

            executor = null;
        }

        if (clusterDataTaskExecutor != null) {
            if (context != null) {
                context.getExecutorServiceManager().shutdown(clusterDataTaskExecutor);
            } else {
                clusterDataTaskExecutor.shutdown();
            }
        }
    }

    ScheduledExecutorService getExecutor() {
        Lock internalLock = getInternalLock();
        internalLock.lock();
        try {
            if (executor == null) {
                // Camel context should be set at this stage.
                final CamelContext context = ObjectHelper.notNull(getCamelContext(), "CamelContext");

                executor = context.getExecutorServiceManager()
                        .newSingleThreadScheduledExecutor(this, "FileLockClusterService-" + getId());
            }

            return executor;
        } finally {
            internalLock.unlock();
        }
    }

    ExecutorService getClusterDataTaskExecutor() {
        Lock internalLock = getInternalLock();
        internalLock.lock();
        try {
            if (clusterDataTaskExecutor == null) {
                final CamelContext context = ObjectHelper.notNull(getCamelContext(), "CamelContext");
                clusterDataTaskExecutor = context.getExecutorServiceManager().newFixedThreadPool(this,
                        "FileLockClusterDataTask-" + getId(), 5);
            }
            return clusterDataTaskExecutor;
        } finally {
            internalLock.unlock();
        }
    }
}
