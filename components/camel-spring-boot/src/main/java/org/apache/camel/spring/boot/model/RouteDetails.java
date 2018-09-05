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
package org.apache.camel.spring.boot.model;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.spi.RouteError;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RouteDetails {

    private long deltaProcessingTime;

    private long exchangesInflight;

    private long exchangesTotal;

    private long externalRedeliveries;

    private long failuresHandled;

    private String firstExchangeCompletedExchangeId;

    private Date firstExchangeCompletedTimestamp;

    private String firstExchangeFailureExchangeId;

    private Date firstExchangeFailureTimestamp;

    private String lastExchangeCompletedExchangeId;

    private Date lastExchangeCompletedTimestamp;

    private String lastExchangeFailureExchangeId;

    private Date lastExchangeFailureTimestamp;

    private long lastProcessingTime;

    private String load01;

    private String load05;

    private String load15;

    private long maxProcessingTime;

    private long meanProcessingTime;

    private long minProcessingTime;

    private Long oldestInflightDuration;

    private String oldestInflightExchangeId;

    private long redeliveries;

    private long totalProcessingTime;

    private RouteError lastError;

    private boolean hasRouteController;

    public RouteDetails(ManagedRouteMBean managedRoute) {
        try {
            this.deltaProcessingTime = managedRoute.getDeltaProcessingTime();
            this.exchangesInflight = managedRoute.getExchangesInflight();
            this.exchangesTotal = managedRoute.getExchangesTotal();
            this.externalRedeliveries = managedRoute.getExternalRedeliveries();
            this.failuresHandled = managedRoute.getFailuresHandled();
            this.firstExchangeCompletedExchangeId = managedRoute.getFirstExchangeCompletedExchangeId();
            this.firstExchangeCompletedTimestamp = managedRoute.getFirstExchangeCompletedTimestamp();
            this.firstExchangeFailureExchangeId = managedRoute.getFirstExchangeFailureExchangeId();
            this.firstExchangeFailureTimestamp = managedRoute.getFirstExchangeFailureTimestamp();
            this.lastExchangeCompletedExchangeId = managedRoute.getLastExchangeCompletedExchangeId();
            this.lastExchangeCompletedTimestamp = managedRoute.getLastExchangeCompletedTimestamp();
            this.lastExchangeFailureExchangeId = managedRoute.getLastExchangeFailureExchangeId();
            this.lastExchangeFailureTimestamp = managedRoute.getLastExchangeFailureTimestamp();
            this.lastProcessingTime = managedRoute.getLastProcessingTime();
            this.load01 = managedRoute.getLoad01();
            this.load05 = managedRoute.getLoad05();
            this.load15 = managedRoute.getLoad15();
            this.maxProcessingTime = managedRoute.getMaxProcessingTime();
            this.meanProcessingTime = managedRoute.getMeanProcessingTime();
            this.minProcessingTime = managedRoute.getMinProcessingTime();
            this.oldestInflightDuration = managedRoute.getOldestInflightDuration();
            this.oldestInflightExchangeId = managedRoute.getOldestInflightExchangeId();
            this.redeliveries = managedRoute.getRedeliveries();
            this.totalProcessingTime = managedRoute.getTotalProcessingTime();
            this.lastError = managedRoute.getLastError();
            this.hasRouteController = managedRoute.getHasRouteController();
        } catch (Exception e) {
            // Ignore
        }
    }

    public long getDeltaProcessingTime() {
        return deltaProcessingTime;
    }

    public long getExchangesInflight() {
        return exchangesInflight;
    }

    public long getExchangesTotal() {
        return exchangesTotal;
    }

    public long getExternalRedeliveries() {
        return externalRedeliveries;
    }

    public long getFailuresHandled() {
        return failuresHandled;
    }

    public String getFirstExchangeCompletedExchangeId() {
        return firstExchangeCompletedExchangeId;
    }

    public Date getFirstExchangeCompletedTimestamp() {
        return firstExchangeCompletedTimestamp;
    }

    public String getFirstExchangeFailureExchangeId() {
        return firstExchangeFailureExchangeId;
    }

    public Date getFirstExchangeFailureTimestamp() {
        return firstExchangeFailureTimestamp;
    }

    public String getLastExchangeCompletedExchangeId() {
        return lastExchangeCompletedExchangeId;
    }

    public Date getLastExchangeCompletedTimestamp() {
        return lastExchangeCompletedTimestamp;
    }

    public String getLastExchangeFailureExchangeId() {
        return lastExchangeFailureExchangeId;
    }

    public Date getLastExchangeFailureTimestamp() {
        return lastExchangeFailureTimestamp;
    }

    public long getLastProcessingTime() {
        return lastProcessingTime;
    }

    public String getLoad01() {
        return load01;
    }

    public String getLoad05() {
        return load05;
    }

    public String getLoad15() {
        return load15;
    }

    public long getMaxProcessingTime() {
        return maxProcessingTime;
    }

    public long getMeanProcessingTime() {
        return meanProcessingTime;
    }

    public long getMinProcessingTime() {
        return minProcessingTime;
    }

    public Long getOldestInflightDuration() {
        return oldestInflightDuration;
    }

    public String getOldestInflightExchangeId() {
        return oldestInflightExchangeId;
    }

    public long getRedeliveries() {
        return redeliveries;
    }

    public long getTotalProcessingTime() {
        return totalProcessingTime;
    }

    public RouteError getLastError() {
        return lastError;
    }

    public boolean getHasRouteController() {
        return hasRouteController;
    }
}
