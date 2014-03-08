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

    @UriParam
    private String oAuthAppId;
    @UriParam
    private String oAuthAppSecret;
    @UriParam
    private String oAuthAccessToken;
    @UriParam
    private String oAuthAuthorizationURL;
    @UriParam
    private String oAuthPermissions;

    @UriParam
    private String oAuthAccessTokenURL;
    @UriParam
    private String clientURL;
    @UriParam
    private String clientVersion;
    @UriParam
    private Boolean debugEnabled;
    @UriParam
    private Boolean gzipEnabled;
    @UriParam
    private Integer httpConnectionTimeout;
    @UriParam
    private Integer httpDefaultMaxPerRoute;
    @UriParam
    private Integer httpMaxTotalConnections;
    @UriParam
    private String httpProxyHost;
    @UriParam
    private String httpProxyPassword;
    @UriParam
    private Integer httpProxyPort;
    @UriParam
    private String httpProxyUser;
    @UriParam
    private Integer httpReadTimeout;
    @UriParam
    private Integer httpRetryCount;
    @UriParam
    private Integer httpRetryIntervalSeconds;
    @UriParam
    private Integer httpStreamingReadTimeout;
    @UriParam
    private Boolean jsonStoreEnabled;
    @UriParam
    private Boolean mbeanEnabled;
    @UriParam
    private Boolean prettyDebugEnabled;
    @UriParam
    private String restBaseURL;
    @UriParam
    private Boolean useSSL;
    @UriParam
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

    public void setOAuthAccessToken(String oAuthAccessToken) {
        this.oAuthAccessToken = oAuthAccessToken;
    }

    public String getOAuthAccessTokenURL() {
        return oAuthAccessTokenURL;
    }

    public void setOAuthAccessTokenURL(String oAuthAccessTokenURL) {
        this.oAuthAccessTokenURL = oAuthAccessTokenURL;
    }

    public String getOAuthAppId() {
        return oAuthAppId;
    }

    public void setOAuthAppId(String oAuthAppId) {
        this.oAuthAppId = oAuthAppId;
    }

    public String getOAuthAppSecret() {
        return oAuthAppSecret;
    }

    public void setOAuthAppSecret(String oAuthAppSecret) {
        this.oAuthAppSecret = oAuthAppSecret;
    }

    public String getOAuthAuthorizationURL() {
        return oAuthAuthorizationURL;
    }

    public void setOAuthAuthorizationURL(String oAuthAuthorizationURL) {
        this.oAuthAuthorizationURL = oAuthAuthorizationURL;
    }

    public String getClientURL() {
        return clientURL;
    }

    public void setClientURL(String clientURL) {
        this.clientURL = clientURL;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    public Boolean getDebugEnabled() {
        return debugEnabled;
    }

    public void setDebugEnabled(Boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    public Boolean getGzipEnabled() {
        return gzipEnabled;
    }

    public void setGzipEnabled(Boolean gzipEnabled) {
        this.gzipEnabled = gzipEnabled;
    }

    public Integer getHttpConnectionTimeout() {
        return httpConnectionTimeout;
    }

    public void setHttpConnectionTimeout(Integer httpConnectionTimeout) {
        this.httpConnectionTimeout = httpConnectionTimeout;
    }

    public Integer getHttpDefaultMaxPerRoute() {
        return httpDefaultMaxPerRoute;
    }

    public void setHttpDefaultMaxPerRoute(Integer httpDefaultMaxPerRoute) {
        this.httpDefaultMaxPerRoute = httpDefaultMaxPerRoute;
    }

    public Integer getHttpMaxTotalConnections() {
        return httpMaxTotalConnections;
    }

    public void setHttpMaxTotalConnections(Integer httpMaxTotalConnections) {
        this.httpMaxTotalConnections = httpMaxTotalConnections;
    }

    public String getHttpProxyHost() {
        return httpProxyHost;
    }

    public void setHttpProxyHost(String httpProxyHost) {
        this.httpProxyHost = httpProxyHost;
    }

    public String getHttpProxyPassword() {
        return httpProxyPassword;
    }

    public void setHttpProxyPassword(String httpProxyPassword) {
        this.httpProxyPassword = httpProxyPassword;
    }

    public Integer getHttpProxyPort() {
        return httpProxyPort;
    }

    public void setHttpProxyPort(Integer httpProxyPort) {
        this.httpProxyPort = httpProxyPort;
    }

    public String getHttpProxyUser() {
        return httpProxyUser;
    }

    public void setHttpProxyUser(String httpProxyUser) {
        this.httpProxyUser = httpProxyUser;
    }

    public Integer getHttpReadTimeout() {
        return httpReadTimeout;
    }

    public void setHttpReadTimeout(Integer httpReadTimeout) {
        this.httpReadTimeout = httpReadTimeout;
    }

    public Integer getHttpRetryCount() {
        return httpRetryCount;
    }

    public void setHttpRetryCount(Integer httpRetryCount) {
        this.httpRetryCount = httpRetryCount;
    }

    public Integer getHttpRetryIntervalSeconds() {
        return httpRetryIntervalSeconds;
    }

    public void setHttpRetryIntervalSeconds(Integer httpRetryIntervalSeconds) {
        this.httpRetryIntervalSeconds = httpRetryIntervalSeconds;
    }

    public Integer getHttpStreamingReadTimeout() {
        return httpStreamingReadTimeout;
    }

    public void setHttpStreamingReadTimeout(Integer httpStreamingReadTimeout) {
        this.httpStreamingReadTimeout = httpStreamingReadTimeout;
    }

    public Boolean getJsonStoreEnabled() {
        return jsonStoreEnabled;
    }

    public void setJsonStoreEnabled(Boolean jsonStoreEnabled) {
        this.jsonStoreEnabled = jsonStoreEnabled;
    }

    public Boolean getMbeanEnabled() {
        return mbeanEnabled;
    }

    public void setMbeanEnabled(Boolean mbeanEnabled) {
        this.mbeanEnabled = mbeanEnabled;
    }

    public String getOAuthPermissions() {
        return oAuthPermissions;
    }

    public void setOAuthPermissions(String oAuthPermissions) {
        this.oAuthPermissions = oAuthPermissions;
    }

    public Boolean getPrettyDebugEnabled() {
        return prettyDebugEnabled;
    }

    public void setPrettyDebugEnabled(Boolean prettyDebugEnabled) {
        this.prettyDebugEnabled = prettyDebugEnabled;
    }

    public String getRestBaseURL() {
        return restBaseURL;
    }

    public void setRestBaseURL(String restBaseURL) {
        this.restBaseURL = restBaseURL;
    }

    public Boolean getUseSSL() {
        return useSSL;
    }

    public void setUseSSL(Boolean useSSL) {
        this.useSSL = useSSL;
    }

    public String getVideoBaseURL() {
        return videoBaseURL;
    }

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
