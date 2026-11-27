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
package org.apache.camel.component.servicenow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.configuration.security.ProxyAuthorizationPolicy;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

@UriParams
public class ServiceNowConfiguration implements Cloneable {
    @UriParam(description = "The user name of the ServiceNow account. The producer sends it with basic authentication, or in "
                            + "the OAuth password grant when oauthClientId and oauthClientSecret are set.",
              label = "security", security = "secret")
    @Metadata(required = true)
    private String userName;
    @UriParam(description = "The password of the ServiceNow account, used with userName.", label = "security",
              security = "secret")
    @Metadata(required = true)
    private String password;
    @UriParam(description = "The client ID of the OAuth application in ServiceNow. When it and oauthClientSecret are set, "
                            + "the producer gets an access token from oauthTokenUrl with the password grant of userName and "
                            + "password, and sends the token instead of basic authentication.",
              label = "security", security = "secret")
    private String oauthClientId;
    @UriParam(description = "The client secret of the OAuth application in ServiceNow, used with oauthClientId.",
              label = "security", security = "secret")
    private String oauthClientSecret;
    @UriParam(description = "The URL of the OAuth token endpoint of ServiceNow. If not set, "
                            + "https://{instanceName}.service-now.com/oauth_token.do is used.",
              label = "security", security = "secret")
    private String oauthTokenUrl;
    @UriParam(description = "The base URL of the ServiceNow REST API. If not set, https://{instanceName}.service-now.com/api "
                            + "is used.",
              label = "security")
    private String apiUrl;
    @UriParam(description = "The version of the REST API, for example v1, which the producer adds to the request path. If "
                            + "not set, the path has no version and ServiceNow uses the latest one. The "
                            + "CamelServiceNowApiVersion header replaces this option.",
              label = "advanced")
    private String apiVersion;
    @UriParam(description = "The REST API that the producer calls when the CamelServiceNowResource header is not set: table, "
                            + "aggregate, import, attachment, scorecards, misc, service_catalog, service_catalog_items, "
                            + "service_catalog_cart or service_catalog_categories. The FUJI release supports only table, "
                            + "aggregate and import.")
    private String resource;
    @UriParam(description = "The name of the table, for example incident, for the table, aggregate and import resources. The "
                            + "CamelServiceNowTable header replaces this option. The table name also selects the class of the "
                            + "models, requestModels and responseModels options.")
    private String table;
    @UriParam(description = "Whether ServiceNow leaves out the Table API links of reference fields in the response "
                            + "(sysparm_exclude_reference_link). The CamelServiceNowExcludeReferenceLink header replaces this "
                            + "option.",
              defaultValue = "false")
    private Boolean excludeReferenceLink = false;
    @UriParam(description = "Whether ServiceNow does not generate the system fields, such as sys_updated_on, when a record "
                            + "is created or changed (sysparm_suppress_auto_sys_field). The "
                            + "CamelServiceNowSuppressAutoSysField header replaces this option.",
              defaultValue = "false")
    private Boolean suppressAutoSysField = false;
    @UriParam(description = "Whether a scorecards request returns all scores of a scorecard instead of only the most recent "
                            + "one (sysparm_include_scores). The CamelServiceNowIncludeScores header replaces this option.",
              defaultValue = "false")
    private Boolean includeScores = false;
    @UriParam(description = "Whether a scorecards request returns all available aggregates of an indicator, also when an "
                            + "aggregate is already applied (sysparm_include_aggregates). The CamelServiceNowIncludeAggregates "
                            + "header replaces this option.",
              defaultValue = "false")
    private Boolean includeAggregates = false;
    @UriParam(description = "Whether a scorecards request returns all available breakdowns of an indicator "
                            + "(sysparm_include_available_breakdowns). The CamelServiceNowIncludeAvailableBreakdowns header "
                            + "replaces this option.",
              defaultValue = "false")
    private Boolean includeAvailableBreakdowns = false;
    @UriParam(description = "Whether a scorecards request returns all available aggregates of an indicator when no aggregate "
                            + "is applied (sysparm_include_available_aggregates). The "
                            + "CamelServiceNowIncludeAvailableAggregates header replaces this option.",
              defaultValue = "false")
    private Boolean includeAvailableAggregates = false;
    @UriParam(description = "Whether a scorecards request returns the notes of each score, with their text, author and time "
                            + "(sysparm_include_score_notes). The CamelServiceNowIncludeScoreNotes header replaces this "
                            + "option.",
              defaultValue = "false")
    private Boolean includeScoreNotes = false;
    @UriParam(description = "Whether the categories of a catalog (the categories subject of the service_catalog resource) "
                            + "are only those whose parent is the catalog (sysparm_top_level_only). The "
                            + "CamelServiceNowTopLevelOnly header replaces this option.")
    private Boolean topLevelOnly;
    @UriParam(description = "Whether a scorecards request returns only the scorecards that are favorites of the user "
                            + "(sysparm_favorites). The CamelServiceNowFavorites header replaces this option.")
    private Boolean favorites;
    @UriParam(description = "Whether the create action of the import resource returns the record that the import inserted "
                            + "into the target table, which the producer reads with a second request, instead of the import "
                            + "set result. The CamelServiceNowRetrieveTargetRecord header replaces this option.",
              label = "advanced", defaultValue = "false")
    private Boolean retrieveTargetRecordOnImport = false;
    @UriParam(description = "Whether a scorecards request returns only the scorecards of key indicators (sysparm_key). The "
                            + "CamelServiceNowKey header replaces this option.")
    private Boolean key;
    @UriParam(description = "Whether a scorecards request returns only the scorecards that have a target (sysparm_target). "
                            + "The CamelServiceNowTarget header replaces this option.")
    private Boolean target;
    @UriParam(description = "Which scorecards a scorecards request returns by the Display field of their indicator: true for "
                            + "only those with Display selected, all for any (sysparm_display). The CamelServiceNowDisplay "
                            + "header replaces this option.",
              defaultValue = "true", enums = "false,true,all")
    private String display = "true";
    @UriParam(description = "The maximum number of scorecards that a scorecards request returns per page, at most 100 "
                            + "(sysparm_per_page). The CamelServiceNowPerPage header replaces this option.",
              defaultValue = "10")
    private Integer perPage = 10;
    @UriParam(description = "The value that a scorecards request sorts the scorecards by (sysparm_sortby). If not set, "
                            + "ServiceNow sorts by value. The CamelServiceNowSortBy header replaces this option.",
              enums = "value,change,changeperc,gap,gapperc,duedate,name,order,default,group,indicator_group,frequency,target,date,trend,bullet,direction")
    private String sortBy;
    @UriParam(description = "The sort direction of a scorecards request, asc or desc (sysparm_sortdir). If not set, "
                            + "ServiceNow sorts in descending order. The CamelServiceNowSortDir header replaces this option.",
              enums = "asc,desc")
    private String sortDir;
    @UriParam(description = "Whether ServiceNow leaves out the Link header of the response "
                            + "(sysparm_suppress_pagination_header), from which the producer sets the "
                            + "CamelServiceNowOffsetNext, CamelServiceNowOffsetPrev, CamelServiceNowOffsetFirst and "
                            + "CamelServiceNowOffsetLast headers. The CamelServiceNowSuppressPaginationHeader header replaces "
                            + "this option.",
              defaultValue = "false")
    private Boolean suppressPaginationHeader = false;
    @UriParam(description = "Whether ServiceNow returns the display values (true), the actual values (false) or both (all) "
                            + "of the fields (sysparm_display_value). The CamelServiceNowDisplayValue header replaces this "
                            + "option.",
              defaultValue = "false", enums = "false,true,all")
    private String displayValue = "false";
    @UriParam(description = "Whether ServiceNow treats the field values of a create or change as display values instead of "
                            + "actual values (sysparm_input_display_value). The CamelServiceNowInputDisplayValue header "
                            + "replaces this option.",
              defaultValue = "false")
    private Boolean inputDisplayValue = false;
    @UriParam(description = "The model class per table name, used for both the request and the response. A table with no "
                            + "class uses a Map. The CamelServiceNowModel header replaces this option.",
              prefix = "model.", multiValue = true)
    private transient Map<String, Class<?>> models; // field not in use as its a shortcut for both requestModels/responseModels
    @UriParam(description = "The class of the request body per table name. The producer fails when the body is not an "
                            + "instance of it, and a table with no class uses a Map. The CamelServiceNowRequestModel and "
                            + "CamelServiceNowModel headers replace this option.",
              prefix = "request-model.", multiValue = true)
    private Map<String, Class<?>> requestModels;
    @UriParam(description = "The class per table name that the producer converts the result to. A table with no class uses a "
                            + "Map. The CamelServiceNowResponseModel and CamelServiceNowModel headers replace this option.",
              prefix = "response-model.", multiValue = true)
    private Map<String, Class<?>> responseModels;
    @UriParam(description = "To use a custom Jackson tools.jackson.databind.ObjectMapper, which must be a JsonMapper, to "
                            + "convert the request and the response. When it is set, the dateFormat, timeFormat and "
                            + "dateTimeFormat options are not used.",
              label = "advanced")
    private ObjectMapper mapper;
    @UriParam(description = "The ServiceNow release whose REST APIs the producer calls. FUJI supports the table, aggregate "
                            + "and import resources, and HELSINKI all resources. GENEVA is not supported.",
              defaultValue = "HELSINKI", enums = "FUJI,GENEVA,HELSINKI")
    private ServiceNowRelease release = ServiceNowRelease.HELSINKI;
    @UriParam(description = "The TLS configuration of the connection to the REST API. If not set and "
                            + "useGlobalSslContextParameters is enabled on the component, the global SSL context parameters "
                            + "are used.",
              label = "security")
    private SSLContextParameters sslContextParameters;
    @UriParam(description = "To use a custom Apache CXF org.apache.cxf.transports.http.configuration.HTTPClientPolicy for "
                            + "the HTTP connection, for example for timeouts. When it is set, the proxyHost and proxyPort "
                            + "options are not used.",
              label = "advanced")
    private HTTPClientPolicy httpClientPolicy;
    @UriParam(description = "To use a custom Apache CXF org.apache.cxf.configuration.security.ProxyAuthorizationPolicy for "
                            + "the authentication to the proxy. When it is set, the proxyUserName and proxyPassword options "
                            + "are not used.",
              label = "advanced")
    private ProxyAuthorizationPolicy proxyAuthorizationPolicy;
    @UriParam(description = "The host name of the HTTP proxy. The proxy is used only when proxyPort is also set and "
                            + "httpClientPolicy is not set.",
              label = "proxy")
    private String proxyHost;
    @UriParam(description = "The port of the HTTP proxy, used with proxyHost.", label = "proxy")
    private Integer proxyPort;
    @UriParam(description = "The user name for basic authentication to the proxy. It is used only when proxyPassword is also "
                            + "set and proxyAuthorizationPolicy is not set.",
              label = "proxy,security")
    private String proxyUserName;
    @UriParam(description = "The password for basic authentication to the proxy, used with proxyUserName.",
              label = "proxy,security", security = "secret")
    private String proxyPassword;
    @UriParam(description = "The java.time.format.DateTimeFormatter pattern of the LocalDate fields of the models in the "
                            + "JSON. It is not used when the mapper option is set.",
              label = "advanced", defaultValue = ServiceNowConstants.DEFAULT_DATE_FORMAT)
    private String dateFormat = ServiceNowConstants.DEFAULT_DATE_FORMAT;
    @UriParam(description = "The java.time.format.DateTimeFormatter pattern of the LocalTime fields of the models in the "
                            + "JSON. It is not used when the mapper option is set.",
              label = "advanced", defaultValue = ServiceNowConstants.DEFAULT_TIME_FORMAT)
    private String timeFormat = ServiceNowConstants.DEFAULT_TIME_FORMAT;
    @UriParam(description = "The java.time.format.DateTimeFormatter pattern of the LocalDateTime fields of the models in the "
                            + "JSON. It is not used when the mapper option is set.",
              label = "advanced", defaultValue = ServiceNowConstants.DEFAULT_DATE_TIME_FORMAT)
    private String dateTimeFormat = ServiceNowConstants.DEFAULT_DATE_TIME_FORMAT;

    public String getUserName() {
        return userName;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public boolean hasApiUrl() {
        return apiUrl != null;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getOauthClientId() {
        return oauthClientId;
    }

    public void setOauthClientId(String oauthClientId) {
        this.oauthClientId = oauthClientId;
    }

    public String getOauthClientSecret() {
        return oauthClientSecret;
    }

    public void setOauthClientSecret(String oauthClientSecret) {
        this.oauthClientSecret = oauthClientSecret;
    }

    public String getOauthTokenUrl() {
        return oauthTokenUrl;
    }

    public boolean hasOauthTokenUrl() {
        return oauthTokenUrl != null;
    }

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

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public Boolean getExcludeReferenceLink() {
        return excludeReferenceLink;
    }

    public void setExcludeReferenceLink(Boolean excludeReferenceLink) {
        this.excludeReferenceLink = excludeReferenceLink;
    }

    public Boolean getSuppressAutoSysField() {
        return suppressAutoSysField;
    }

    public void setSuppressAutoSysField(Boolean suppressAutoSysField) {
        this.suppressAutoSysField = suppressAutoSysField;
    }

    public Boolean getSuppressPaginationHeader() {
        return suppressPaginationHeader;
    }

    public void setSuppressPaginationHeader(Boolean suppressPaginationHeader) {
        this.suppressPaginationHeader = suppressPaginationHeader;
    }

    public Boolean getIncludeScores() {
        return includeScores;
    }

    public void setIncludeScores(Boolean includeScores) {
        this.includeScores = includeScores;
    }

    public Boolean getIncludeAggregates() {
        return includeAggregates;
    }

    public void setIncludeAggregates(Boolean includeAggregates) {
        this.includeAggregates = includeAggregates;
    }

    public Boolean getIncludeAvailableBreakdowns() {
        return includeAvailableBreakdowns;
    }

    public void setIncludeAvailableBreakdowns(Boolean includeAvailableBreakdowns) {
        this.includeAvailableBreakdowns = includeAvailableBreakdowns;
    }

    public Boolean getIncludeAvailableAggregates() {
        return includeAvailableAggregates;
    }

    public void setIncludeAvailableAggregates(Boolean includeAvailableAggregates) {
        this.includeAvailableAggregates = includeAvailableAggregates;
    }

    public Boolean getIncludeScoreNotes() {
        return includeScoreNotes;
    }

    public void setIncludeScoreNotes(Boolean includeScoreNotes) {
        this.includeScoreNotes = includeScoreNotes;
    }

    public Boolean getFavorites() {
        return favorites;
    }

    public void setFavorites(Boolean favorites) {
        this.favorites = favorites;
    }

    public Boolean getRetrieveTargetRecordOnImport() {
        return retrieveTargetRecordOnImport;
    }

    public void setRetrieveTargetRecordOnImport(Boolean retrieveTargetRecordOnImport) {
        this.retrieveTargetRecordOnImport = retrieveTargetRecordOnImport;
    }

    public Boolean getKey() {
        return key;
    }

    public void setKey(Boolean key) {
        this.key = key;
    }

    public Boolean getTarget() {
        return target;
    }

    public void setTarget(Boolean target) {
        this.target = target;
    }

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public Integer getPerPage() {
        return perPage;
    }

    public void setPerPage(Integer perPage) {
        this.perPage = perPage;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getSortDir() {
        return sortDir;
    }

    public void setSortDir(String sortDir) {
        this.sortDir = sortDir;
    }

    public String getDisplayValue() {
        return displayValue;
    }

    public void setDisplayValue(String displayValue) {
        this.displayValue = displayValue;
    }

    public Boolean getInputDisplayValue() {
        return inputDisplayValue;
    }

    public void setInputDisplayValue(Boolean inputDisplayValue) {
        this.inputDisplayValue = inputDisplayValue;
    }

    public Map<String, Class<?>> getRequestModels() {
        return requestModels;
    }

    public void setMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectMapper getMapper() {
        return mapper;
    }

    public ObjectMapper getOrCreateMapper() {
        if (mapper == null) {
            final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern(getDateFormat());
            final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern(getTimeFormat());
            final DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern(getDateTimeFormat());

            this.mapper = JsonMapper.builderWithJackson2Defaults()
                    .addModule(new SimpleModule("camel-servicenow")
                            .addSerializer(LocalDate.class, new LocalDateSerializer(dateFormat))
                            .addDeserializer(LocalDate.class, new LocalDateDeserializer(dateFormat))
                            .addSerializer(LocalTime.class, new LocalTimeSerializer(timeFormat))
                            .addDeserializer(LocalTime.class, new LocalTimeDeserializer(timeFormat))
                            .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormat))
                            .addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dateTimeFormat)))
                    .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .changeDefaultPropertyInclusion(incl -> incl
                            .withValueInclusion(JsonInclude.Include.NON_NULL)
                            .withContentInclusion(JsonInclude.Include.NON_NULL))
                    .build();
        }

        return mapper;
    }

    public boolean hasMapper() {
        return mapper != null;
    }

    public void setRelease(ServiceNowRelease release) {
        this.release = release;
    }

    public ServiceNowRelease getRelease() {
        return release;
    }

    public Boolean getTopLevelOnly() {
        return topLevelOnly;
    }

    public void setTopLevelOnly(Boolean topLevelOnly) {
        this.topLevelOnly = topLevelOnly;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public HTTPClientPolicy getHttpClientPolicy() {
        return httpClientPolicy;
    }

    public void setHttpClientPolicy(HTTPClientPolicy httpClientPolicy) {
        this.httpClientPolicy = httpClientPolicy;
    }

    public ProxyAuthorizationPolicy getProxyAuthorizationPolicy() {
        return proxyAuthorizationPolicy;
    }

    public void setProxyAuthorizationPolicy(ProxyAuthorizationPolicy proxyAuthorizationPolicy) {
        this.proxyAuthorizationPolicy = proxyAuthorizationPolicy;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyUserName() {
        return proxyUserName;
    }

    public void setProxyUserName(String proxyUserName) {
        this.proxyUserName = proxyUserName;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public String getTimeFormat() {
        return timeFormat;
    }

    public void setTimeFormat(String timeFormat) {
        this.timeFormat = timeFormat;
    }

    public String getDateTimeFormat() {
        return dateTimeFormat;
    }

    public void setDateTimeFormat(String dateTimeFormat) {
        this.dateTimeFormat = dateTimeFormat;
    }

    // *************************************************
    //
    // *************************************************

    public void setModels(Map<String, Class<?>> models) {
        setRequestModels(models);
        setResponseModels(models);
    }

    public Map<String, Class<?>> getModels() {
        return models;
    }

    public void addModel(String name, Class<?> type) {
        addRequestModel(name, type);
        addResponseModel(name, type);
    }

    // *************************************************
    // Request model
    // *************************************************

    public void setRequestModels(Map<String, Class<?>> models) {
        if (this.requestModels == null) {
            this.requestModels = new HashMap<>();
        }

        this.requestModels.clear();
        this.requestModels.putAll(models);
    }

    public Map<String, Class<?>> getResponseModels() {
        return responseModels;
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
            return (ServiceNowConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
