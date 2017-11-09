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
package org.apache.camel.component.file.cluster;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.cluster.AbstractCamelClusterService;
import org.apache.camel.util.ObjectHelper;

public class FileLockClusterService extends AbstractCamelClusterService<FileLockClusterView> {
    private String root;
    private long acquireLockDelay;
    private TimeUnit acquireLockDelayUnit;
    private long acquireLockInterval;
    private TimeUnit acquireLockIntervalUnit;
    private ScheduledExecutorService executor;

    public FileLockClusterService() {
        this.acquireLockDelay = 1;
        this.acquireLockDelayUnit = TimeUnit.SECONDS;
        this.acquireLockInterval = 10;
        this.acquireLockIntervalUnit = TimeUnit.SECONDS;
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
     * The time to wait before starting to try to acquire lock, default 1.
     */
    public void setAcquireLockDelay(long acquireLockDelay) {
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
     * The time unit fo the acquireLockDelay, default to TimeUnit.SECONDS.
     */
    public void setAcquireLockDelayUnit(TimeUnit acquireLockDelayUnit) {
        this.acquireLockDelayUnit = acquireLockDelayUnit;
    }

    public long getAcquireLockInterval() {
        return acquireLockInterval;
    }

    /**
     * The time to wait between attempts to try to acquire lock, default 10.
     */
    public void setAcquireLockInterval(long acquireLockInterval) {
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
     * The time unit fo the acquireLockInterva, default to TimeUnit.SECONDS.
     */
    public void setAcquireLockIntervalUnit(TimeUnit acquireLockIntervalUnit) {
        this.acquireLockIntervalUnit = acquireLockIntervalUnit;
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
    }

    synchronized ScheduledExecutorService getExecutor() {
        if (executor == null) {
            // Camel context should be set at this stage.
            final CamelContext context = ObjectHelper.notNull(getCamelContext(), "CamelContext");

            executor = context.getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "FileLockClusterService-" + getId());
        }

        return executor;
    }
}
