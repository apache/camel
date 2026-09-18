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

    private boolean staticEnabled;
    private String staticSourceDir;
    @Metadata(defaultValue = "/")
    private String staticContextPath = "/";
    @Metadata(label = "advanced", defaultValue = "true")
    private boolean fileUploadEnabled = true;
    @Metadata(label = "advanced")
    private String fileUploadDirectory;
    @Metadata(label = "advanced")
    private Long maxBodySize;

    @Metadata(label = "security")
    private boolean useGlobalSslContextParameters;
    @Metadata(label = "security")
    private boolean authenticationEnabled;
    @Metadata(label = "security")
    private String authenticationPath;
    @Metadata(label = "security")
    private String authenticationRealm;
    @Metadata(label = "security")
    private String basicPropertiesFile;
    @Metadata(label = "security")
    private String jwtKeystoreType;
    @Metadata(label = "security")
    private String jwtKeystorePath;
    @Metadata(label = "security", security = "secret")
    private String jwtKeystorePassword;
    @Metadata(label = "security")
    private String jwtIssuer;
    @Metadata(label = "security")
    private String jwtAudience;
    @Metadata(label = "security", defaultValue = "false", security = "insecure:dev")
    private boolean jwtAllowMissingIssuerAndAudience;

    @Metadata
    private boolean mcpEnabled;
    @Metadata(defaultValue = "http", enums = "http,stdio")
    private String mcpTransport = "http";
    @Metadata
    private String mcpTags;
    @Metadata(defaultValue = "20000")
    private long mcpToolTimeout = 20000;
    @Metadata(defaultValue = "20000")
    private long mcpResourceTimeout = 20000;
    @Metadata(defaultValue = "/mcp")
    private String mcpPath = "/mcp";
    @Metadata(defaultValue = "30000")
    private long mcpSessionKeepAliveInterval = 30000;
    @Metadata(defaultValue = "300000")
    private long mcpSessionIdleTtl = 300000;
    @Metadata
    private String mcpServerName;
    @Metadata
    private String mcpServerTitle;
    @Metadata
    private String mcpServerDescription;
    @Metadata
    private String mcpServerWebsiteUrl;
    @Metadata
    private String mcpInstructions;
    @Metadata
    private String mcpServerIcons;

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
     * Port to use for binding embedded HTTP server. Use 0 to dynamic assign a free random port number.
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

    public boolean isFileUploadEnabled() {
        return fileUploadEnabled;
    }

    /**
     * Whether to enable file uploads being supported (such as POST multipart/form-data) and stored into a temporary
     * directory.
     */
    public void setFileUploadEnabled(boolean fileUploadEnabled) {
        this.fileUploadEnabled = fileUploadEnabled;
    }

    public String getFileUploadDirectory() {
        return fileUploadDirectory;
    }

    /**
     * Directory to temporary store file uploads while Camel routes the incoming request.
     *
     * If no directory has been explicit configured, then a temporary directory is created in the java.io.tmpdir
     * directory.
     */
    public void setFileUploadDirectory(String fileUploadDirectory) {
        this.fileUploadDirectory = fileUploadDirectory;
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

    public String getStaticSourceDir() {
        return staticSourceDir;
    }

    /**
     * Additional directory that holds static content when static is enabled.
     */
    public void setStaticSourceDir(String staticSourceDir) {
        this.staticSourceDir = staticSourceDir;
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

    public String getAuthenticationRealm() {
        return authenticationRealm;
    }

    /**
     * Sets the authentication realm
     */
    public void setAuthenticationRealm(String authenticationRealm) {
        this.authenticationRealm = authenticationRealm;
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

    public String getJwtIssuer() {
        return jwtIssuer;
    }

    /**
     * Expected JWT issuer (iss claim) for token validation. When set, tokens whose issuer does not match are rejected.
     */
    public void setJwtIssuer(String jwtIssuer) {
        this.jwtIssuer = jwtIssuer;
    }

    public String getJwtAudience() {
        return jwtAudience;
    }

    /**
     * Expected JWT audience (aud claim) for token validation. Multiple values can be separated by comma. When set,
     * tokens whose audience does not contain any of the configured values are rejected.
     */
    public void setJwtAudience(String jwtAudience) {
        this.jwtAudience = jwtAudience;
    }

    public boolean isJwtAllowMissingIssuerAndAudience() {
        return jwtAllowMissingIssuerAndAudience;
    }

    /**
     * Whether JWT authentication is allowed to run without an expected issuer or audience. By default the embedded
     * server fails to start when a JWT keystore is configured but neither jwtIssuer nor jwtAudience is set, because the
     * tokens would only be checked for signature and expiry. Enable this to accept that and validate signature and
     * expiry only.
     */
    public void setJwtAllowMissingIssuerAndAudience(boolean jwtAllowMissingIssuerAndAudience) {
        this.jwtAllowMissingIssuerAndAudience = jwtAllowMissingIssuerAndAudience;
    }

    public boolean isMcpEnabled() {
        return mcpEnabled;
    }

    /**
     * Whether to expose ai-tool routes as MCP tools and ai-resource routes as MCP resources. Requires camel-mcp-server
     * on the classpath. Use {@link #setMcpTransport(String)} to choose streamable HTTP (default) or stdio for IDE
     * subprocess integration. By default, the MCP server is not enabled.
     */
    public void setMcpEnabled(boolean mcpEnabled) {
        this.mcpEnabled = mcpEnabled;
    }

    public String getMcpTransport() {
        return mcpTransport;
    }

    /**
     * MCP transport for ai-tool routes: {@code http} (streamable HTTP on the embedded server, default) or {@code stdio}
     * (JSON-RPC on process stdin/stdout for local agent subprocesses).
     */
    public void setMcpTransport(String mcpTransport) {
        this.mcpTransport = mcpTransport;
    }

    public String getMcpTags() {
        return mcpTags;
    }

    /**
     * Comma-separated list of tag patterns selecting the ai-tool routes to expose as MCP tools and the ai-resource
     * routes to expose as MCP resources. Matching is case-insensitive and supports exact match, wildcard prefix
     * ({@code foo*}), and {@code *} to match all tags. Only routes registered under a matching tag are exposed; the
     * untagged default pools are never exposed. When not set, nothing is exposed.
     */
    public void setMcpTags(String mcpTags) {
        this.mcpTags = mcpTags;
    }

    public long getMcpToolTimeout() {
        return mcpToolTimeout;
    }

    /**
     * Per-call MCP tool execution timeout in milliseconds. A call exceeding the timeout returns an error result to the
     * MCP client; the underlying route keeps running until it completes on its own.
     */
    public void setMcpToolTimeout(long mcpToolTimeout) {
        this.mcpToolTimeout = mcpToolTimeout;
    }

    public long getMcpResourceTimeout() {
        return mcpResourceTimeout;
    }

    /**
     * Per-read MCP resource execution timeout in milliseconds. A read exceeding the timeout returns an error to the MCP
     * client; the underlying route keeps running until it completes on its own.
     */
    public void setMcpResourceTimeout(long mcpResourceTimeout) {
        this.mcpResourceTimeout = mcpResourceTimeout;
    }

    public String getMcpPath() {
        return mcpPath;
    }

    /**
     * HTTP path where the MCP endpoint is served.
     */
    public void setMcpPath(String mcpPath) {
        this.mcpPath = mcpPath;
    }

    public String getMcpServerName() {
        return mcpServerName;
    }

    /**
     * MCP server name advertised to clients. Defaults to the CamelContext name.
     */
    public void setMcpServerName(String mcpServerName) {
        this.mcpServerName = mcpServerName;
    }

    public String getMcpServerTitle() {
        return mcpServerTitle;
    }

    /**
     * MCP server display title advertised to clients in {@code serverInfo.title}.
     */
    public void setMcpServerTitle(String mcpServerTitle) {
        this.mcpServerTitle = mcpServerTitle;
    }

    public String getMcpServerDescription() {
        return mcpServerDescription;
    }

    /**
     * MCP server description advertised to clients in {@code serverInfo.description}.
     */
    public void setMcpServerDescription(String mcpServerDescription) {
        this.mcpServerDescription = mcpServerDescription;
    }

    public String getMcpServerWebsiteUrl() {
        return mcpServerWebsiteUrl;
    }

    /**
     * MCP server website URL advertised to clients in {@code serverInfo.websiteUrl}.
     */
    public void setMcpServerWebsiteUrl(String mcpServerWebsiteUrl) {
        this.mcpServerWebsiteUrl = mcpServerWebsiteUrl;
    }

    public String getMcpInstructions() {
        return mcpInstructions;
    }

    /**
     * Top-level MCP instructions returned to clients on initialize.
     */
    public void setMcpInstructions(String mcpInstructions) {
        this.mcpInstructions = mcpInstructions;
    }

    public String getMcpServerIcons() {
        return mcpServerIcons;
    }

    /**
     * JSON array of MCP server icons advertised to clients in {@code serverInfo.icons}. Each entry must include a
     * {@code src} URL and may include {@code mimeType}, {@code sizes} and {@code theme}.
     */
    public void setMcpServerIcons(String mcpServerIcons) {
        this.mcpServerIcons = mcpServerIcons;
    }

    public long getMcpSessionKeepAliveInterval() {
        return mcpSessionKeepAliveInterval;
    }

    /**
     * Keep-alive ping interval in milliseconds for MCP sessions on the Vert.x streamable transport. Dead sessions are
     * evicted after consecutive ping failures. {@code 0} disables keep-alive pings.
     */
    public void setMcpSessionKeepAliveInterval(long mcpSessionKeepAliveInterval) {
        this.mcpSessionKeepAliveInterval = mcpSessionKeepAliveInterval;
    }

    public long getMcpSessionIdleTtl() {
        return mcpSessionIdleTtl;
    }

    /**
     * Idle TTL in milliseconds for MCP sessions on the Vert.x streamable transport. Sessions with no activity for
     * longer than this interval are evicted. {@code 0} disables idle eviction.
     */
    public void setMcpSessionIdleTtl(long mcpSessionIdleTtl) {
        this.mcpSessionIdleTtl = mcpSessionIdleTtl;
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
     * Port to use for binding embedded HTTP server. Use 0 to dynamic assign a free random port number.
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
     * Whether to enable file uploads being supported (such as POST multipart/form-data) and stored into a temporary
     * directory.
     */
    public HttpServerConfigurationProperties withFileUploadEnabled(boolean fileUploadEnabled) {
        this.fileUploadEnabled = fileUploadEnabled;
        return this;
    }

    /**
     * Directory to temporary store file uploads while Camel routes the incoming request.
     *
     * If no directory has been explicit configured, then a temporary directory is created in the java.io.tmpdir
     * directory.
     */
    public HttpServerConfigurationProperties withFileUploadDirectory(String fileUploadDirectory) {
        this.fileUploadDirectory = fileUploadDirectory;
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
     * Additional directory that holds static content when static is enabled.
     */
    public HttpServerConfigurationProperties withStaticSourceDir(String staticSourceDir) {
        this.staticSourceDir = staticSourceDir;
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
     * Sets the authentication realm
     */
    public HttpServerConfigurationProperties withAuthenticationRealm(String authenticationRealm) {
        this.authenticationRealm = authenticationRealm;
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

    /**
     * Expected JWT issuer (iss claim) for token validation. When set, tokens whose issuer does not match are rejected.
     */
    public HttpServerConfigurationProperties withJwtIssuer(String jwtIssuer) {
        this.jwtIssuer = jwtIssuer;
        return this;
    }

    /**
     * Expected JWT audience (aud claim) for token validation. Multiple values can be separated by comma. When set,
     * tokens whose audience does not contain any of the configured values are rejected.
     */
    public HttpServerConfigurationProperties withJwtAudience(String jwtAudience) {
        this.jwtAudience = jwtAudience;
        return this;
    }

    /**
     * Whether JWT authentication is allowed to run without an expected issuer or audience. By default the embedded
     * server fails to start when a JWT keystore is configured but neither jwtIssuer nor jwtAudience is set, because the
     * tokens would only be checked for signature and expiry. Enable this to accept that and validate signature and
     * expiry only.
     */
    public HttpServerConfigurationProperties withJwtAllowMissingIssuerAndAudience(
            boolean jwtAllowMissingIssuerAndAudience) {
        this.jwtAllowMissingIssuerAndAudience = jwtAllowMissingIssuerAndAudience;
        return this;
    }

    /**
     * Whether to expose ai-tool routes as MCP tools and ai-resource routes as MCP resources. Requires camel-mcp-server
     * on the classpath. Use {@link #setMcpTransport(String)} to choose streamable HTTP (default) or stdio for IDE
     * subprocess integration. By default, the MCP server is not enabled.
     */
    public HttpServerConfigurationProperties withMcpEnabled(boolean mcpEnabled) {
        this.mcpEnabled = mcpEnabled;
        return this;
    }

    /**
     * MCP transport for ai-tool routes: {@code http} (default) or {@code stdio}.
     */
    public HttpServerConfigurationProperties withMcpTransport(String mcpTransport) {
        this.mcpTransport = mcpTransport;
        return this;
    }

    /**
     * Comma-separated list of tag patterns selecting the ai-tool routes to expose as MCP tools and the ai-resource
     * routes to expose as MCP resources. Matching is case-insensitive and supports exact match, wildcard prefix
     * ({@code foo*}), and {@code *} to match all tags. Only routes registered under a matching tag are exposed; the
     * untagged default pools are never exposed. When not set, nothing is exposed.
     */
    public HttpServerConfigurationProperties withMcpTags(String mcpTags) {
        this.mcpTags = mcpTags;
        return this;
    }

    /**
     * Per-call MCP tool execution timeout in milliseconds. A call exceeding the timeout returns an error result to the
     * MCP client; the underlying route keeps running until it completes on its own.
     */
    public HttpServerConfigurationProperties withMcpToolTimeout(long mcpToolTimeout) {
        this.mcpToolTimeout = mcpToolTimeout;
        return this;
    }

    /**
     * Per-read MCP resource execution timeout in milliseconds. A read exceeding the timeout returns an error to the MCP
     * client; the underlying route keeps running until it completes on its own.
     */
    public HttpServerConfigurationProperties withMcpResourceTimeout(long mcpResourceTimeout) {
        this.mcpResourceTimeout = mcpResourceTimeout;
        return this;
    }

    /**
     * HTTP path where the MCP endpoint is served.
     */
    public HttpServerConfigurationProperties withMcpPath(String mcpPath) {
        this.mcpPath = mcpPath;
        return this;
    }

    /**
     * MCP server name advertised to clients. Defaults to the CamelContext name.
     */
    public HttpServerConfigurationProperties withMcpServerName(String mcpServerName) {
        this.mcpServerName = mcpServerName;
        return this;
    }

    /**
     * MCP server display title advertised to clients in {@code serverInfo.title}.
     */
    public HttpServerConfigurationProperties withMcpServerTitle(String mcpServerTitle) {
        this.mcpServerTitle = mcpServerTitle;
        return this;
    }

    /**
     * MCP server description advertised to clients in {@code serverInfo.description}.
     */
    public HttpServerConfigurationProperties withMcpServerDescription(String mcpServerDescription) {
        this.mcpServerDescription = mcpServerDescription;
        return this;
    }

    /**
     * MCP server website URL advertised to clients in {@code serverInfo.websiteUrl}.
     */
    public HttpServerConfigurationProperties withMcpServerWebsiteUrl(String mcpServerWebsiteUrl) {
        this.mcpServerWebsiteUrl = mcpServerWebsiteUrl;
        return this;
    }

    /**
     * Top-level MCP instructions returned to clients on initialize.
     */
    public HttpServerConfigurationProperties withMcpInstructions(String mcpInstructions) {
        this.mcpInstructions = mcpInstructions;
        return this;
    }

    /**
     * JSON array of MCP server icons advertised to clients in {@code serverInfo.icons}.
     */
    public HttpServerConfigurationProperties withMcpServerIcons(String mcpServerIcons) {
        this.mcpServerIcons = mcpServerIcons;
        return this;
    }

    /**
     * Keep-alive ping interval in milliseconds for MCP sessions on the Vert.x streamable transport.
     */
    public HttpServerConfigurationProperties withMcpSessionKeepAliveInterval(long mcpSessionKeepAliveInterval) {
        this.mcpSessionKeepAliveInterval = mcpSessionKeepAliveInterval;
        return this;
    }

    /**
     * Idle TTL in milliseconds for MCP sessions on the Vert.x streamable transport.
     */
    public HttpServerConfigurationProperties withMcpSessionIdleTtl(long mcpSessionIdleTtl) {
        this.mcpSessionIdleTtl = mcpSessionIdleTtl;
        return this;
    }

}
