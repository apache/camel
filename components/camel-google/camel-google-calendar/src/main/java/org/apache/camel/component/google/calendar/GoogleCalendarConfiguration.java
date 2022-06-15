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
package org.apache.camel.component.google.calendar;

import java.util.List;

import com.google.api.services.calendar.CalendarScopes;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarApiName;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

/**
 * Component configuration for GoogleCalendar component.
 */
@UriParams
@Configurer(extended = true)
public class GoogleCalendarConfiguration {

    @UriPath
    @Metadata(required = true)
    private GoogleCalendarApiName apiName;
    @UriPath(enums = "calendarImport,clear,delete,get,insert,instances,list,move,patch,query,quickAdd,stop,update,watch")
    @Metadata(required = true)
    private String methodName;
    @UriParam(defaultValue = CalendarScopes.CALENDAR)
    private List<String> scopes;
    @UriParam
    private String clientId;
    @UriParam
    private String applicationName;
    @UriParam(label = "security", secret = true)
    private String emailAddress;
    @UriParam(label = "security", secret = true)
    private String clientSecret;
    @UriParam(label = "security", secret = true)
    private String accessToken;
    @UriParam(label = "security", secret = true)
    private String refreshToken;
    @UriParam(label = "security", secret = true)
    private String p12FileName;
    @UriParam(label = "security", secret = true)
    private String user;
    /* Service account */
    @UriParam(label = "security")
    private String serviceAccountKey;
    @UriParam
    private String delegate;

    public GoogleCalendarApiName getApiName() {
        return apiName;
    }

    /**
     * What kind of operation to perform
     */
    public void setApiName(GoogleCalendarApiName apiName) {
        this.apiName = apiName;
    }

    public String getMethodName() {
        return methodName;
    }

    /**
     * What sub operation to use for the selected operation
     */
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

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
     * Google calendar application name. Example would be "camel-google-calendar/1.0"
     */
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public List<String> getScopes() {
        return scopes;
    }

    /**
     * Specifies the level of permissions you want a calendar application to have to a user account. You can separate
     * multiple scopes by comma. See https://developers.google.com/google-apps/calendar/auth for more info.
     */
    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
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
     * The email address of the user the application is trying to impersonate in the service account flow
     */
    public void setUser(String user) {
        this.user = user;
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
}
