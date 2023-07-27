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

import java.time.Duration;
import java.util.List;

import org.apache.camel.support.jsse.SSLContextParameters;

/**
 * HTTP server configuration
 */
public class VertxPlatformHttpServerConfiguration {
    public static final String DEFAULT_BIND_HOST = "0.0.0.0";
    public static final int DEFAULT_BIND_PORT = 8080;
    public static final String DEFAULT_PATH = "/";

    private String bindHost = DEFAULT_BIND_HOST;
    private int bindPort = DEFAULT_BIND_PORT;
    private String path = DEFAULT_PATH;
    private Long maxBodySize;

    private SSLContextParameters sslContextParameters;
    private boolean useGlobalSslContextParameters;

    private BodyHandler bodyHandler = new BodyHandler();
    private Cors cors = new Cors();

    public int getPort() {
        return getBindPort();
    }

    public void setPort(int port) {
        setBindPort(port);
    }

    public void setHost(String host) {
        setBindHost(host);
    }

    public String getHost() {
        return getBindHost();
    }

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

    public Long getMaxBodySize() {
        return maxBodySize;
    }

    public void setMaxBodySize(Long maxBodySize) {
        this.maxBodySize = maxBodySize;
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

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors corsConfiguration) {
        this.cors = corsConfiguration;
    }

    public BodyHandler getBodyHandler() {
        return bodyHandler;
    }

    public void setBodyHandler(BodyHandler bodyHandler) {
        this.bodyHandler = bodyHandler;
    }

    public static class Cors {
        private boolean enabled;
        private List<String> origins;
        private List<String> methods;
        private List<String> headers;
        private List<String> exposedHeaders;
        private Duration accessControlMaxAge;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getOrigins() {
            return origins;
        }

        public void setOrigins(List<String> origins) {
            this.origins = origins;
        }

        public List<String> getMethods() {
            return methods;
        }

        public void setMethods(List<String> methods) {
            this.methods = methods;
        }

        public List<String> getHeaders() {
            return headers;
        }

        public List<String> getExposedHeaders() {
            return exposedHeaders;
        }

        public void setExposedHeaders(List<String> exposedHeaders) {
            this.exposedHeaders = exposedHeaders;
        }

        public void setHeaders(List<String> headers) {
            this.headers = headers;
        }

        public Duration getAccessControlMaxAge() {
            return accessControlMaxAge;
        }

        public void setAccessControlMaxAge(Duration accessControlMaxAge) {
            this.accessControlMaxAge = accessControlMaxAge;
        }
    }

    public static class BodyHandler {
        private boolean handleFileUploads;
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
