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
@Configurer(extended = true)
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

    private boolean infoEnabled;
    private boolean staticEnabled;
    @Metadata(defaultValue = "/")
    private String staticContextPath = "/";
    private boolean devConsoleEnabled;
    private boolean healthCheckEnabled;
    private boolean jolokiaEnabled;
    private boolean metricsEnabled;
    private boolean uploadEnabled;
    private String uploadSourceDir;
    private boolean downloadEnabled;
    private boolean sendEnabled;

    @Metadata(label = "security")
    private boolean authenticationEnabled;
    @Metadata(label = "security")
    private String authenticationPath;
    @Metadata(label = "security")
    private String basicPropertiesFile;
    @Metadata(label = "security")
    private String jwtKeystoreType;
    @Metadata(label = "security")
    private String jwtKeystorePath;
    @Metadata(label = "security", secret = true)
    private String jwtKeystorePassword;

    @Metadata(defaultValue = "/q/health")
    private String healthPath = "/q/health";

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

    public boolean isInfoEnabled() {
        return infoEnabled;
    }

    /**
     * Whether to enable info console. If enabled then you can see some basic Camel information at /q/info
     */
    public void setInfoEnabled(boolean infoEnabled) {
        this.infoEnabled = infoEnabled;
    }

    public boolean isStaticEnabled() {
        return staticEnabled;
    }

    /**
     * Whether serving static files is enabled. If enabled then Camel can host html/js and other web files that makes it
     * possible to include small web applications.
     */
    public void setStaticEnabled(boolean staticEnabled) {
        this.staticEnabled = staticEnabled;
    }

    public String getStaticContextPath() {
        return staticContextPath;
    }

    /**
     * The context-path to use for serving static content. By default, the root path is used. And if there is an
     * index.html page then this is automatically loaded.
     */
    public void setStaticContextPath(String staticContextPath) {
        this.staticContextPath = staticContextPath;
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
     * /q/health (default)
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
     * Whether to enable metrics. If enabled then you can access metrics on context-path: /q/metrics (default)
     */
    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }

    public String getHealthPath() {
        return healthPath;
    }

    /**
     * The path endpoint used to expose the health status
     */
    public void setHealthPath(String healthPath) {
        this.healthPath = healthPath;
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

    public boolean isDownloadEnabled() {
        return downloadEnabled;
    }

    /**
     * Whether to enable file download via HTTP. This makes it possible to browse and download resource source files
     * such as Camel XML or YAML routes. Only enable this for development, troubleshooting or special situations for
     * management and monitoring.
     */
    public void setDownloadEnabled(boolean downloadEnabled) {
        this.downloadEnabled = downloadEnabled;
    }

    public boolean isSendEnabled() {
        return sendEnabled;
    }

    /**
     * Whether to enable sending messages to Camel via HTTP. This makes it possible to use Camel to send messages to
     * Camel endpoint URIs via HTTP.
     */
    public void setSendEnabled(boolean sendEnabled) {
        this.sendEnabled = sendEnabled;
    }

    public boolean isAuthenticationEnabled() {
        return authenticationEnabled;
    }

    /**
     * Whether to enable HTTP authentication for embedded server (for standalone applications; not Spring Boot or
     * Quarkus).
     */
    public void setAuthenticationEnabled(boolean authenticationEnabled) {
        this.authenticationEnabled = authenticationEnabled;
    }

    public String getAuthenticationPath() {
        return authenticationPath;
    }

    /**
     * Set HTTP url path of embedded server that is protected by authentication configuration.
     */
    public void setAuthenticationPath(String authenticationPath) {
        this.authenticationPath = authenticationPath;
    }

    public String getBasicPropertiesFile() {
        return basicPropertiesFile;
    }

    /**
     * Name of the file that contains basic authentication info for Vert.x file auth provider.
     */
    public void setBasicPropertiesFile(String basicPropertiesFile) {
        this.basicPropertiesFile = basicPropertiesFile;
    }

    public String getJwtKeystoreType() {
        return jwtKeystoreType;
    }

    /**
     * Type of the keystore used for JWT tokens validation (jks, pkcs12, etc.).
     */
    public void setJwtKeystoreType(String jwtKeystoreType) {
        this.jwtKeystoreType = jwtKeystoreType;
    }

    public String getJwtKeystorePath() {
        return jwtKeystorePath;
    }

    /**
     * Path to the keystore file used for JWT tokens validation.
     */
    public void setJwtKeystorePath(String jwtKeystorePath) {
        this.jwtKeystorePath = jwtKeystorePath;
    }

    public String getJwtKeystorePassword() {
        return jwtKeystorePassword;
    }

    /**
     * Password from the keystore used for JWT tokens validation.
     */
    public void setJwtKeystorePassword(String jwtKeystorePassword) {
        this.jwtKeystorePassword = jwtKeystorePassword;
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
     * Whether to enable info console. If enabled then you can see some basic Camel information at /q/info
     */
    public HttpServerConfigurationProperties withInfoEnabled(boolean infoEnabled) {
        this.infoEnabled = infoEnabled;
        return this;
    }

    /**
     * Whether serving static files is enabled. If enabled then Camel can host html/js and other web files that makes it
     * possible to include small web applications.
     */
    public HttpServerConfigurationProperties withStaticEnabled(boolean staticEnabled) {
        this.staticEnabled = staticEnabled;
        return this;
    }

    /**
     * The context-path to use for serving static content. By default, the root path is used. And if there is an
     * index.html page then this is automatically loaded.
     */
    public HttpServerConfigurationProperties withStaticContextPath(String staticContextPath) {
        this.staticContextPath = staticContextPath;
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
     * /q/health (default)
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
     * Whether to enable metrics. If enabled then you can access metrics on context-path: /q/metrics (default)
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

    /**
     * Whether to enable file download via HTTP. This makes it possible to browse and download resource source files
     * such as Camel XML or YAML routes. Only enable this for development, troubleshooting or special situations for
     * management and monitoring.
     */
    public HttpServerConfigurationProperties withDownloadEnabled(boolean downloadEnabled) {
        this.downloadEnabled = downloadEnabled;
        return this;
    }

    /**
     * Whether to enable sending messages to Camel via HTTP. This makes it possible to use Camel to send messages to
     * Camel endpoint URIs via HTTP.
     */
    public HttpServerConfigurationProperties withSendEnabled(boolean sendEnabled) {
        this.sendEnabled = sendEnabled;
        return this;
    }

    /**
     * Whether to enable HTTP authentication for embedded server (for standalone applications; not Spring Boot or
     * Quarkus).
     */
    public HttpServerConfigurationProperties withAuthenticationEnabled(boolean authenticationEnabled) {
        this.authenticationEnabled = authenticationEnabled;
        return this;
    }

    /**
     * Set HTTP url path of embedded server that is protected by authentication configuration.
     */
    public HttpServerConfigurationProperties withAuthenticationPath(String authenticationPath) {
        this.authenticationPath = authenticationPath;
        return this;
    }

    /**
     * Name of the file that contains basic authentication info for Vert.x file auth provider.
     */
    public HttpServerConfigurationProperties withBasicPropertiesFile(String basicPropertiesFile) {
        this.basicPropertiesFile = basicPropertiesFile;
        return this;
    }

    /**
     * Type of the keystore used for JWT tokens validation (jks, pkcs12, etc.).
     */
    public HttpServerConfigurationProperties withJwtKeystoreType(String jwtKeystoreType) {
        this.jwtKeystoreType = jwtKeystoreType;
        return this;
    }

    /**
     * Path to the keystore file used for JWT tokens validation.
     */
    public HttpServerConfigurationProperties withJwtKeystorePath(String jwtKeystorePath) {
        this.jwtKeystorePath = jwtKeystorePath;
        return this;
    }

    /**
     * Password from the keystore used for JWT tokens validation.
     */
    public HttpServerConfigurationProperties withJwtKeystorePassword(String jwtKeystorePassword) {
        this.jwtKeystorePassword = jwtKeystorePassword;
        return this;
    }

}
