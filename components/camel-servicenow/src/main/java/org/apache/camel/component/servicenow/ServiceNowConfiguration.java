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
package org.apache.camel.component.servicenow;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.cxf.configuration.security.ProxyAuthorizationPolicy;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;

@UriParams
public class ServiceNowConfiguration implements Cloneable {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false)
        .setSerializationInclusion(
            JsonInclude.Include.NON_NULL
        );

    @UriParam(label = "security", secret = true)
    @Metadata(required = "true")
    private String userName;
    @UriParam(label = "security", secret = true)
    @Metadata(required = "true")
    private String password;
    @UriParam(label = "security", secret = true)
    private String oauthClientId;
    @UriParam(label = "security", secret = true)
    private String oauthClientSecret;
    @UriParam(label = "security", secret = true)
    private String oauthTokenUrl;
    @UriParam(label = "security")
    private String apiUrl;
    @UriParam(label = "advanced")
    private String apiVersion;
    @UriParam
    private String resource;
    @UriParam
    private String table;
    @UriParam
    private Boolean excludeReferenceLink = false;
    @UriParam
    private Boolean suppressAutoSysField = false;
    @UriParam
    private Boolean includeScores = false;
    @UriParam
    private Boolean includeAggregates = false;
    @UriParam
    private Boolean includeAvailableBreakdowns = false;
    @UriParam
    private Boolean includeAvailableAggregates = false;
    @UriParam
    private Boolean includeScoreNotes = false;
    @UriParam
    private Boolean topLevelOnly;
    @UriParam
    private Boolean favorites;
    @UriParam
    private Boolean key;
    @UriParam
    private Boolean target;
    @UriParam(defaultValue = "true", enums = "false,true,all")
    private String display = "true";
    @UriParam(defaultValue = "10")
    private Integer perPage = 10;
    @UriParam(enums = "value,change,changeperc,gap,gapperc,duedate,name,order,default,group,indicator_group,frequency,target,date,trend,bullet,direction")
    private String sortBy;
    @UriParam(enums = "asc,desc")
    private String sortDir;
    @UriParam
    private Boolean suppressPaginationHeader = false;
    @UriParam(defaultValue = "false", enums = "false,true,all")
    private String displayValue = "false";
    @UriParam
    private Boolean inputDisplayValue = false;
    @UriParam(prefix = "model.", multiValue = true, javaType = "java.lang.String", description = "Defines both request and response models")
    private transient Map<String, Class<?>> models; // field not in use as its a shortcut for both requestModels/responseModels
    @UriParam(prefix = "request-model.", multiValue = true, javaType = "java.lang.String")
    private Map<String, Class<?>> requestModels;
    @UriParam(prefix = "response-model.", multiValue = true, javaType = "java.lang.String")
    private Map<String, Class<?>> responseModels;
    @UriParam(label = "advanced")
    private ObjectMapper mapper = MAPPER;
    @UriParam(defaultValue = "HELSINKI", enums = "FUJI,GENEVA,HELSINKI")
    private ServiceNowRelease release = ServiceNowRelease.HELSINKI;
    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "advanced")
    private HTTPClientPolicy httpClientPolicy;
    @UriParam(label = "advanced")
    private ProxyAuthorizationPolicy proxyAuthorizationPolicy;
    @UriParam(label = "proxy")
    private String proxyHost;
    @UriParam(label = "proxy")
    private Integer proxyPort;
    @UriParam(label = "proxy,security")
    private String proxyUserName;
    @UriParam(label = "proxy,security")
    private String proxyPassword;

    public String getUserName() {
        return userName;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    /**
     * The ServiceNow REST API url
     */
    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public boolean hasApiUrl() {
        return apiUrl != null;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    /**
     * The ServiceNow REST API version, default latest
     */
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    /**
     * ServiceNow user account name, MUST be provided
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    /**
     * ServiceNow account password, MUST be provided
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getOauthClientId() {
        return oauthClientId;
    }

    /**
     * OAuth2 ClientID
     */
    public void setOauthClientId(String oauthClientId) {
        this.oauthClientId = oauthClientId;
    }

    public String getOauthClientSecret() {
        return oauthClientSecret;
    }

    /**
     * OAuth2 ClientSecret
     */
    public void setOauthClientSecret(String oauthClientSecret) {
        this.oauthClientSecret = oauthClientSecret;
    }

    public String getOauthTokenUrl() {
        return oauthTokenUrl;
    }

    public boolean hasOauthTokenUrl() {
        return oauthTokenUrl != null;
    }

    /**
     * OAuth token Url
     */
    public void setOauthTokenUrl(String oauthTokenUrl) {
        this.oauthTokenUrl = oauthTokenUrl;
    }

    public boolean hasBasicAuthentication() {
        return ObjectHelper.isNotEmpty(userName)
            && ObjectHelper.isNotEmpty(password);
    }

    public boolean hasOAuthAuthentication() {
        return ObjectHelper.isNotEmpty(userName)
            && ObjectHelper.isNotEmpty(password)
            && ObjectHelper.isNotEmpty(oauthClientId)
            && ObjectHelper.isNotEmpty(oauthClientSecret);
    }

    public String getResource() {
        return resource;
    }

    /**
     * The default resource, can be overridden by header CamelServiceNowResource
     */
    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getTable() {
        return table;
    }

    /**
     * The default table, can be overridden by header CamelServiceNowTable
     */
    public void setTable(String table) {
        this.table = table;
    }

    public Boolean getExcludeReferenceLink() {
        return excludeReferenceLink;
    }

    /**
     * True to exclude Table API links for reference fields (default: false)
     */
    public void setExcludeReferenceLink(Boolean excludeReferenceLink) {
        this.excludeReferenceLink = excludeReferenceLink;
    }

    public Boolean getSuppressAutoSysField() {
        return suppressAutoSysField;
    }

    /**
     * True to suppress auto generation of system fields (default: false)
     */
    public void setSuppressAutoSysField(Boolean suppressAutoSysField) {
        this.suppressAutoSysField = suppressAutoSysField;
    }

    public Boolean getSuppressPaginationHeader() {
        return suppressPaginationHeader;
    }

    /**
     * Set this value to true to remove the Link header from the response. The
     * Link header allows you to request additional pages of data when the number
     * of records matching your query exceeds the query limit
     */
    public void setSuppressPaginationHeader(Boolean suppressPaginationHeader) {
        this.suppressPaginationHeader = suppressPaginationHeader;
    }

    public Boolean getIncludeScores() {
        return includeScores;
    }

    /**
     * Set this parameter to true to return all scores for a scorecard. If a value
     * is not specified, this parameter defaults to false and returns only the most
     * recent score value.
     */
    public void setIncludeScores(Boolean includeScores) {
        this.includeScores = includeScores;
    }

    public Boolean getIncludeAggregates() {
        return includeAggregates;
    }

    /**
     * Set this parameter to true to always return all available aggregates for
     * an indicator, including when an aggregate has already been applied. If a
     * value is not specified, this parameter defaults to false and returns no
     * aggregates.
     */
    public void setIncludeAggregates(Boolean includeAggregates) {
        this.includeAggregates = includeAggregates;
    }

    public Boolean getIncludeAvailableBreakdowns() {
        return includeAvailableBreakdowns;
    }

    /**
     * Set this parameter to true to return all available breakdowns for an indicator.
     * If a value is not specified, this parameter defaults to false and returns
     * no breakdowns.
     */
    public void setIncludeAvailableBreakdowns(Boolean includeAvailableBreakdowns) {
        this.includeAvailableBreakdowns = includeAvailableBreakdowns;
    }

    public Boolean getIncludeAvailableAggregates() {
        return includeAvailableAggregates;
    }

    /**
     * Set this parameter to true to return all available aggregates for an indicator
     * when no aggregate has been applied. If a value is not specified, this parameter
     * defaults to false and returns no aggregates.
     */
    public void setIncludeAvailableAggregates(Boolean includeAvailableAggregates) {
        this.includeAvailableAggregates = includeAvailableAggregates;
    }

    public Boolean getIncludeScoreNotes() {
        return includeScoreNotes;
    }

    /**
     * Set this parameter to true to return all notes associated with the score.
     * The note element contains the note text as well as the author and timestamp
     * when the note was added.
     */
    public void setIncludeScoreNotes(Boolean includeScoreNotes) {
        this.includeScoreNotes = includeScoreNotes;
    }

    public Boolean getFavorites() {
        return favorites;
    }

    /**
     * Set this parameter to true to return only scorecards that are favorites of
     * the querying user.
     */
    public void setFavorites(Boolean favorites) {
        this.favorites = favorites;
    }

    public Boolean getKey() {
        return key;
    }

    /**
     * Set this parameter to true to return only scorecards for key indicators.
     */
    public void setKey(Boolean key) {
        this.key = key;
    }

    public Boolean getTarget() {
        return target;
    }

    /**
     * Set this parameter to true to return only scorecards that have a target.
     */
    public void setTarget(Boolean target) {
        this.target = target;
    }

    public String getDisplay() {
        return display;
    }

    /**
     * Set this parameter to true to return only scorecards where the indicator
     * Display field is selected. Set this parameter to all to return scorecards
     * with any Display field value. This parameter is true by default.
     */
    public void setDisplay(String display) {
        this.display = display;
    }

    public Integer getPerPage() {
        return perPage;
    }

    /**
     * Enter the maximum number of scorecards each query can return. By default
     * this value is 10, and the maximum is 100.
     */
    public void setPerPage(Integer perPage) {
        this.perPage = perPage;
    }

    public String getSortBy() {
        return sortBy;
    }

    /**
     * Specify the value to use when sorting results. By default, queries sort
     * records by value.
     */
    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getSortDir() {
        return sortDir;
    }

    /**
     * Specify the sort direction, ascending or descending. By default, queries
     * sort records in descending order. Use sysparm_sortdir=asc to sort in
     * ascending order.
     */
    public void setSortDir(String sortDir) {
        this.sortDir = sortDir;
    }

    public String getDisplayValue() {
        return displayValue;
    }

    /**
     * Return the display value (true), actual value (false), or both (all) for
     * reference fields (default: false)
     */
    public void setDisplayValue(String displayValue) {
        this.displayValue = displayValue;
    }

    public Boolean getInputDisplayValue() {
        return inputDisplayValue;
    }

    /**
     * True to set raw value of input fields (default: false)
     */
    public void setInputDisplayValue(Boolean inputDisplayValue) {
        this.inputDisplayValue = inputDisplayValue;
    }

    public Map<String, Class<?>> getRequestModels() {
        return requestModels;
    }

    /**
     * Sets Jackson's ObjectMapper to use for request/reply
     */
    public void setMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectMapper getMapper() {
        return mapper;
    }

    public boolean hasMapper() {
        return mapper != null;
    }

    /**
     * The ServiceNow release to target, default to Helsinki
     *
     * See https://docs.servicenow.com
     */
    public void setRelease(ServiceNowRelease release) {
        this.release = release;
    }

    public ServiceNowRelease getRelease() {
        return release;
    }

    public Boolean getTopLevelOnly() {
        return topLevelOnly;
    }

    /**
     * Gets only those categories whose parent is a catalog.
     */
    public void setTopLevelOnly(Boolean topLevelOnly) {
        this.topLevelOnly = topLevelOnly;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * To configure security using SSLContextParameters. See http://camel.apache.org/camel-configuration-utilities.html
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public HTTPClientPolicy getHttpClientPolicy() {
        return httpClientPolicy;
    }

    /**
     * To configure http-client
     */
    public void setHttpClientPolicy(HTTPClientPolicy httpClientPolicy) {
        this.httpClientPolicy = httpClientPolicy;
    }

    public ProxyAuthorizationPolicy getProxyAuthorizationPolicy() {
        return proxyAuthorizationPolicy;
    }

    /**
     * To configure proxy authentication
     */
    public void setProxyAuthorizationPolicy(ProxyAuthorizationPolicy proxyAuthorizationPolicy) {
        this.proxyAuthorizationPolicy = proxyAuthorizationPolicy;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * The proxy host name
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * The proxy port number
     */
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyUserName() {
        return proxyUserName;
    }

    /**
     * Username for proxy authentication
     */
    public void setProxyUserName(String proxyUserName) {
        this.proxyUserName = proxyUserName;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    /**
     * Password for proxy authentication
     */
    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    // *************************************************
    //
    // *************************************************

    public void setModels(Map<String, Class<?>> models) {
        setRequestModels(models);
        setResponseModels(models);
    }

    public void addModel(String name, Class<?> type) {
        addRequestModel(name, type);
        addResponseModel(name, type);
    }

    // *************************************************
    // Request model
    // *************************************************

    /**
     * Defines the request model
     */
    public void setRequestModels(Map<String, Class<?>> models) {
        if (this.requestModels == null) {
            this.requestModels = new HashMap<>();
        }

        this.requestModels.clear();
        this.requestModels.putAll(models);
    }

    public void addRequestModel(String name, Class<?> type) {
        if (this.requestModels == null) {
            this.requestModels = new HashMap<>();
        }

        this.requestModels.put(name, type);
    }

    public Class<?> getRequestModel(String name) {
        return getRequestModel(name, null);
    }

    public Class<?> getRequestModel(String name, Class<?> defaultType) {
        Class<?> model = defaultType;

        if (this.requestModels != null && this.requestModels.containsKey(name)) {
            model = this.requestModels.get(name);
        }

        return model;
    }

    // *************************************************
    // Response model
    // *************************************************

    /**
     * Defines the response model
     */
    public void setResponseModels(Map<String, Class<?>> models) {
        if (this.responseModels == null) {
            this.responseModels = new HashMap<>();
        }

        this.responseModels.putAll(models);
    }

    public void addResponseModel(String name, Class<?> type) {
        if (this.responseModels == null) {
            this.responseModels = new HashMap<>();
        }

        this.responseModels.clear();
        this.responseModels.put(name, type);
    }

    public Class<?> getResponseModel(String name) {
        return getResponseModel(name, null);
    }

    public Class<?> getResponseModel(String name, Class<?> defaultType) {
        Class<?> model = defaultType;

        if (this.responseModels != null && this.responseModels.containsKey(name)) {
            model = this.responseModels.get(name);
        }

        return model;
    }

    // *************************************************
    //
    // *************************************************

    public ServiceNowConfiguration copy() {
        try {
            return (ServiceNowConfiguration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
