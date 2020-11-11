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

}
