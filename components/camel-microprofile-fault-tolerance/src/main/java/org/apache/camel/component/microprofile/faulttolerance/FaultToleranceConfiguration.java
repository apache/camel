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
package org.apache.camel.component.microprofile.faulttolerance;

public class FaultToleranceConfiguration {

    private long delay;
    private int successThreshold;
    private int requestVolumeThreshold;
    private float failureRatio;
    private boolean timeoutEnabled;
    private long timeoutDuration;
    private int timeoutPoolSize;
    private String timeoutExecutorServiceRef;
    private boolean bulkheadEnabled;
    private int bulkheadMaxConcurrentCalls;
    private int bulkheadWaitingTaskQueue;

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public int getSuccessThreshold() {
        return successThreshold;
    }

    public void setSuccessThreshold(int successThreshold) {
        this.successThreshold = successThreshold;
    }

    public int getRequestVolumeThreshold() {
        return requestVolumeThreshold;
    }

    public void setRequestVolumeThreshold(int requestVolumeThreshold) {
        this.requestVolumeThreshold = requestVolumeThreshold;
    }

    public float getFailureRatio() {
        return failureRatio;
    }

    public void setFailureRatio(float failureRatio) {
        this.failureRatio = failureRatio;
    }

    public boolean isTimeoutEnabled() {
        return timeoutEnabled;
    }

    public void setTimeoutEnabled(boolean timeoutEnabled) {
        this.timeoutEnabled = timeoutEnabled;
    }

    public long getTimeoutDuration() {
        return timeoutDuration;
    }

    public void setTimeoutDuration(long timeoutDuration) {
        this.timeoutDuration = timeoutDuration;
    }

    public int getTimeoutPoolSize() {
        return timeoutPoolSize;
    }

    public void setTimeoutPoolSize(int timeoutPoolSize) {
        this.timeoutPoolSize = timeoutPoolSize;
    }

    public String getTimeoutExecutorServiceRef() {
        return timeoutExecutorServiceRef;
    }

    public void setTimeoutExecutorServiceRef(String timeoutExecutorServiceRef) {
        this.timeoutExecutorServiceRef = timeoutExecutorServiceRef;
    }

    public boolean isBulkheadEnabled() {
        return bulkheadEnabled;
    }

    public void setBulkheadEnabled(boolean bulkheadEnabled) {
        this.bulkheadEnabled = bulkheadEnabled;
    }

    public int getBulkheadMaxConcurrentCalls() {
        return bulkheadMaxConcurrentCalls;
    }

    public void setBulkheadMaxConcurrentCalls(int bulkheadMaxConcurrentCalls) {
        this.bulkheadMaxConcurrentCalls = bulkheadMaxConcurrentCalls;
    }

    public int getBulkheadWaitingTaskQueue() {
        return bulkheadWaitingTaskQueue;
    }

    public void setBulkheadWaitingTaskQueue(int bulkheadWaitingTaskQueue) {
        this.bulkheadWaitingTaskQueue = bulkheadWaitingTaskQueue;
    }
}
