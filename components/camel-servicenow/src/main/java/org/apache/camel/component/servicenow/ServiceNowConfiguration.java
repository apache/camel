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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.ObjectHelper;

@UriParams
public class ServiceNowConfiguration {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false)
        .setSerializationInclusion(
            JsonInclude.Include.NON_NULL
        );

    @UriParam @Metadata(required = "true")
    private String userName;
    @UriParam @Metadata(required = "true")
    private String password;
    @UriParam(label = "security")
    private String oauthClientId;
    @UriParam(label = "security")
    private String oauthClientSecret;
    @UriParam(label = "security")
    private String oauthTokenUrl;
    @UriParam(label = "advanced")
    private String apiUrl;
    @UriParam
    private String table;
    @UriParam
    private Boolean excludeReferenceLink = false;
    @UriParam
    private Boolean suppressAutoSysField = false;
    @UriParam(defaultValue = "false", enums = "false,true,all")
    private String displayValue = "false";
    @UriParam
    private Boolean inputDisplayValue = false;
    @UriParam(prefix = "model.", multiValue = true, javaType = "java.lang.String")
    private Map<String, Class<?>> models;
    @UriParam(label = "advanced")
    private ObjectMapper mapper = MAPPER;


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

    public boolean hasOautTokenUrl() {
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

    public Map<String, Class<?>> getModels() {
        return models;
    }

    /**
     * Defines the default model to use for a table
     */
    public void setModels(Map<String, Class<?>> models) {
        this.models = models;
    }

    public void addModel(String name, Class<?> type) {
        if (this.models == null) {
            this.models = new HashMap<>();
        }

        this.models.put(name, type);
    }

    public Class<?> getModel(String name) {
        return getModel(name, null);
    }

    public Class<?> getModel(String name, Class<?> defaultType) {
        Class<?> model = defaultType;

        if (this.models != null && this.models.containsKey(name)) {
            model = this.models.get(name);
        }

        return model;
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
}
