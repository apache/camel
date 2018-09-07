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
package org.apache.camel.component.google.calendar.stream;

import java.util.Arrays;
import java.util.List;

import com.google.api.services.calendar.CalendarScopes;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

/**
 * Component configuration for GoogleCalendar stream component.
 */
@UriParams
public class GoogleCalendarStreamConfiguration implements Cloneable {
    private static final List<String> DEFAULT_SCOPES = Arrays.asList(CalendarScopes.CALENDAR);

    @UriPath
    private String index;

    @UriParam
    private List<String> scopes = DEFAULT_SCOPES;

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

    @UriParam
    private String query;

    @UriParam(defaultValue = "10")
    private int maxResults = 10;
    
    @UriParam(defaultValue = "primary")
    private String calendarSummaryName = "primary";

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
     * Google Calendar application name. Example would be "camel-google-calendar/1.0"
     */
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public List<String> getScopes() {
        return scopes;
    }

    /**
     * Specifies the level of permissions you want a mail application to have to
     * a user account. See https://developers.google.com/calendar/api/auth/scopes
     * for more info.
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

    public String getCalendarSummaryName() {
        return calendarSummaryName;
    }

    /**
     * Calendar Summary name to use
     */
    public void setCalendarSummaryName(String calendarSummaryName) {
        this.calendarSummaryName = calendarSummaryName;
    } 

    // *************************************************
    //
    // *************************************************
    public GoogleCalendarStreamConfiguration copy() {
        try {
            return (GoogleCalendarStreamConfiguration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

}
