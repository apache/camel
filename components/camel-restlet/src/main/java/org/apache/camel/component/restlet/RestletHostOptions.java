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
package org.apache.camel.component.restlet;

/**
 * Options to configure on a {@link RestletHost}
 */
public final class RestletHostOptions {

    /**
     * Indicates if the controller thread should be a daemon.
     */
    private Boolean controllerDaemon;

    /**
     * Time for the controller thread to sleep between each control.
     */
    private Integer controllerSleepTimeMs;

    /**
     * Size of the content buffer for receiving messages.
     */
    private Integer inboundBufferSize;

    /**
     * Maximum number of calls that can be queued if there aren’t any worker thread available to service them.
     */
    private Integer maxQueued;

    /**
     * Maximum number of concurrent connections per host.
     */
    private Integer maxConnectionsPerHost;

    /**
     * Maximum number of worker threads waiting to service requests.
     */
    private Integer maxThreads;

    /**
     * Maximum number of concurrent connections in total.
     */
    private Integer maxTotalConnections;

    /**
     * Minimum number of worker threads waiting to service requests, even if they are idle.
     */
    private Integer minThreads;

    /**
     * Number of worker threads determining when the connector is considered overloaded.
     */
    private Integer lowThreads;

    /**
     * Size of the content buffer for sending messages.
     */
    private Integer outboundBufferSize;

    /**
     * Indicates if connections should be kept alive after a call.
     */
    private Boolean persistingConnections;

    /**
     * Indicates if pipelining connections are supported.
     */
    private Boolean pipeliningConnections;

    /**
     * Enable/disable the SO_REUSEADDR socket option. See java.io.ServerSocket#reuseAddress property for additional details.
     */
    private Boolean reuseAddress;

    /**
     * Time for an idle thread to wait for an operation before being collected.
     */
    private Integer threadMaxIdleTimeMs;

    /**
     * Lookup the "X-Forwarded-For" header supported by popular proxies and caches and uses it to populate the Request.getClientAddresses() method result.
     */
    private Boolean useForwardedForHeader;

    public Boolean getControllerDaemon() {
        return controllerDaemon;
    }

    public void setControllerDaemon(Boolean controllerDaemon) {
        this.controllerDaemon = controllerDaemon;
    }

    public Integer getControllerSleepTimeMs() {
        return controllerSleepTimeMs;
    }

    public void setControllerSleepTimeMs(Integer controllerSleepTimeMs) {
        this.controllerSleepTimeMs = controllerSleepTimeMs;
    }

    public Integer getInboundBufferSize() {
        return inboundBufferSize;
    }

    public void setInboundBufferSize(Integer inboundBufferSize) {
        this.inboundBufferSize = inboundBufferSize;
    }

    public Integer getMaxQueued() {
        return maxQueued;
    }

    public void setMaxQueued(Integer maxQueued) {
        this.maxQueued = maxQueued;
    }

    public Integer getMaxConnectionsPerHost() {
        return maxConnectionsPerHost;
    }

    public void setMaxConnectionsPerHost(Integer maxConnectionsPerHost) {
        this.maxConnectionsPerHost = maxConnectionsPerHost;
    }

    public Integer getMaxThreads() {
        return maxThreads;
    }

    public void setMaxThreads(Integer maxThreads) {
        this.maxThreads = maxThreads;
    }

    public Integer getMaxTotalConnections() {
        return maxTotalConnections;
    }

    public void setMaxTotalConnections(Integer maxTotalConnections) {
        this.maxTotalConnections = maxTotalConnections;
    }

    public Integer getMinThreads() {
        return minThreads;
    }

    public void setMinThreads(Integer minThreads) {
        this.minThreads = minThreads;
    }

    public Integer getLowThreads() {
        return lowThreads;
    }

    public void setLowThreads(Integer lowThreads) {
        this.lowThreads = lowThreads;
    }

    public Integer getOutboundBufferSize() {
        return outboundBufferSize;
    }

    public void setOutboundBufferSize(Integer outboundBufferSize) {
        this.outboundBufferSize = outboundBufferSize;
    }

    public Boolean getPersistingConnections() {
        return persistingConnections;
    }

    public void setPersistingConnections(Boolean persistingConnections) {
        this.persistingConnections = persistingConnections;
    }

    public Boolean getPipeliningConnections() {
        return pipeliningConnections;
    }

    public void setPipeliningConnections(Boolean pipeliningConnections) {
        this.pipeliningConnections = pipeliningConnections;
    }

    public Boolean getReuseAddress() {
        return reuseAddress;
    }

    public void setReuseAddress(Boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    public Integer getThreadMaxIdleTimeMs() {
        return threadMaxIdleTimeMs;
    }

    public void setThreadMaxIdleTimeMs(Integer threadMaxIdleTimeMs) {
        this.threadMaxIdleTimeMs = threadMaxIdleTimeMs;
    }

    public Boolean getUseForwardedForHeader() {
        return useForwardedForHeader;
    }

    public void setUseForwardedForHeader(Boolean useForwardedForHeader) {
        this.useForwardedForHeader = useForwardedForHeader;
    }
}
