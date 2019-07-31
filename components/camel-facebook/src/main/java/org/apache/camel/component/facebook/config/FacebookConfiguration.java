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
package org.apache.camel.component.facebook.config;

import facebook4j.Facebook;
import facebook4j.FacebookException;
import facebook4j.FacebookFactory;
import facebook4j.auth.OAuthAuthorization;
import facebook4j.conf.Configuration;
import facebook4j.conf.ConfigurationBuilder;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Facebook component configuration.
 */
@UriParams
public class FacebookConfiguration implements Cloneable {

    private static final Logger LOG = LoggerFactory.getLogger(FacebookConfiguration.class);

    @UriParam(label = "security", secret = true)
    private String oAuthAppId;
    @UriParam(label = "security", secret = true)
    private String oAuthAppSecret;
    @UriParam(label = "security", secret = true)
    private String oAuthAccessToken;
    @UriParam(label = "security", defaultValue = "https://www.facebook.com/dialog/oauth")
    private String oAuthAuthorizationURL;
    @UriParam(label = "security")
    private String oAuthPermissions;
    @UriParam(label = "security", defaultValue = "https://graph.facebook.com/oauth/access_token")
    private String oAuthAccessTokenURL;
    @UriParam
    private String clientURL;
    @UriParam
    private String clientVersion;
    @UriParam(defaultValue = "false")
    private Boolean debugEnabled;
    @UriParam(defaultValue = "true")
    private Boolean gzipEnabled;
    @UriParam(defaultValue = "20000")
    private Integer httpConnectionTimeout;
    @UriParam(defaultValue = "2")
    private Integer httpDefaultMaxPerRoute;
    @UriParam(defaultValue = "20")
    private Integer httpMaxTotalConnections;
    @UriParam(label = "proxy")
    private String httpProxyHost;
    @UriParam(label = "proxy")
    private String httpProxyPassword;
    @UriParam(label = "proxy")
    private Integer httpProxyPort;
    @UriParam(label = "proxy")
    private String httpProxyUser;
    @UriParam(defaultValue = "120000")
    private Integer httpReadTimeout;
    @UriParam(defaultValue = "0")
    private Integer httpRetryCount;
    @UriParam(defaultValue = "5")
    private Integer httpRetryIntervalSeconds;
    @UriParam(defaultValue = "40000")
    private Integer httpStreamingReadTimeout;
    @UriParam(defaultValue = "false")
    private Boolean jsonStoreEnabled;
    @UriParam(defaultValue = "false")
    private Boolean mbeanEnabled;
    @UriParam(defaultValue = "false")
    private Boolean prettyDebugEnabled;
    @UriParam(defaultValue = "https://graph.facebook.com/")
    private String restBaseURL;
    @UriParam(defaultValue = "true")
    private Boolean useSSL;
    @UriParam(defaultValue = "https://graph-video.facebook.com/")
    private String videoBaseURL;

    // cached FaceBook instance, is created in getFacebook by endpoint producers and consumers
    private Facebook facebook;

    public Configuration getConfiguration() {
        final ConfigurationBuilder builder = new ConfigurationBuilder();
        // apply builder settings

        if (oAuthAccessToken != null) {
            builder.setOAuthAccessToken(oAuthAccessToken);
        }
        if (oAuthAccessTokenURL != null) {
            builder.setOAuthAccessTokenURL(oAuthAccessTokenURL);
        }
        if (oAuthAppId != null) {
            builder.setOAuthAppId(oAuthAppId);
        }
        if (oAuthAppSecret != null) {
            builder.setOAuthAppSecret(oAuthAppSecret);
        }
        if (oAuthAuthorizationURL != null) {
            builder.setOAuthAuthorizationURL(oAuthAuthorizationURL);
        }
        if (oAuthPermissions != null) {
            builder.setOAuthPermissions(oAuthPermissions);
        }

        if (clientURL != null) {
            builder.setClientURL(clientURL);
        }
        if (clientVersion != null) {
            builder.setClientVersion(clientVersion);
        }
        if (debugEnabled != null) {
            builder.setDebugEnabled(debugEnabled);
        }
        if (gzipEnabled != null) {
            builder.setGZIPEnabled(gzipEnabled);
        }
        if (httpConnectionTimeout != null) {
            builder.setHttpConnectionTimeout(httpConnectionTimeout);
        }
        if (httpDefaultMaxPerRoute != null) {
            builder.setHttpDefaultMaxPerRoute(httpDefaultMaxPerRoute);
        }
        if (httpMaxTotalConnections != null) {
            builder.setHttpMaxTotalConnections(httpMaxTotalConnections);
        }
        if (httpProxyHost != null) {
            builder.setHttpProxyHost(httpProxyHost);
        }
        if (httpProxyPassword != null) {
            builder.setHttpProxyPassword(httpProxyPassword);
        }
        if (httpProxyPort != null) {
            builder.setHttpProxyPort(httpProxyPort);
        }
        if (httpProxyUser != null) {
            builder.setHttpProxyUser(httpProxyUser);
        }
        if (httpReadTimeout != null) {
            builder.setHttpReadTimeout(httpReadTimeout);
        }
        if (httpRetryCount != null) {
            builder.setHttpRetryCount(httpRetryCount);
        }
        if (httpRetryIntervalSeconds != null) {
            builder.setHttpRetryIntervalSeconds(httpRetryIntervalSeconds);
        }
        if (httpStreamingReadTimeout != null) {
            builder.setHttpStreamingReadTimeout(httpStreamingReadTimeout);
        }
        if (jsonStoreEnabled != null) {
            builder.setJSONStoreEnabled(jsonStoreEnabled);
        }
        if (mbeanEnabled != null) {
            builder.setMBeanEnabled(mbeanEnabled);
        }
        if (prettyDebugEnabled != null) {
            builder.setPrettyDebugEnabled(prettyDebugEnabled);
        }
        if (restBaseURL != null) {
            builder.setRestBaseURL(restBaseURL);
        }
        if (useSSL != null) {
            builder.setUseSSL(useSSL);
        }
        if (videoBaseURL != null) {
            builder.setVideoBaseURL(videoBaseURL);
        }

        return builder.build();
    }

    /**
     * Returns {@link Facebook} instance. If needed, creates one from configuration.
     * @return {@link Facebook} instance
     */
    public Facebook getFacebook() throws FacebookException {
        if (facebook == null) {
            final Configuration configuration = getConfiguration();
            FacebookFactory factory = new FacebookFactory(configuration);
            if (this.oAuthAccessToken == null) {
                // app login
                facebook = factory.getInstance(new OAuthAuthorization(configuration));
                // also get the App access token
                facebook.getOAuthAppAccessToken();
                LOG.warn("Login with app id and secret, access to some APIs is restricted!");
            } else {
                // user login with token
                facebook = factory.getInstance();
                // verify the access token
                facebook.getOAuthAccessToken();
                LOG.debug("Login with app id, secret and token, all APIs accessible");
            }
        }
        return facebook;
    }

    public FacebookConfiguration copy() throws CloneNotSupportedException {
        final FacebookConfiguration copy = (FacebookConfiguration) clone();
        // do not copy facebook instance!!!
        copy.facebook = null;
        return copy;
    }

    public String getOAuthAccessToken() {
        return oAuthAccessToken;
    }

    /**
     * The user access token
     */
    public void setOAuthAccessToken(String oAuthAccessToken) {
        this.oAuthAccessToken = oAuthAccessToken;
    }

    public String getOAuthAccessTokenURL() {
        return oAuthAccessTokenURL;
    }

    /**
     * OAuth access token URL
     */
    public void setOAuthAccessTokenURL(String oAuthAccessTokenURL) {
        this.oAuthAccessTokenURL = oAuthAccessTokenURL;
    }

    public String getOAuthAppId() {
        return oAuthAppId;
    }

    /**
     * The application Id
     */
    public void setOAuthAppId(String oAuthAppId) {
        this.oAuthAppId = oAuthAppId;
    }

    public String getOAuthAppSecret() {
        return oAuthAppSecret;
    }

    /**
     * The application Secret
     */
    public void setOAuthAppSecret(String oAuthAppSecret) {
        this.oAuthAppSecret = oAuthAppSecret;
    }

    public String getOAuthAuthorizationURL() {
        return oAuthAuthorizationURL;
    }

    /**
     * OAuth authorization URL
     */
    public void setOAuthAuthorizationURL(String oAuthAuthorizationURL) {
        this.oAuthAuthorizationURL = oAuthAuthorizationURL;
    }

    public String getClientURL() {
        return clientURL;
    }

    /**
     * Facebook4J API client URL
     */
    public void setClientURL(String clientURL) {
        this.clientURL = clientURL;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    /**
     * Facebook4J client API version
     */
    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    public Boolean getDebugEnabled() {
        return debugEnabled;
    }

    /**
     * Enables deubg output. Effective only with the embedded logger
     */
    public void setDebugEnabled(Boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    public Boolean getGzipEnabled() {
        return gzipEnabled;
    }

    /**
     * Use Facebook GZIP encoding
     */
    public void setGzipEnabled(Boolean gzipEnabled) {
        this.gzipEnabled = gzipEnabled;
    }

    public Integer getHttpConnectionTimeout() {
        return httpConnectionTimeout;
    }

    /**
     * Http connection timeout in milliseconds
     */
    public void setHttpConnectionTimeout(Integer httpConnectionTimeout) {
        this.httpConnectionTimeout = httpConnectionTimeout;
    }

    public Integer getHttpDefaultMaxPerRoute() {
        return httpDefaultMaxPerRoute;
    }

    /**
     * HTTP maximum connections per route
     */
    public void setHttpDefaultMaxPerRoute(Integer httpDefaultMaxPerRoute) {
        this.httpDefaultMaxPerRoute = httpDefaultMaxPerRoute;
    }

    public Integer getHttpMaxTotalConnections() {
        return httpMaxTotalConnections;
    }

    /**
     * HTTP maximum total connections
     */
    public void setHttpMaxTotalConnections(Integer httpMaxTotalConnections) {
        this.httpMaxTotalConnections = httpMaxTotalConnections;
    }

    public String getHttpProxyHost() {
        return httpProxyHost;
    }

    /**
     * HTTP proxy server host name
     */
    public void setHttpProxyHost(String httpProxyHost) {
        this.httpProxyHost = httpProxyHost;
    }

    public String getHttpProxyPassword() {
        return httpProxyPassword;
    }

    /**
     * HTTP proxy server password
     */
    public void setHttpProxyPassword(String httpProxyPassword) {
        this.httpProxyPassword = httpProxyPassword;
    }

    public Integer getHttpProxyPort() {
        return httpProxyPort;
    }

    /**
     * HTTP proxy server port
     */
    public void setHttpProxyPort(Integer httpProxyPort) {
        this.httpProxyPort = httpProxyPort;
    }

    public String getHttpProxyUser() {
        return httpProxyUser;
    }

    /**
     * HTTP proxy server user name
     */
    public void setHttpProxyUser(String httpProxyUser) {
        this.httpProxyUser = httpProxyUser;
    }

    public Integer getHttpReadTimeout() {
        return httpReadTimeout;
    }

    /**
     * Http read timeout in milliseconds
     */
    public void setHttpReadTimeout(Integer httpReadTimeout) {
        this.httpReadTimeout = httpReadTimeout;
    }

    public Integer getHttpRetryCount() {
        return httpRetryCount;
    }

    /**
     * Number of HTTP retries
     */
    public void setHttpRetryCount(Integer httpRetryCount) {
        this.httpRetryCount = httpRetryCount;
    }

    public Integer getHttpRetryIntervalSeconds() {
        return httpRetryIntervalSeconds;
    }

    /**
     * HTTP retry interval in seconds
     */
    public void setHttpRetryIntervalSeconds(Integer httpRetryIntervalSeconds) {
        this.httpRetryIntervalSeconds = httpRetryIntervalSeconds;
    }

    public Integer getHttpStreamingReadTimeout() {
        return httpStreamingReadTimeout;
    }

    /**
     * HTTP streaming read timeout in milliseconds
     */
    public void setHttpStreamingReadTimeout(Integer httpStreamingReadTimeout) {
        this.httpStreamingReadTimeout = httpStreamingReadTimeout;
    }

    public Boolean getJsonStoreEnabled() {
        return jsonStoreEnabled;
    }

    /**
     * If set to true, raw JSON forms will be stored in DataObjectFactory
     */
    public void setJsonStoreEnabled(Boolean jsonStoreEnabled) {
        this.jsonStoreEnabled = jsonStoreEnabled;
    }

    public Boolean getMbeanEnabled() {
        return mbeanEnabled;
    }

    /**
     * If set to true, Facebook4J mbean will be registerd
     */
    public void setMbeanEnabled(Boolean mbeanEnabled) {
        this.mbeanEnabled = mbeanEnabled;
    }

    public String getOAuthPermissions() {
        return oAuthPermissions;
    }

    /**
     * Default OAuth permissions. Comma separated permission names.
     * See https://developers.facebook.com/docs/reference/login/#permissions for the detail
     */
    public void setOAuthPermissions(String oAuthPermissions) {
        this.oAuthPermissions = oAuthPermissions;
    }

    public Boolean getPrettyDebugEnabled() {
        return prettyDebugEnabled;
    }

    /**
     * Prettify JSON debug output if set to true
     */
    public void setPrettyDebugEnabled(Boolean prettyDebugEnabled) {
        this.prettyDebugEnabled = prettyDebugEnabled;
    }

    public String getRestBaseURL() {
        return restBaseURL;
    }

    /**
     * API base URL
     */
    public void setRestBaseURL(String restBaseURL) {
        this.restBaseURL = restBaseURL;
    }

    public Boolean getUseSSL() {
        return useSSL;
    }

    /**
     * Use SSL
     */
    public void setUseSSL(Boolean useSSL) {
        this.useSSL = useSSL;
    }

    public String getVideoBaseURL() {
        return videoBaseURL;
    }

    /**
     * Video API base URL
     */
    public void setVideoBaseURL(String videoBaseURL) {
        this.videoBaseURL = videoBaseURL;
    }

    public void validate() {
        if ((oAuthAppId == null || oAuthAppId.isEmpty())
            || (oAuthAppSecret == null || oAuthAppSecret.isEmpty())) {
            throw new IllegalArgumentException("Missing required properties oAuthAppId, oAuthAppSecret");
        }
    }

}
