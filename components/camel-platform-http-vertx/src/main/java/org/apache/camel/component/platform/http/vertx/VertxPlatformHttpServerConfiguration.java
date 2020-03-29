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
package org.apache.camel.component.platform.http.vertx;

import java.math.BigInteger;

import org.apache.camel.support.jsse.SSLContextParameters;

public class VertxPlatformHttpServerConfiguration {
    public static final String DEFAULT_BIND_HOST = "0.0.0.0";
    public static final int DEFAULT_BIND_PORT = 8080;
    public static final String DEFAULT_PATH = "/";

    private String bindHost = DEFAULT_BIND_HOST;
    private int bindPort = DEFAULT_BIND_PORT;
    private String path = DEFAULT_PATH;
    private BigInteger maxBodySize;

    private BodyHandler bodyHandler = new BodyHandler();
    private SSLContextParameters sslContextParameters;
    private boolean useGlobalSslContextParameters;

    public String getBindHost() {
        return bindHost;
    }

    public void setBindHost(String bindHost) {
        this.bindHost = bindHost;
    }

    public int getBindPort() {
        return bindPort;
    }

    public void setBindPort(int bindPort) {
        this.bindPort = bindPort;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public BigInteger getMaxBodySize() {
        return maxBodySize;
    }

    public void setMaxBodySize(BigInteger maxBodySize) {
        this.maxBodySize = maxBodySize;
    }

    public BodyHandler getBodyHandler() {
        return bodyHandler;
    }

    public void setBodyHandler(BodyHandler bodyHandler) {
        this.bodyHandler = bodyHandler;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public boolean isUseGlobalSslContextParameters() {
        return useGlobalSslContextParameters;
    }

    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

    public static class BodyHandler {
        private boolean handleFileUploads = true;
        private String uploadsDirectory = "file-uploads";
        private boolean mergeFormAttributes = true;
        private boolean deleteUploadedFilesOnEnd;
        private boolean preallocateBodyBuffer = true;

        public boolean isHandleFileUploads() {
            return handleFileUploads;
        }

        public void setHandleFileUploads(boolean handleFileUploads) {
            this.handleFileUploads = handleFileUploads;
        }

        public String getUploadsDirectory() {
            return uploadsDirectory;
        }

        public void setUploadsDirectory(String uploadsDirectory) {
            this.uploadsDirectory = uploadsDirectory;
        }

        public boolean isMergeFormAttributes() {
            return mergeFormAttributes;
        }

        public void setMergeFormAttributes(boolean mergeFormAttributes) {
            this.mergeFormAttributes = mergeFormAttributes;
        }

        public boolean isDeleteUploadedFilesOnEnd() {
            return deleteUploadedFilesOnEnd;
        }

        public void setDeleteUploadedFilesOnEnd(boolean deleteUploadedFilesOnEnd) {
            this.deleteUploadedFilesOnEnd = deleteUploadedFilesOnEnd;
        }

        public boolean isPreallocateBodyBuffer() {
            return preallocateBodyBuffer;
        }

        public void setPreallocateBodyBuffer(boolean preallocateBodyBuffer) {
            this.preallocateBodyBuffer = preallocateBodyBuffer;
        }
    }
}
