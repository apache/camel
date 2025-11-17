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
package org.apache.camel.opentelemetry.metrics.routepolicy;

/**
 * Configuration for enabling and disabling metrics collected by the OpenTelemetry route policy.
 */
public class OpenTelemetryRoutePolicyConfiguration {

    private boolean contextEnabled = true;
    private boolean routeEnabled = true;
    private String excludePattern;
    private boolean additionalCounters = true;
    private boolean exchangesSucceeded = true;
    private boolean exchangesFailed = true;
    private boolean exchangesTotal = true;
    private boolean externalRedeliveries = true;
    private boolean failuresHandled = true;
    private boolean longTask;

    public boolean isContextEnabled() {
        return contextEnabled;
    }

    /**
     * Enable context level metric collection.
     */
    public void setContextEnabled(boolean contextEnabled) {
        this.contextEnabled = contextEnabled;
    }

    public boolean isRouteEnabled() {
        return routeEnabled;
    }

    /**
     * Enable route level metrics.
     */
    public void setRouteEnabled(boolean routeEnabled) {
        this.routeEnabled = routeEnabled;
    }

    public String getExcludePattern() {
        return excludePattern;
    }

    /**
     * A comma separated list of regex patterns to that are used to exclude routes from metrics.
     */
    public void setExcludePattern(String excludePattern) {
        this.excludePattern = excludePattern;
    }

    public boolean isAdditionalCounters() {
        return additionalCounters;
    }

    /**
     * Enable all additional route metrics such as exchanges succeeded, failed, total exchanges, external redeliveries
     * and handled failures are enabled.
     */
    public void setAdditionalCounters(boolean additionalCounters) {
        this.additionalCounters = additionalCounters;
    }

    public boolean isExchangesSucceeded() {
        return exchangesSucceeded;
    }

    /**
     * Enable 'succeeded exchanges' counter.
     */
    public void setExchangesSucceeded(boolean exchangesSucceeded) {
        this.exchangesSucceeded = exchangesSucceeded;
    }

    public boolean isExchangesFailed() {
        return exchangesFailed;
    }

    /**
     * Enable 'failed exchanges' counter.
     */
    public void setExchangesFailed(boolean exchangesFailed) {
        this.exchangesFailed = exchangesFailed;
    }

    public boolean isExchangesTotal() {
        return exchangesTotal;
    }

    /**
     * Enable 'total exchanges' counter.
     */
    public void setExchangesTotal(boolean exchangesTotal) {
        this.exchangesTotal = exchangesTotal;
    }

    public boolean isExternalRedeliveries() {
        return externalRedeliveries;
    }

    /**
     * Enable 'external redeliveries' counter.
     */
    public void setExternalRedeliveries(boolean externalRedeliveries) {
        this.externalRedeliveries = externalRedeliveries;
    }

    public boolean isFailuresHandled() {
        return failuresHandled;
    }

    /**
     * Enable 'handled failures' counter.
     */
    public void setFailuresHandled(boolean failuresHandled) {
        this.failuresHandled = failuresHandled;
    }

    public boolean isLongTask() {
        return longTask;
    }

    /**
     * Enable 'long task timer' metrics that track the number of active tasks and total duration of all tasks in
     * progress.
     */
    public void setLongTask(boolean longTask) {
        this.longTask = longTask;
    }
}
