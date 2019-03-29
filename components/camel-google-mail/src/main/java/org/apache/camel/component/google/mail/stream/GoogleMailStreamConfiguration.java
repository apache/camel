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
package org.apache.camel.component.google.mail.stream;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

/**
 * Component configuration for GoogleMail stream component.
 */
@UriParams
public class GoogleMailStreamConfiguration implements Cloneable {
    @UriPath
    private String index;

    @UriParam
    private String clientId;

    @UriParam
    private String clientSecret;

    @UriParam
    private String accessToken;

    @UriParam
    private String refreshToken;

    @UriParam
    private String applicationName;

    @UriParam(defaultValue = "is:unread")
    private String query = "is:unread";

    @UriParam(defaultValue = "10")
    private long maxResults = 10L;

    @UriParam
    private String labels;

    @UriParam
    private boolean markAsRead;

    public String getClientId() {
        return clientId;
    }

    /**
     * Client ID of the mail application
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * Client secret of the mail application
     */
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getAccessToken() {
        return accessToken;
    }

    /**
     * OAuth 2 access token. This typically expires after an hour so
     * refreshToken is recommended for long term usage.
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * OAuth 2 refresh token. Using this, the Google Calendar component can
     * obtain a new accessToken whenever the current one expires - a necessity
     * if the application is long-lived.
     */
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getApplicationName() {
        return applicationName;
    }

    /**
     * Google mail application name. Example would be "camel-google-mail/1.0"
     */
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getIndex() {
        return index;
    }

    /**
     * Specifies an index for the endpoint
     */
    public void setIndex(String index) {
        this.index = index;
    }

    public String getQuery() {
        return query;
    }

    /**
     * The query to execute on gmail box
     */
    public void setQuery(String query) {
        this.query = query;
    }

    public long getMaxResults() {
        return maxResults;
    }

    /**
     * Max results to be returned
     */
    public void setMaxResults(long maxResults) {
        this.maxResults = maxResults;
    }

    public String getLabels() {
        return labels;
    }

    /**
     * Comma separated list of labels to take into account
     */
    public void setLabels(String labels) {
        this.labels = labels;
    }

    public boolean isMarkAsRead() {
        return markAsRead;
    }

    /**
     * Mark the message as read once it has been consumed
     */
    public void setMarkAsRead(boolean markAsRead) {
        this.markAsRead = markAsRead;
    }

    // *************************************************
    //
    // *************************************************

    public GoogleMailStreamConfiguration copy() {
        try {
            return (GoogleMailStreamConfiguration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

}
