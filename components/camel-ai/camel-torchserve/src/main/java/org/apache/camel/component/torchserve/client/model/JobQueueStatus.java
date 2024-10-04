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

import org.apache.camel.component.torchserve.client.management.model.DescribeModel200ResponseInnerJobQueueStatus;

public class JobQueueStatus {

    private Integer remainingCapacity = null;
    private Integer pendingRequests = null;

    public JobQueueStatus() {
    }

    public static JobQueueStatus from(DescribeModel200ResponseInnerJobQueueStatus src) {
        if (src == null) {
            return null;
        }

        JobQueueStatus status = new JobQueueStatus();
        status.setRemainingCapacity(src.getRemainingCapacity());
        status.setPendingRequests(src.getPendingRequests());
        return status;
    }

    public Integer getRemainingCapacity() {
        return remainingCapacity;
    }

    public void setRemainingCapacity(Integer remainingCapacity) {
        this.remainingCapacity = remainingCapacity;
    }

    public Integer getPendingRequests() {
        return pendingRequests;
    }

    public void setPendingRequests(Integer pendingRequests) {
        this.pendingRequests = pendingRequests;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " {" +
               " remainingCapacity: " + remainingCapacity + "," +
               " pendingRequests: " + pendingRequests + " " +
               "}";
    }
}
