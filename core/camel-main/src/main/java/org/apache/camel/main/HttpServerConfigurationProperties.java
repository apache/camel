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
package org.apache.camel.main;

import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;

/**
 * Configuration for embedded HTTP server for standalone Camel applications (not Spring Boot / Quarkus).
 */
@Configurer(bootstrap = true)
public class HttpServerConfigurationProperties implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    @Metadata
    private boolean enabled;
    @Metadata(defaultValue = "0.0.0.0")
    private String host = "0.0.0.0";
    @Metadata(defaultValue = "8080")
    private int port = 8080;
    @Metadata(defaultValue = "/")
    private String path = "/";
    private Long maxBodySize;
    private boolean useGlobalSslContextParameters;

    private boolean devConsoleEnabled;
    private boolean healthCheckEnabled;
    private boolean jolokiaEnabled;
    private boolean metricsEnabled;
    private boolean uploadEnabled;
    private String uploadSourceDir;

    public HttpServerConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    @Override
    public void close() {
        parent = null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether embedded HTTP server is enabled. By default, the server is not enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    /**
     * Hostname to use for binding embedded HTTP server
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * Port to use for binding embedded HTTP server
     */
    public void setPort(int port) {
        this.port = port;
    }

    public String getPath() {
        return path;
    }

    /**
     * Context-path to use for embedded HTTP server
     */
    public void setPath(String path) {
        this.path = path;
    }

    public Long getMaxBodySize() {
        return maxBodySize;
    }

    /**
     * Maximum HTTP body size the embedded HTTP server can accept.
     */
    public void setMaxBodySize(Long maxBodySize) {
        this.maxBodySize = maxBodySize;
    }

    public boolean isUseGlobalSslContextParameters() {
        return useGlobalSslContextParameters;
    }

    /**
     * Whether to use global SSL configuration for securing the embedded HTTP server.
     */
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

    public boolean isDevConsoleEnabled() {
        return devConsoleEnabled;
    }

    /**
     * Whether to enable developer console (not intended for production use). Dev console must also be enabled on
     * CamelContext. For example by setting camel.context.dev-console=true in application.properties, or via code
     * <tt>camelContext.setDevConsole(true);</tt> If enabled then you can access a basic developer console on
     * context-path: /q/dev.
     */
    public void setDevConsoleEnabled(boolean devConsoleEnabled) {
        this.devConsoleEnabled = devConsoleEnabled;
    }

    public boolean isHealthCheckEnabled() {
        return healthCheckEnabled;
    }

    /**
     * Whether to enable health-check console. If enabled then you can access health-check status on context-path:
     * /q/health
     */
    public void setHealthCheckEnabled(boolean healthCheckEnabled) {
        this.healthCheckEnabled = healthCheckEnabled;
    }

    public boolean isJolokiaEnabled() {
        return jolokiaEnabled;
    }

    /**
     * Whether to enable jolokia. If enabled then you can access jolokia api on context-path: /q/jolokia
     */
    public void setJolokiaEnabled(boolean jolokiaEnabled) {
        this.jolokiaEnabled = jolokiaEnabled;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    /**
     * Whether to enable metrics. If enabled then you can access metrics on context-path: /q/metrics
     */
    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }

    public boolean isUploadEnabled() {
        return uploadEnabled;
    }

    /**
     * Whether to enable file upload via HTTP (not intended for production use). This functionality is for development
     * to be able to reload Camel routes and code with source changes (if reload is enabled). If enabled then you can
     * upload/delete files via HTTP PUT/DELETE on context-path: /q/upload/{name}. You must also configure the
     * uploadSourceDir option.
     */
    public void setUploadEnabled(boolean uploadEnabled) {
        this.uploadEnabled = uploadEnabled;
    }

    public String getUploadSourceDir() {
        return uploadSourceDir;
    }

    /**
     * Source directory when upload is enabled.
     */
    public void setUploadSourceDir(String uploadSourceDir) {
        this.uploadSourceDir = uploadSourceDir;
    }

    /**
     * Whether embedded HTTP server is enabled. By default, the server is not enabled.
     */
    public HttpServerConfigurationProperties withEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Hostname to use for binding embedded HTTP server
     */
    public HttpServerConfigurationProperties withHost(String host) {
        this.host = host;
        return this;
    }

    /**
     * Port to use for binding embedded HTTP server
     */
    public HttpServerConfigurationProperties withPort(int port) {
        this.port = port;
        return this;
    }

    /**
     * Context-path to use for embedded HTTP server
     */
    public HttpServerConfigurationProperties withPath(String path) {
        this.path = path;
        return this;
    }

    /**
     * Maximum HTTP body size the embedded HTTP server can accept.
     */
    public HttpServerConfigurationProperties withMaxBodySize(long maxBodySize) {
        this.maxBodySize = maxBodySize;
        return this;
    }

    /**
     * Whether to use global SSL configuration for securing the embedded HTTP server.
     */
    public HttpServerConfigurationProperties withUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
        return this;
    }

    /**
     * Whether to enable developer console (not intended for production use). Dev console must also be enabled on
     * CamelContext. For example by setting camel.context.dev-console=true in application.properties, or via code
     * <tt>camelContext.setDevConsole(true);</tt> If enabled then you can access a basic developer console on
     * context-path: /q/dev.
     */
    public HttpServerConfigurationProperties withDevConsoleEnabled(boolean devConsoleEnabled) {
        this.devConsoleEnabled = devConsoleEnabled;
        return this;
    }

    /**
     * Whether to enable health-check console. If enabled then you can access health-check status on context-path:
     * /q/health
     */
    public HttpServerConfigurationProperties withHealthCheckEnabled(boolean healthCheckEnabled) {
        this.healthCheckEnabled = healthCheckEnabled;
        return this;
    }

    /**
     * Whether to enable jolokia. If enabled then you can access jolokia api on context-path: /q/jolokia
     */
    public HttpServerConfigurationProperties withJolokiaEnabled(boolean jolokiaEnabled) {
        this.jolokiaEnabled = jolokiaEnabled;
        return this;
    }

    /**
     * Whether to enable metrics. If enabled then you can access metrics on context-path: /q/metrics
     */
    public HttpServerConfigurationProperties withMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
        return this;
    }

    /**
     * Whether to enable file upload via HTTP (not intended for production use). This functionality is for development
     * to be able to reload Camel routes and code with source changes (if reload is enabled). If enabled then you can
     * upload/delete files via HTTP PUT/DELETE on context-path: /q/upload/{name}. You must also configure the
     * uploadSourceDir option.
     */
    public HttpServerConfigurationProperties withUploadEnabled(boolean uploadEnabled) {
        this.uploadEnabled = uploadEnabled;
        return this;
    }

    /**
     * Source directory when upload is enabled.
     */
    public HttpServerConfigurationProperties withUploadSourceDir(String uploadSourceDir) {
        this.uploadSourceDir = uploadSourceDir;
        return this;
    }

}
