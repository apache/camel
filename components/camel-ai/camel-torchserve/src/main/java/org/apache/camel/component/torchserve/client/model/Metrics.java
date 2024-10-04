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
package org.apache.camel.component.torchserve.client.model;

import org.apache.camel.component.torchserve.client.management.model.DescribeModel200ResponseInnerMetrics;

public class Metrics {

    private Integer rejectedRequests = null;
    private Integer waitingQueueSize = null;
    private Integer requests = null;

    public Metrics() {
    }

    public static Metrics from(DescribeModel200ResponseInnerMetrics src) {
        if (src == null) {
            return null;
        }

        Metrics metrics = new Metrics();
        metrics.setRejectedRequests(src.getRejectedRequests());
        metrics.setWaitingQueueSize(src.getWaitingQueueSize());
        metrics.setRequests(src.getRequests());
        return metrics;
    }

    public Integer getRejectedRequests() {
        return rejectedRequests;
    }

    public void setRejectedRequests(Integer rejectedRequests) {
        this.rejectedRequests = rejectedRequests;
    }

    public Integer getWaitingQueueSize() {
        return waitingQueueSize;
    }

    public void setWaitingQueueSize(Integer waitingQueueSize) {
        this.waitingQueueSize = waitingQueueSize;
    }

    public Integer getRequests() {
        return requests;
    }

    public void setRequests(Integer requests) {
        this.requests = requests;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " {" +
               " rejectedRequests: " + rejectedRequests + "," +
               " waitingQueueSize: " + waitingQueueSize + "," +
               " requests: " + requests + " " +
               "}";
    }
}
