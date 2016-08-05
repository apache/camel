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
package org.apache.camel.component.salesforce;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.camel.CamelContext;
import org.apache.camel.ComponentConfiguration;
import org.apache.camel.Endpoint;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.AbstractQueryRecordsBase;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportMetadata;
import org.apache.camel.component.salesforce.api.dto.bulk.ContentType;
import org.apache.camel.component.salesforce.internal.OperationName;
import org.apache.camel.component.salesforce.internal.PayloadFormat;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.component.salesforce.internal.dto.NotifyForFieldsEnum;
import org.apache.camel.component.salesforce.internal.dto.NotifyForOperationsEnum;
import org.apache.camel.component.salesforce.internal.streaming.SubscriptionHelper;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.EndpointCompleter;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReflectionHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.Origin;
import org.eclipse.jetty.client.ProxyConfiguration;
import org.eclipse.jetty.client.Socks4Proxy;
import org.eclipse.jetty.client.api.Authentication;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.client.util.DigestAuthentication;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages {@link SalesforceEndpoint}.
 */
public class SalesforceComponent extends UriEndpointComponent implements EndpointCompleter {

    private static final Logger LOG = LoggerFactory.getLogger(SalesforceComponent.class);

    private static final int CONNECTION_TIMEOUT = 60000;
    private static final Pattern SOBJECT_NAME_PATTERN = Pattern.compile("^.*[\\?&]sObjectName=([^&,]+).*$");
    private static final String APEX_CALL_PREFIX = OperationName.APEX_CALL.value() + "/";

    private SalesforceLoginConfig loginConfig;
    private SalesforceEndpointConfig config;

    // HTTP client parameters, map of property-name to value
    private Map<String, Object> httpClientProperties;

    // SSL parameters
    private SSLContextParameters sslContextParameters;

    // Proxy host and port
    private String httpProxyHost;
    private Integer httpProxyPort;
    private boolean isHttpProxySocks4;
    private boolean isHttpProxySecure = true;
    private Set<String> httpProxyIncludedAddresses;
    private Set<String> httpProxyExcludedAddresses;

    // Proxy basic authentication
    private String httpProxyUsername;
    private String httpProxyPassword;
    private String httpProxyAuthUri;
    private String httpProxyRealm;
    private boolean httpProxyUseDigestAuth;

    // DTO packages to scan
    private String[] packages;

    // component state
    private SalesforceHttpClient httpClient;
    private SalesforceSession session;
    private Map<String, Class<?>> classMap;

    // Lazily created helper for consumer endpoints
    private SubscriptionHelper subscriptionHelper;

    public SalesforceComponent() {
        super(SalesforceEndpoint.class);
    }

    public SalesforceComponent(CamelContext context) {
        super(context, SalesforceEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // get Operation from remaining URI
        OperationName operationName = null;
        String topicName = null;
        String apexUrl = null;
        try {
            LOG.debug("Creating endpoint for: {}", remaining);
            if (remaining.startsWith(APEX_CALL_PREFIX)) {
                // extract APEX URL
                apexUrl = remaining.substring(APEX_CALL_PREFIX.length());
                remaining = OperationName.APEX_CALL.value();
            }
            operationName = OperationName.fromValue(remaining);
        } catch (IllegalArgumentException ex) {
            // if its not an operation name, treat is as topic name for consumer endpoints
            topicName = remaining;
        }

        // create endpoint config
        if (config == null) {
            config = new SalesforceEndpointConfig();
        }
        if (config.getHttpClient() == null) {
            // set the component's httpClient as default
            config.setHttpClient(httpClient);
        }

        // create a deep copy and map parameters
        final SalesforceEndpointConfig copy = config.copy();
        setProperties(copy, parameters);

        // set apexUrl in endpoint config
        if (apexUrl != null) {
            copy.setApexUrl(apexUrl);
        }

        final SalesforceEndpoint endpoint = new SalesforceEndpoint(uri, this, copy,
                operationName, topicName);

        // map remaining parameters to endpoint (specifically, synchronous)
        setProperties(endpoint, parameters);

        // if operation is APEX call, map remaining parameters to query params
        if (operationName == OperationName.APEX_CALL && !parameters.isEmpty()) {
            Map<String, Object> queryParams = new HashMap<String, Object>(copy.getApexQueryParams());

            // override component params with endpoint params
            queryParams.putAll(parameters);
            parameters.clear();

            copy.setApexQueryParams(queryParams);
        }

        return endpoint;
    }

    private Map<String, Class<?>> parsePackages() {
        Map<String, Class<?>> result = new HashMap<String, Class<?>>();
        Set<Class<?>> classes = getCamelContext().getPackageScanClassResolver().
                findImplementations(AbstractSObjectBase.class, packages);
        for (Class<?> aClass : classes) {
            // findImplementations also returns AbstractSObjectBase for some reason!!!
            if (AbstractSObjectBase.class != aClass) {
                result.put(aClass.getSimpleName(), aClass);
            }
        }

        return result;
    }

    @Override
    protected void doStart() throws Exception {
        // validate properties
        ObjectHelper.notNull(loginConfig, "loginConfig");

        // create a Jetty HttpClient if not already set
        if (null == httpClient) {
            if (config != null && config.getHttpClient() != null) {
                httpClient = config.getHttpClient();
            } else {
                // set ssl context parameters if set
                final SSLContextParameters contextParameters = sslContextParameters != null
                    ? sslContextParameters : new SSLContextParameters();
                final SslContextFactory sslContextFactory = new SslContextFactory();
                sslContextFactory.setSslContext(contextParameters.createSSLContext(getCamelContext()));

                httpClient = new SalesforceHttpClient(sslContextFactory);
                // default settings, use httpClientProperties to set other properties
                httpClient.setConnectTimeout(CONNECTION_TIMEOUT);
            }
        }

        // set HTTP client parameters
        if (httpClientProperties != null && !httpClientProperties.isEmpty()) {
            IntrospectionSupport.setProperties(getCamelContext().getTypeConverter(),
                httpClient, new HashMap<String, Object>(httpClientProperties));
        }

        // set HTTP proxy settings
        if (this.httpProxyHost != null && httpProxyPort != null) {
            Origin.Address proxyAddress = new Origin.Address(this.httpProxyHost, this.httpProxyPort);
            ProxyConfiguration.Proxy proxy; 
            if (isHttpProxySocks4) {
                proxy = new Socks4Proxy(proxyAddress, isHttpProxySecure);
            } else {
                proxy = new HttpProxy(proxyAddress, isHttpProxySecure);
            }
            if (httpProxyIncludedAddresses != null && !httpProxyIncludedAddresses.isEmpty()) {
                proxy.getIncludedAddresses().addAll(httpProxyIncludedAddresses);
            }
            if (httpProxyExcludedAddresses != null && !httpProxyExcludedAddresses.isEmpty()) {
                proxy.getExcludedAddresses().addAll(httpProxyExcludedAddresses);
            }
            httpClient.getProxyConfiguration().getProxies().add(proxy);
        }
        if (this.httpProxyUsername != null && httpProxyPassword != null) {

            ObjectHelper.notEmpty(httpProxyAuthUri, "httpProxyAuthUri");
            ObjectHelper.notEmpty(httpProxyRealm, "httpProxyRealm");

            final Authentication authentication;
            if (httpProxyUseDigestAuth) {
                authentication = new DigestAuthentication(new URI(httpProxyAuthUri),
                    httpProxyRealm, httpProxyUsername, httpProxyPassword);
            } else {
                authentication = new BasicAuthentication(new URI(httpProxyAuthUri),
                    httpProxyRealm, httpProxyUsername, httpProxyPassword);
            }
            httpClient.getAuthenticationStore().addAuthentication(authentication);
        }

        // support restarts
        if (null == this.session) {
            this.session = new SalesforceSession(httpClient, httpClient.getTimeout(), loginConfig);
        }
        // set session before calling start()
        httpClient.setSession(this.session);

        // start the Jetty client to initialize thread pool, etc.
        httpClient.start();

        // login at startup if lazyLogin is disabled
        if (!loginConfig.isLazyLogin()) {
            ServiceHelper.startService(session);
        }

        if (packages != null && packages.length > 0) {
            // parse the packages to create SObject name to class map
            classMap = parsePackages();
            LOG.info("Found {} generated classes in packages: {}", classMap.size(), Arrays.asList(packages));
        } else {
            // use an empty map to avoid NPEs later
            LOG.warn("Missing property packages, getSObject* operations will NOT work");
            classMap = new HashMap<String, Class<?>>(0);
        }

        if (subscriptionHelper != null) {
            ServiceHelper.startService(subscriptionHelper);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (classMap != null) {
            classMap.clear();
        }

        try {
            if (subscriptionHelper != null) {
                // shutdown all streaming connections
                // note that this is done in the component, and not in consumer
                ServiceHelper.stopService(subscriptionHelper);
                subscriptionHelper = null;
            }
            if (session != null && session.getAccessToken() != null) {
                try {
                    // logout of Salesforce
                    ServiceHelper.stopService(session);
                } catch (SalesforceException ignored) {
                }
            }
        } finally {
            if (httpClient != null) {
                // shutdown http client connections
                httpClient.stop();
                // destroy http client if it was created by the component
                if (config.getHttpClient() == null) {
                    httpClient.destroy();
                }
                httpClient = null;
            }
        }
    }

    public SubscriptionHelper getSubscriptionHelper(String topicName) throws Exception {
        if (subscriptionHelper == null) {
            // lazily create subscription helper
            subscriptionHelper = new SubscriptionHelper(this, topicName);

            // also start the helper to connect to Salesforce
            ServiceHelper.startService(subscriptionHelper);
        }
        return subscriptionHelper;
    }

    @Override
    public List<String> completeEndpointPath(ComponentConfiguration configuration, String completionText) {
        final List<String> result = new ArrayList<String>();
        // return operations names on empty completion text
        final boolean empty = ObjectHelper.isEmpty(completionText);
        if (empty || completionText.indexOf('?') == -1) {
            if (empty) {
                completionText = "";
            }
            final OperationName[] values = OperationName.values();
            for (OperationName val : values) {
                final String strValue = val.value();
                if (strValue.startsWith(completionText)) {
                    result.add(strValue);
                }
            }
            // also add place holder for user defined push topic name for empty completionText
            if (empty) {
                result.add("[PushTopicName]");
            }
        } else {
            // handle package parameters
            if (completionText.matches("^.*[\\?&]sObjectName=$")) {
                result.addAll(classMap.keySet());
            } else if (completionText.matches("^.*[\\?&]sObjectFields=$")) {
                // find sObjectName from configuration or completionText
                String sObjectName = (String) configuration.getParameter("sObjectName");
                if (sObjectName == null) {
                    final Matcher matcher = SOBJECT_NAME_PATTERN.matcher(completionText);
                    if (matcher.matches()) {
                        sObjectName = matcher.group(1);
                    }
                }
                // return all fields of sObject
                if (sObjectName != null) {
                    final Class<?> aClass = classMap.get(sObjectName);
                    ReflectionHelper.doWithFields(aClass, new ReflectionHelper.FieldCallback() {
                        @Override
                        public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
                            // get non-static fields
                            if ((field.getModifiers() & Modifier.STATIC) == 0) {
                                result.add(field.getName());
                            }
                        }
                    });
                }
            } else if (completionText.matches("^.*[\\?&]sObjectClass=$")) {
                for (Class c : classMap.values()) {
                    result.add(c.getName());
                }
                // also add Query records classes
                Set<Class<?>> classes = getCamelContext().getPackageScanClassResolver().
                    findImplementations(AbstractQueryRecordsBase.class, packages);
                for (Class<?> aClass : classes) {
                    // findImplementations also returns AbstractQueryRecordsBase for some reason!!!
                    if (AbstractQueryRecordsBase.class != aClass) {
                        result.add(aClass.getName());
                    }
                }
            }
        }
        return result;
    }

    public SalesforceLoginConfig getLoginConfig() {
        return loginConfig;
    }

    /**
     * To use the shared SalesforceLoginConfig as login configuration. Properties of the shared configuration can also be set individually.
     */
    public void setLoginConfig(SalesforceLoginConfig loginConfig) {
        this.loginConfig = loginConfig;
    }

    public SalesforceEndpointConfig getConfig() {
        return config;
    }

    /**
     * To use the shared SalesforceEndpointConfig as configuration. Properties of the shared configuration can also be set individually.
     */
    public void setConfig(SalesforceEndpointConfig config) {
        this.config = config;
    }

    public Map<String, Object> getHttpClientProperties() {
        return httpClientProperties;
    }

    /**
     * Used for configuring HTTP client properties as key/value pairs
     */
    public void setHttpClientProperties(Map<String, Object> httpClientProperties) {
        this.httpClientProperties = httpClientProperties;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * To configure security using SSLContextParameters
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public String getHttpProxyHost() {
        return httpProxyHost;
    }

    /**
     * To configure HTTP proxy host
     */
    public void setHttpProxyHost(String httpProxyHost) {
        this.httpProxyHost = httpProxyHost;
    }

    public Integer getHttpProxyPort() {
        return httpProxyPort;
    }

    /**
     * To configure HTTP proxy port
     */
    public void setHttpProxyPort(Integer httpProxyPort) {
        this.httpProxyPort = httpProxyPort;
    }

    public String getHttpProxyUsername() {
        return httpProxyUsername;
    }

    /**
     * To configure HTTP proxy username
     */
    public void setHttpProxyUsername(String httpProxyUsername) {
        this.httpProxyUsername = httpProxyUsername;
    }

    public String getHttpProxyPassword() {
        return httpProxyPassword;
    }

    /**
     * To configure HTTP proxy password
     */
    public void setHttpProxyPassword(String httpProxyPassword) {
        this.httpProxyPassword = httpProxyPassword;
    }

    public boolean isHttpProxySocks4() {
        return isHttpProxySocks4;
    }

    /**
     * Enable for Socks4 proxy, false by default
     */
    public void setIsHttpProxySocks4(boolean isHttpProxySocks4) {
        this.isHttpProxySocks4 = isHttpProxySocks4;
    }

    public boolean isHttpProxySecure() {
        return isHttpProxySecure;
    }

    /**
     * Enable for TLS connections, true by default
     */
    public void setIsHttpProxySecure(boolean isHttpProxySecure) {
        this.isHttpProxySecure = isHttpProxySecure;
    }

    public Set<String> getHttpProxyIncludedAddresses() {
        return httpProxyIncludedAddresses;
    }

    /**
     * HTTP proxy included addresses
     */
    public void setHttpProxyIncludedAddresses(Set<String> httpProxyIncludedAddresses) {
        this.httpProxyIncludedAddresses = httpProxyIncludedAddresses;
    }

    public Set<String> getHttpProxyExcludedAddresses() {
        return httpProxyExcludedAddresses;
    }

    /**
     * HTTP proxy excluded addresses
     */
    public void setHttpProxyExcludedAddresses(Set<String> httpProxyExcludedAddresses) {
        this.httpProxyExcludedAddresses = httpProxyExcludedAddresses;
    }

    public String getHttpProxyAuthUri() {
        return httpProxyAuthUri;
    }

    /**
     * HTTP proxy authentication URI
     */
    public void setHttpProxyAuthUri(String httpProxyAuthUri) {
        this.httpProxyAuthUri = httpProxyAuthUri;
    }

    public String getHttpProxyRealm() {
        return httpProxyRealm;
    }

    /**
     * HTTP proxy authentication realm
     */
    public void setHttpProxyRealm(String httpProxyRealm) {
        this.httpProxyRealm = httpProxyRealm;
    }

    public boolean isHttpProxyUseDigestAuth() {
        return httpProxyUseDigestAuth;
    }

    /**
     * Use HTTP proxy Digest authentication, false by default
     */
    public void setHttpProxyUseDigestAuth(boolean httpProxyUseDigestAuth) {
        this.httpProxyUseDigestAuth = httpProxyUseDigestAuth;
    }

    public String[] getPackages() {
        return packages;
    }

    /**
     * Package names to scan for DTO classes (multiple packages can be separated by comma).
     */
    public void setPackages(String[] packages) {
        this.packages = packages;
    }

    /**
     * Package names to scan for DTO classes (multiple packages can be separated by comma).
     */
    public void setPackages(String packages) {
        // split using comma
        if (packages != null) {
            setPackages(packages.split(","));
        }
    }

    public SalesforceSession getSession() {
        return session;
    }

    public Map<String, Class<?>> getClassMap() {
        return classMap;
    }

    private SalesforceLoginConfig getLoginConfigOrCreate() {
        if (this.getLoginConfig() == null) {
            this.setLoginConfig(new SalesforceLoginConfig());
        }
        return this.getLoginConfig();
    }

    private SalesforceEndpointConfig getConfigOrCreate() {
        if (this.getConfig() == null) {
            this.setConfig(new SalesforceEndpointConfig());
        }
        return this.getConfig();
    }

    public String getLoginUrl() {
        return getLoginConfigOrCreate().getLoginUrl();
    }

    /**
     * Salesforce login URL, defaults to https://login.salesforce.com
     * @param loginUrl
     */
    public void setLoginUrl(String loginUrl) {
        getLoginConfigOrCreate().setLoginUrl(loginUrl);
    }

    public String getClientId() {
        return getLoginConfigOrCreate().getClientId();
    }

    /**
     * Salesforce connected application Consumer Key
     * @param clientId
     */
    public void setClientId(String clientId) {
        getLoginConfigOrCreate().setClientId(clientId);
    }

    public String getClientSecret() {
        return getLoginConfigOrCreate().getClientSecret();
    }

    /**
     * Salesforce connected application Consumer Secret
     * @param clientSecret
     */
    public void setClientSecret(String clientSecret) {
        getLoginConfigOrCreate().setClientSecret(clientSecret);
    }

    public String getUserName() {
        return getLoginConfigOrCreate().getUserName();
    }

    /**
     * Salesforce account user name
     * @param userName
     */
    public void setUserName(String userName) {
        getLoginConfigOrCreate().setUserName(userName);
    }

    public String getPassword() {
        return getLoginConfigOrCreate().getPassword();
    }

    /**
     * Salesforce account password
     * @param password
     */
    public void setPassword(String password) {
        getLoginConfigOrCreate().setPassword(password);
    }

    public boolean isLazyLogin() {
        return getLoginConfigOrCreate().isLazyLogin();
    }

    /**
     * Flag to enable/disable lazy OAuth, default is false. When enabled, OAuth token retrieval or generation is not done until the first API call
     * @param lazyLogin
     */
    public void setLazyLogin(boolean lazyLogin) {
        getLoginConfigOrCreate().setLazyLogin(lazyLogin);
    }

    public PayloadFormat getFormat() {
        return getConfigOrCreate().getFormat();
    }

    /**
     * Payload format to use for Salesforce API calls, either JSON or XML, defaults to JSON
     * @param format
     */
    public void setFormat(PayloadFormat format) {
        getConfigOrCreate().setFormat(format);
    }

    public String getApiVersion() {
        return getConfigOrCreate().getApiVersion();
    }

    /**
     * Salesforce API version, defaults to SalesforceEndpointConfig.DEFAULT_VERSION
     * @param apiVersion
     */
    public void setApiVersion(String apiVersion) {
        getConfigOrCreate().setApiVersion(apiVersion);
    }

    public String getSObjectName() {
        return getConfigOrCreate().getSObjectName();
    }

    /**
     * SObject name if required or supported by API
     * @param sObjectName
     */
    public void setSObjectName(String sObjectName) {
        getConfigOrCreate().setSObjectName(sObjectName);
    }

    public String getSObjectId() {
        return getConfigOrCreate().getSObjectId();
    }

    /**
     * SObject ID if required by API
     * @param sObjectId
     */
    public void setSObjectId(String sObjectId) {
        getConfigOrCreate().setSObjectId(sObjectId);
    }

    public String getSObjectFields() {
        return getConfigOrCreate().getSObjectFields();
    }

    /**
     * SObject fields to retrieve
     * @param sObjectFields
     */
    public void setSObjectFields(String sObjectFields) {
        getConfigOrCreate().setSObjectFields(sObjectFields);
    }

    public String getSObjectIdName() {
        return getConfigOrCreate().getSObjectIdName();
    }

    /**
     * SObject external ID field name
     * @param sObjectIdName
     */
    public void setSObjectIdName(String sObjectIdName) {
        getConfigOrCreate().setSObjectIdName(sObjectIdName);
    }

    public String getSObjectIdValue() {
        return getConfigOrCreate().getSObjectIdValue();
    }

    /**
     * SObject external ID field value
     * @param sObjectIdValue
     */
    public void setSObjectIdValue(String sObjectIdValue) {
        getConfigOrCreate().setSObjectIdValue(sObjectIdValue);
    }

    public String getSObjectBlobFieldName() {
        return getConfigOrCreate().getSObjectBlobFieldName();
    }

    /**
     * SObject blob field name
     * @param sObjectBlobFieldName
     */
    public void setSObjectBlobFieldName(String sObjectBlobFieldName) {
        getConfigOrCreate().setSObjectBlobFieldName(sObjectBlobFieldName);
    }

    public String getSObjectClass() {
        return getConfigOrCreate().getSObjectClass();
    }

    /**
     * Fully qualified SObject class name, usually generated using camel-salesforce-maven-plugin
     * @param sObjectClass
     */
    public void setSObjectClass(String sObjectClass) {
        getConfigOrCreate().setSObjectClass(sObjectClass);
    }

    public String getSObjectQuery() {
        return getConfigOrCreate().getSObjectQuery();
    }

    /**
     * Salesforce SOQL query string
     * @param sObjectQuery
     */
    public void setSObjectQuery(String sObjectQuery) {
        getConfigOrCreate().setSObjectQuery(sObjectQuery);
    }

    public String getSObjectSearch() {
        return getConfigOrCreate().getSObjectSearch();
    }

    /**
     * Salesforce SOSL search string
     * @param sObjectSearch
     */
    public void setSObjectSearch(String sObjectSearch) {
        getConfigOrCreate().setSObjectSearch(sObjectSearch);
    }

    public String getApexMethod() {
        return getConfigOrCreate().getApexMethod();
    }

    /**
     * APEX method name
     * @param apexMethod
     */
    public void setApexMethod(String apexMethod) {
        getConfigOrCreate().setApexMethod(apexMethod);
    }

    public String getApexUrl() {
        return getConfigOrCreate().getApexUrl();
    }

    /**
     * APEX method URL
     * @param apexUrl
     */
    public void setApexUrl(String apexUrl) {
        getConfigOrCreate().setApexUrl(apexUrl);
    }

    public Map<String, Object> getApexQueryParams() {
        return getConfigOrCreate().getApexQueryParams();
    }

    /**
     * Query params for APEX method
     * @param apexQueryParams
     */
    public void setApexQueryParams(Map<String, Object> apexQueryParams) {
        getConfigOrCreate().setApexQueryParams(apexQueryParams);
    }

    public ContentType getContentType() {
        return getConfigOrCreate().getContentType();
    }

    /**
     * Bulk API content type, one of XML, CSV, ZIP_XML, ZIP_CSV
     * @param contentType
     */
    public void setContentType(ContentType contentType) {
        getConfigOrCreate().setContentType(contentType);
    }

    public String getJobId() {
        return getConfigOrCreate().getJobId();
    }

    /**
     * Bulk API Job ID
     * @param jobId
     */
    public void setJobId(String jobId) {
        getConfigOrCreate().setJobId(jobId);
    }

    public String getBatchId() {
        return getConfigOrCreate().getBatchId();
    }

    /**
     * Bulk API Batch ID
     * @param batchId
     */
    public void setBatchId(String batchId) {
        getConfigOrCreate().setBatchId(batchId);
    }

    public String getResultId() {
        return getConfigOrCreate().getResultId();
    }

    /**
     * Bulk API Result ID
     * @param resultId
     */
    public void setResultId(String resultId) {
        getConfigOrCreate().setResultId(resultId);
    }

    public boolean isUpdateTopic() {
        return getConfigOrCreate().isUpdateTopic();
    }

    /**
     * Whether to update an existing Push Topic when using the Streaming API, defaults to false
     * @param updateTopic
     */
    public void setUpdateTopic(boolean updateTopic) {
        getConfigOrCreate().setUpdateTopic(updateTopic);
    }

    public NotifyForFieldsEnum getNotifyForFields() {
        return getConfigOrCreate().getNotifyForFields();
    }

    /**
     * Notify for fields, options are ALL, REFERENCED, SELECT, WHERE
     * @param notifyForFields
     */
    public void setNotifyForFields(NotifyForFieldsEnum notifyForFields) {
        getConfigOrCreate().setNotifyForFields(notifyForFields);
    }

    public NotifyForOperationsEnum getNotifyForOperations() {
        return getConfigOrCreate().getNotifyForOperations();
    }

    /**
     * Notify for operations, options are ALL, CREATE, EXTENDED, UPDATE (API version < 29.0)
     * @param notifyForOperations
     */
    public void setNotifyForOperations(NotifyForOperationsEnum notifyForOperations) {
        getConfigOrCreate().setNotifyForOperations(notifyForOperations);
    }

    public Boolean getNotifyForOperationCreate() {
        return getConfigOrCreate().getNotifyForOperationCreate();
    }

    /**
     * Notify for create operation, defaults to false (API version >= 29.0)
     * @param notifyForOperationCreate
     */
    public void setNotifyForOperationCreate(Boolean notifyForOperationCreate) {
        getConfigOrCreate().setNotifyForOperationCreate(notifyForOperationCreate);
    }

    public Boolean getNotifyForOperationUpdate() {
        return getConfigOrCreate().getNotifyForOperationUpdate();
    }

    /**
     * Notify for update operation, defaults to false (API version >= 29.0)
     * @param notifyForOperationUpdate
     */
    public void setNotifyForOperationUpdate(Boolean notifyForOperationUpdate) {
        getConfigOrCreate().setNotifyForOperationUpdate(notifyForOperationUpdate);
    }

    public Boolean getNotifyForOperationDelete() {
        return getConfigOrCreate().getNotifyForOperationDelete();
    }

    /**
     * Notify for delete operation, defaults to false (API version >= 29.0)
     * @param notifyForOperationDelete
     */
    public void setNotifyForOperationDelete(Boolean notifyForOperationDelete) {
        getConfigOrCreate().setNotifyForOperationDelete(notifyForOperationDelete);
    }

    public Boolean getNotifyForOperationUndelete() {
        return getConfigOrCreate().getNotifyForOperationUndelete();
    }

    /**
     * Notify for un-delete operation, defaults to false (API version >= 29.0)
     * @param notifyForOperationUndelete
     */
    public void setNotifyForOperationUndelete(Boolean notifyForOperationUndelete) {
        getConfigOrCreate().setNotifyForOperationUndelete(notifyForOperationUndelete);
    }

    public String getReportId() {
        return getConfigOrCreate().getReportId();
    }

    /**
     * Salesforce1 Analytics report Id
     * @param reportId
     */
    public void setReportId(String reportId) {
        getConfigOrCreate().setReportId(reportId);
    }

    public Boolean getIncludeDetails() {
        return getConfigOrCreate().getIncludeDetails();
    }

    /**
     * Include details in Salesforce1 Analytics report, defaults to false.
     * @param includeDetails
     */
    public void setIncludeDetails(Boolean includeDetails) {
        getConfigOrCreate().setIncludeDetails(includeDetails);
    }

    public ReportMetadata getReportMetadata() {
        return getConfigOrCreate().getReportMetadata();
    }

    /**
     * Salesforce1 Analytics report metadata for filtering
     * @param reportMetadata
     */
    public void setReportMetadata(ReportMetadata reportMetadata) {
        getConfigOrCreate().setReportMetadata(reportMetadata);
    }

    public String getInstanceId() {
        return getConfigOrCreate().getInstanceId();
    }

    /**
     * Salesforce1 Analytics report execution instance ID
     * @param instanceId
     */
    public void setInstanceId(String instanceId) {
        getConfigOrCreate().setInstanceId(instanceId);
    }

    /**
     * Custom Jetty Http Client to use to connect to Salesforce.
     * @param httpClient
     */
    public void setHttpClient(SalesforceHttpClient httpClient) {
        getConfigOrCreate().setHttpClient(httpClient);
    }

    public SalesforceHttpClient getHttpClient() {
        return getConfigOrCreate().getHttpClient();
    }

    public ObjectMapper getObjectMapper() {
        return getConfigOrCreate().getObjectMapper();
    }

    /**
     * Custom Jackson ObjectMapper to use when serializing/deserializing Salesforce objects.
     * @param objectMapper
     */
    public void setObjectMapper(ObjectMapper objectMapper) {
        getConfigOrCreate().setObjectMapper(objectMapper);
    }

    public Integer getDefaultReplayId() {
        return getConfigOrCreate().getDefaultReplayId();
    }

    /**
     * Default replayId setting if no value is found in {@link #initialReplayIdMap}
     * @param defaultReplayId
     */
    public void setDefaultReplayId(Integer defaultReplayId) {
        getConfigOrCreate().setDefaultReplayId(defaultReplayId);
    }

    public Map<String, Integer> getInitialReplayIdMap() {
        return getConfigOrCreate().getInitialReplayIdMap();
    }

    /**
     * Replay IDs to start from per channel name.
     * @param initialReplayIdMap
     */
    public void setInitialReplayIdMap(Map<String, Integer> initialReplayIdMap) {
        getConfigOrCreate().setInitialReplayIdMap(initialReplayIdMap);
    }
}
