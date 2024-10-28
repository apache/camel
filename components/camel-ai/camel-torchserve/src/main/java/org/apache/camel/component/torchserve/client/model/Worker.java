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

import org.apache.camel.component.torchserve.client.management.model.DescribeModel200ResponseInnerWorkersInner;

public class Worker {

    private String id = null;
    private String startTime = null;
    private Boolean gpu = null;
    private Status status = null;

    public Worker() {
    }

    public static Worker from(DescribeModel200ResponseInnerWorkersInner src) {
        if (src == null) {
            return null;
        }

        Worker worker = new Worker();
        worker.setId(src.getId());
        worker.setStartTime(src.getStartTime());
        worker.setGpu(src.getGpu());
        worker.setStatus(Status.from(src.getStatus()));
        return worker;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public Boolean getGpu() {
        return gpu;
    }

    public void setGpu(Boolean gpu) {
        this.gpu = gpu;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " {" +
               " id: " + id + "," +
               " startTime: " + startTime + "," +
               " gpu: " + gpu + "," +
               " status: " + status + " " +
               "}";
    }

    public enum Status {
        READY,
        LOADING,
        UNLOADING;

        public static Status from(DescribeModel200ResponseInnerWorkersInner.StatusEnum status) {
            return switch (status) {
                case READY -> READY;
                case LOADING -> LOADING;
                case UNLOADING -> UNLOADING;
            };
        }
    }
}
