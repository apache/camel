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
package org.apache.camel.component.google.sheets.stream;

import java.util.Collection;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

/**
 * Component configuration for GoogleSheets stream component.
 */
@UriParams
public class GoogleSheetsStreamConfiguration implements Cloneable {

    @UriPath
    @Metadata(required = true)
    private String spreadsheetId;
    @UriParam
    private Collection<String> scopes;
    @UriParam
    private String clientId;
    @UriParam(label = "security", secret = true)
    private String clientSecret;
    @UriParam(label = "security", secret = true)
    private String accessToken;
    @UriParam(label = "security", secret = true)
    private String refreshToken;
    @UriParam
    private String applicationName;
    @UriParam
    private int maxResults;
    @UriParam
    private String range;
    @UriParam
    private boolean includeGridData;
    @UriParam
    private boolean splitResults;
    @UriParam(enums = "ROWS,COLUMNS,DIMENSION_UNSPECIFIED", defaultValue = "ROWS")
    private String majorDimension = "ROWS";
    @UriParam(enums = "FORMATTED_VALUE,UNFORMATTED_VALUE,FORMULA", defaultValue = "FORMATTED_VALUE")
    private String valueRenderOption = "FORMATTED_VALUE";
    /* Service account */
    @UriParam(label = "security")
    private String serviceAccountKey;
    @UriParam
    private String delegate;

    public String getClientId() {
        return clientId;
    }

    /**
     * Client ID of the sheets application
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * Client secret of the sheets application
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
     * OAuth 2 refresh token. Using this, the Google Sheets component can obtain a new accessToken whenever the current
     * one expires - a necessity if the application is long-lived.
     */
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getApplicationName() {
        return applicationName;
    }

    /**
     * Google Sheets application name. Example would be "camel-google-sheets/1.0"
     */
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public Collection<String> getScopes() {
        return scopes;
    }

    /**
     * Specifies the level of permissions you want a sheets application to have to a user account. See
     * https://developers.google.com/identity/protocols/googlescopes for more info.
     *
     * @see com.google.api.services.sheets.v4.SheetsScopes
     */
    public void setScopes(Collection<String> scopes) {
        this.scopes = scopes;
    }

    public String getSpreadsheetId() {
        return spreadsheetId;
    }

    /**
     * Specifies the spreadsheet identifier that is used to identify the target to obtain.
     */
    public void setSpreadsheetId(String spreadsheetId) {
        this.spreadsheetId = spreadsheetId;
    }

    public int getMaxResults() {
        return maxResults;
    }

    /**
     * Specify the maximum number of returned results. This will limit the number of rows in a returned value range data
     * set or the number of returned value ranges in a batch request.
     */
    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public String getRange() {
        return range;
    }

    /**
     * Specifies the range of rows and columns in a sheet to get data from.
     */
    public void setRange(String range) {
        this.range = range;
    }

    public String getMajorDimension() {
        return majorDimension;
    }

    /**
     * Specifies the major dimension that results should use..
     */
    public void setMajorDimension(String majorDimension) {
        this.majorDimension = majorDimension;
    }

    public String getValueRenderOption() {
        return valueRenderOption;
    }

    /**
     * Determines how values should be rendered in the output.
     */
    public void setValueRenderOption(String valueRenderOption) {
        this.valueRenderOption = valueRenderOption;
    }

    public boolean isIncludeGridData() {
        return includeGridData;
    }

    /**
     * True if grid data should be returned.
     */
    public void setIncludeGridData(boolean includeGridData) {
        this.includeGridData = includeGridData;
    }

    public boolean isSplitResults() {
        return splitResults;
    }

    /**
     * True if value range result should be split into rows or columns to process each of them individually. When true
     * each row or column is represented with a separate exchange in batch processing. Otherwise value range object is
     * used as exchange junk size.
     */
    public void setSplitResults(boolean splitResults) {
        this.splitResults = splitResults;
    }

    public String getServiceAccountKey() {
        return serviceAccountKey;
    }

    /**
     * Sets "*.json" file with credentials for Service account
     *
     * @param serviceAccountKey String file, classpath, or http url
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

    public GoogleSheetsStreamConfiguration copy() {
        try {
            return (GoogleSheetsStreamConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

}
