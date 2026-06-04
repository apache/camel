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
package org.apache.camel.component.undertow;

import org.apache.camel.spi.Metadata;

/**
 * Options to configure an Undertow host.
 */
public final class UndertowHostOptions {

    @Metadata(description = "The number of worker threads to use in a Undertow host.")
    private Integer workerThreads;
    @Metadata(description = "The number of io threads to use in a Undertow host.")
    private Integer ioThreads;
    @Metadata(description = "The buffer size of the Undertow host.")
    private Integer bufferSize;
    @Metadata(description = "Set if the Undertow host should use direct buffers.")
    private Boolean directBuffers;
    @Metadata(description = "Set if the Undertow host should use http2 protocol.")
    private Boolean http2Enabled;
    @Metadata(description = "The maximum size of the HTTP entity body, in bytes. Requests with a body larger than this will be rejected.")
    private Long maxEntitySize;
    @Metadata(description = "The maximum size of a multipart HTTP entity body, in bytes. Multipart requests larger than this will be rejected.")
    private Long multipartMaxEntitySize;
    @Metadata(description = "The maximum size of an HTTP request header, in bytes. Requests with headers larger than this will be rejected.")
    private Integer maxHeaderSize;
    @Metadata(description = "The amount of time in milliseconds a connection can be idle with no current requests before it is closed.")
    private Integer noRequestTimeout;
    @Metadata(description = "The idle timeout in milliseconds after which the channel will be closed.")
    private Integer idleTimeout;
    @Metadata(description = "The maximum time in milliseconds to parse an HTTP request.")
    private Integer requestParseTimeout;
    @Metadata(description = "The maximum number of query and path parameters that will be parsed.")
    private Integer maxParameters;
    @Metadata(description = "The maximum number of HTTP headers that will be parsed.")
    private Integer maxHeaders;

    public UndertowHostOptions() {
    }

    public Integer getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(Integer workerThreads) {
        this.workerThreads = workerThreads;
    }

    public Integer getIoThreads() {
        return ioThreads;
    }

    public void setIoThreads(Integer ioThreads) {
        this.ioThreads = ioThreads;
    }

    public Integer getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(Integer bufferSize) {
        this.bufferSize = bufferSize;
    }

    public Boolean getDirectBuffers() {
        return directBuffers;
    }

    public void setDirectBuffers(Boolean directBuffers) {
        this.directBuffers = directBuffers;
    }

    public Boolean getHttp2Enabled() {
        return http2Enabled;
    }

    public void setHttp2Enabled(Boolean http2Enabled) {
        this.http2Enabled = http2Enabled;
    }

    public Long getMaxEntitySize() {
        return maxEntitySize;
    }

    public void setMaxEntitySize(Long maxEntitySize) {
        this.maxEntitySize = maxEntitySize;
    }

    public Long getMultipartMaxEntitySize() {
        return multipartMaxEntitySize;
    }

    public void setMultipartMaxEntitySize(Long multipartMaxEntitySize) {
        this.multipartMaxEntitySize = multipartMaxEntitySize;
    }

    public Integer getMaxHeaderSize() {
        return maxHeaderSize;
    }

    public void setMaxHeaderSize(Integer maxHeaderSize) {
        this.maxHeaderSize = maxHeaderSize;
    }

    public Integer getNoRequestTimeout() {
        return noRequestTimeout;
    }

    public void setNoRequestTimeout(Integer noRequestTimeout) {
        this.noRequestTimeout = noRequestTimeout;
    }

    public Integer getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(Integer idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public Integer getRequestParseTimeout() {
        return requestParseTimeout;
    }

    public void setRequestParseTimeout(Integer requestParseTimeout) {
        this.requestParseTimeout = requestParseTimeout;
    }

    public Integer getMaxParameters() {
        return maxParameters;
    }

    public void setMaxParameters(Integer maxParameters) {
        this.maxParameters = maxParameters;
    }

    public Integer getMaxHeaders() {
        return maxHeaders;
    }

    public void setMaxHeaders(Integer maxHeaders) {
        this.maxHeaders = maxHeaders;
    }

}
