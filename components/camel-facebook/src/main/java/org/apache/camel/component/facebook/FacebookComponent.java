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
package org.apache.camel.component.facebook;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.facebook.config.FacebookConfiguration;
import org.apache.camel.component.facebook.config.FacebookEndpointConfiguration;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.IntrospectionSupport;

/**
 * Represents the component that manages {@link FacebookEndpoint}.
 */
public class FacebookComponent extends UriEndpointComponent {

    private FacebookConfiguration configuration;

    public FacebookComponent() {
        this(new FacebookConfiguration());
    }

    public FacebookComponent(FacebookConfiguration configuration) {
        this(null, configuration);
    }

    public FacebookComponent(CamelContext context) {
        this(context, new FacebookConfiguration());
    }

    public FacebookComponent(CamelContext context, FacebookConfiguration configuration) {
        super(context, FacebookEndpoint.class);
        this.configuration = configuration;
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        FacebookEndpointConfiguration config = copyComponentProperties();
        final FacebookEndpoint endpoint = new FacebookEndpoint(uri, this, remaining, config);

        // set endpoint property inBody so that it's available in initState()
        setProperties(endpoint, parameters);

        // configure endpoint properties
        endpoint.configureProperties(parameters);

        // validate parameters
        validateParameters(uri, parameters, null);

        return endpoint;
    }

    private FacebookEndpointConfiguration copyComponentProperties() throws Exception {
        Map<String, Object> componentProperties = new HashMap<String, Object>();
        IntrospectionSupport.getProperties(configuration, componentProperties, null, false);

        // create endpoint configuration with component properties
        FacebookEndpointConfiguration config = new FacebookEndpointConfiguration();
        IntrospectionSupport.setProperties(config, componentProperties);
        return config;
    }

    public FacebookConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use the shared configuration. Properties of the shared configuration can also be set individually.
     */
    public void setConfiguration(FacebookConfiguration configuration) {
        this.configuration = configuration;
    }

    public String getOAuthAccessToken() {
        return configuration.getOAuthAccessToken();
    }

    /**
     * The user access token
     * @param oAuthAccessToken
     */
    public void setOAuthAccessToken(String oAuthAccessToken) {
        configuration.setOAuthAccessToken(oAuthAccessToken);
    }

    public String getOAuthAccessTokenURL() {
        return configuration.getOAuthAccessTokenURL();
    }

    /**
     * OAuth access token URL
     * @param oAuthAccessTokenURL
     */
    public void setOAuthAccessTokenURL(String oAuthAccessTokenURL) {
        configuration.setOAuthAccessTokenURL(oAuthAccessTokenURL);
    }

    public String getOAuthAppId() {
        return configuration.getOAuthAppId();
    }

    /**
     * The application Id
     * @param oAuthAppId
     */
    public void setOAuthAppId(String oAuthAppId) {
        configuration.setOAuthAppId(oAuthAppId);
    }

    public String getOAuthAppSecret() {
        return configuration.getOAuthAppSecret();
    }

    /**
     * The application Secret
     * @param oAuthAppSecret
     */
    public void setOAuthAppSecret(String oAuthAppSecret) {
        configuration.setOAuthAppSecret(oAuthAppSecret);
    }

    public String getOAuthAuthorizationURL() {
        return configuration.getOAuthAuthorizationURL();
    }

    /**
     * OAuth authorization URL
     * @param oAuthAuthorizationURL
     */
    public void setOAuthAuthorizationURL(String oAuthAuthorizationURL) {
        configuration.setOAuthAuthorizationURL(oAuthAuthorizationURL);
    }

    public String getClientURL() {
        return configuration.getClientURL();
    }

    /**
     * Facebook4J API client URL
     * @param clientURL
     */
    public void setClientURL(String clientURL) {
        configuration.setClientURL(clientURL);
    }

    public String getClientVersion() {
        return configuration.getClientVersion();
    }

    /**
     * Facebook4J client API version
     * @param clientVersion
     */
    public void setClientVersion(String clientVersion) {
        configuration.setClientVersion(clientVersion);
    }

    public Boolean getDebugEnabled() {
        return configuration.getDebugEnabled();
    }

    /**
     * Enables deubg output. Effective only with the embedded logger
     * @param debugEnabled
     */
    public void setDebugEnabled(Boolean debugEnabled) {
        configuration.setDebugEnabled(debugEnabled);
    }

    public Boolean getGzipEnabled() {
        return configuration.getGzipEnabled();
    }

    /**
     * Use Facebook GZIP encoding
     * @param gzipEnabled
     */
    public void setGzipEnabled(Boolean gzipEnabled) {
        configuration.setGzipEnabled(gzipEnabled);
    }

    public Integer getHttpConnectionTimeout() {
        return configuration.getHttpConnectionTimeout();
    }

    /**
     * Http connection timeout in milliseconds
     * @param httpConnectionTimeout
     */
    public void setHttpConnectionTimeout(Integer httpConnectionTimeout) {
        configuration.setHttpConnectionTimeout(httpConnectionTimeout);
    }

    public Integer getHttpDefaultMaxPerRoute() {
        return configuration.getHttpDefaultMaxPerRoute();
    }

    /**
     * HTTP maximum connections per route
     * @param httpDefaultMaxPerRoute
     */
    public void setHttpDefaultMaxPerRoute(Integer httpDefaultMaxPerRoute) {
        configuration.setHttpDefaultMaxPerRoute(httpDefaultMaxPerRoute);
    }

    public Integer getHttpMaxTotalConnections() {
        return configuration.getHttpMaxTotalConnections();
    }

    /**
     * HTTP maximum total connections
     * @param httpMaxTotalConnections
     */
    public void setHttpMaxTotalConnections(Integer httpMaxTotalConnections) {
        configuration.setHttpMaxTotalConnections(httpMaxTotalConnections);
    }

    public String getHttpProxyHost() {
        return configuration.getHttpProxyHost();
    }

    /**
     * HTTP proxy server host name
     * @param httpProxyHost
     */
    public void setHttpProxyHost(String httpProxyHost) {
        configuration.setHttpProxyHost(httpProxyHost);
    }

    public String getHttpProxyPassword() {
        return configuration.getHttpProxyPassword();
    }

    /**
     * HTTP proxy server password
     * @param httpProxyPassword
     */
    public void setHttpProxyPassword(String httpProxyPassword) {
        configuration.setHttpProxyPassword(httpProxyPassword);
    }

    public Integer getHttpProxyPort() {
        return configuration.getHttpProxyPort();
    }

    /**
     * HTTP proxy server port
     * @param httpProxyPort
     */
    public void setHttpProxyPort(Integer httpProxyPort) {
        configuration.setHttpProxyPort(httpProxyPort);
    }

    public String getHttpProxyUser() {
        return configuration.getHttpProxyUser();
    }

    /**
     * HTTP proxy server user name
     * @param httpProxyUser
     */
    public void setHttpProxyUser(String httpProxyUser) {
        configuration.setHttpProxyUser(httpProxyUser);
    }

    public Integer getHttpReadTimeout() {
        return configuration.getHttpReadTimeout();
    }

    /**
     * Http read timeout in milliseconds
     * @param httpReadTimeout
     */
    public void setHttpReadTimeout(Integer httpReadTimeout) {
        configuration.setHttpReadTimeout(httpReadTimeout);
    }

    public Integer getHttpRetryCount() {
        return configuration.getHttpRetryCount();
    }

    /**
     * Number of HTTP retries
     * @param httpRetryCount
     */
    public void setHttpRetryCount(Integer httpRetryCount) {
        configuration.setHttpRetryCount(httpRetryCount);
    }

    public Integer getHttpRetryIntervalSeconds() {
        return configuration.getHttpRetryIntervalSeconds();
    }

    /**
     * HTTP retry interval in seconds
     * @param httpRetryIntervalSeconds
     */
    public void setHttpRetryIntervalSeconds(Integer httpRetryIntervalSeconds) {
        configuration.setHttpRetryIntervalSeconds(httpRetryIntervalSeconds);
    }

    public Integer getHttpStreamingReadTimeout() {
        return configuration.getHttpStreamingReadTimeout();
    }

    /**
     * HTTP streaming read timeout in milliseconds
     * @param httpStreamingReadTimeout
     */
    public void setHttpStreamingReadTimeout(Integer httpStreamingReadTimeout) {
        configuration.setHttpStreamingReadTimeout(httpStreamingReadTimeout);
    }

    public Boolean getJsonStoreEnabled() {
        return configuration.getJsonStoreEnabled();
    }

    /**
     * If set to true, raw JSON forms will be stored in DataObjectFactory
     * @param jsonStoreEnabled
     */
    public void setJsonStoreEnabled(Boolean jsonStoreEnabled) {
        configuration.setJsonStoreEnabled(jsonStoreEnabled);
    }

    public Boolean getMbeanEnabled() {
        return configuration.getMbeanEnabled();
    }

    /**
     * If set to true, Facebook4J mbean will be registerd
     * @param mbeanEnabled
     */
    public void setMbeanEnabled(Boolean mbeanEnabled) {
        configuration.setMbeanEnabled(mbeanEnabled);
    }

    public String getOAuthPermissions() {
        return configuration.getOAuthPermissions();
    }

    /**
     * Default OAuth permissions. Comma separated permission names.
     * See https://developers.facebook.com/docs/reference/login/#permissions for the detail
     * @param oAuthPermissions
     */
    public void setOAuthPermissions(String oAuthPermissions) {
        configuration.setOAuthPermissions(oAuthPermissions);
    }

    public Boolean getPrettyDebugEnabled() {
        return configuration.getPrettyDebugEnabled();
    }

    /**
     * Prettify JSON debug output if set to true
     * @param prettyDebugEnabled
     */
    public void setPrettyDebugEnabled(Boolean prettyDebugEnabled) {
        configuration.setPrettyDebugEnabled(prettyDebugEnabled);
    }

    public String getRestBaseURL() {
        return configuration.getRestBaseURL();
    }

    /**
     * API base URL
     * @param restBaseURL
     */
    public void setRestBaseURL(String restBaseURL) {
        configuration.setRestBaseURL(restBaseURL);
    }

    public Boolean getUseSSL() {
        return configuration.getUseSSL();
    }

    /**
     * Use SSL
     * @param useSSL
     */
    public void setUseSSL(Boolean useSSL) {
        configuration.setUseSSL(useSSL);
    }

    public String getVideoBaseURL() {
        return configuration.getVideoBaseURL();
    }

    /**
     * Video API base URL
     * @param videoBaseURL
     */
    public void setVideoBaseURL(String videoBaseURL) {
        configuration.setVideoBaseURL(videoBaseURL);
    }
}
