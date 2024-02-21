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
package org.apache.camel.component.google.calendar.stream;

import java.util.Collections;
import java.util.List;

import com.google.api.services.calendar.CalendarScopes;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

/**
 * Component configuration for GoogleCalendar stream component.
 */
@UriParams
public class GoogleCalendarStreamConfiguration implements Cloneable {
    private static final List<String> DEFAULT_SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);

    @UriPath
    @Metadata(required = true)
    private String index;
    @UriParam
    private List<String> scopes = DEFAULT_SCOPES;
    @UriParam
    private String clientId;
    @UriParam
    private String applicationName;
    @UriParam(label = "security", secret = true)
    private String clientSecret;
    @UriParam(label = "security", secret = true)
    private String accessToken;
    @UriParam(label = "security", secret = true)
    private String refreshToken;
    @UriParam(label = "security", secret = true)
    private String p12FileName;
    @UriParam(label = "security", secret = true)
    private String emailAddress;
    @UriParam(label = "security", secret = true)
    private String user;
    @UriParam
    private String query;
    @UriParam(defaultValue = "10")
    private int maxResults = 10;
    @UriParam(defaultValue = "primary")
    private String calendarId = "primary";
    @UriParam(defaultValue = "true")
    private boolean consumeFromNow = true;
    @UriParam
    private boolean considerLastUpdate;
    @UriParam
    private boolean syncFlow;
    /* Service account */
    @UriParam(label = "security")
    private String serviceAccountKey;
    @UriParam
    private String delegate;

    public String getClientId() {
        return clientId;
    }

    /**
     * Client ID of the calendar application
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    /**
     * The emailAddress of the Google Service Account.
     */
    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * Client secret of the calendar application
     */
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getAccessToken() {
        return accessToken;
    }

    /**
     * OAuth 2 access token. This typically expires after an hour so refreshToken is recommended for long term usage.
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * OAuth 2 refresh token. Using this, the Google Calendar component can obtain a new accessToken whenever the
     * current one expires - a necessity if the application is long-lived.
     */
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getApplicationName() {
        return applicationName;
    }

    /**
     * Google Calendar application name. Example would be "camel-google-calendar/1.0"
     */
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public List<String> getScopes() {
        return scopes;
    }

    /**
     * Specifies the level of permissions you want a calendar application to have to a user account. See
     * https://developers.google.com/calendar/auth for more info.
     */
    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
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

    public String getP12FileName() {
        return p12FileName;
    }

    /**
     * The name of the p12 file which has the private key to use with the Google Service Account.
     */
    public void setP12FileName(String p12FileName) {
        this.p12FileName = p12FileName;
    }

    public String getUser() {
        return user;
    }

    /**
     * The email address of the user the application is trying to impersonate in the service account flow.
     */
    public void setUser(String user) {
        this.user = user;
    }

    public String getQuery() {
        return query;
    }

    /**
     * The query to execute on calendar
     */
    public void setQuery(String query) {
        this.query = query;
    }

    public int getMaxResults() {
        return maxResults;
    }

    /**
     * Max results to be returned
     */
    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public String getCalendarId() {
        return calendarId;
    }

    /**
     * The calendarId to be used
     */
    public void setCalendarId(String calendarId) {
        this.calendarId = calendarId;
    }

    public boolean isConsumeFromNow() {
        return consumeFromNow;
    }

    /**
     * Consume events in the selected calendar from now on
     */
    public void setConsumeFromNow(boolean consumeFromNow) {
        this.consumeFromNow = consumeFromNow;
    }

    public boolean isConsiderLastUpdate() {
        return considerLastUpdate;
    }

    /**
     * Sync events, see https://developers.google.com/calendar/v3/sync
     *
     * Note: not compatible with: 'query' and 'considerLastUpdate' parameters
     */
    public void setSyncFlow(boolean syncFlow) {
        this.syncFlow = syncFlow;
    }

    public boolean isSyncFlow() {
        return syncFlow;
    }

    /**
     * Take into account the lastUpdate of the last event polled as start date for the next poll
     */
    public void setConsiderLastUpdate(boolean considerLastUpdate) {
        this.considerLastUpdate = considerLastUpdate;
    }

    public String getServiceAccountKey() {
        return serviceAccountKey;
    }

    /**
     * Service account key in json format to authenticate an application as a service account. Accept base64 adding the
     * prefix "base64:"
     *
     * @param serviceAccountKey String file, classpath, base64, or http url
     */
    public void setServiceAccountKey(String serviceAccountKey) {
        this.serviceAccountKey = serviceAccountKey;
    }

    public String getDelegate() {
        return delegate;
    }

    /**
     * Delegate for wide-domain service account
     */
    public void setDelegate(String delegate) {
        this.delegate = delegate;
    }

    // *************************************************
    //
    // *************************************************
    public GoogleCalendarStreamConfiguration copy() {
        try {
            return (GoogleCalendarStreamConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
