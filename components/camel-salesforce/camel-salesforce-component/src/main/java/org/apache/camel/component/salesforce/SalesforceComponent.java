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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.ComponentConfiguration;
import org.apache.camel.Endpoint;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.AbstractQueryRecordsBase;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;
import org.apache.camel.component.salesforce.internal.OperationName;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.component.salesforce.internal.streaming.SubscriptionHelper;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.EndpointCompleter;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReflectionHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.eclipse.jetty.client.Address;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.RedirectListener;
import org.eclipse.jetty.client.security.ProxyAuthorization;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages {@link SalesforceEndpoint}.
 */
public class SalesforceComponent extends UriEndpointComponent implements EndpointCompleter {

    private static final Logger LOG = LoggerFactory.getLogger(SalesforceComponent.class);

    private static final int CONNECTION_TIMEOUT = 60000;
    private static final long RESPONSE_TIMEOUT = 60000;
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

    // Proxy basic authentication
    private String httpProxyUsername;
    private String httpProxyPassword;

    // DTO packages to scan
    private String[] packages;

    // component state
    private HttpClient httpClient;
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
                httpClient = new HttpClient();
                // default settings
                httpClient.setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);
                httpClient.setConnectTimeout(CONNECTION_TIMEOUT);
                httpClient.setTimeout(RESPONSE_TIMEOUT);
            }
        }

        // set ssl context parameters
        final SSLContextParameters contextParameters = sslContextParameters != null
            ? sslContextParameters : new SSLContextParameters();
        final SslContextFactory sslContextFactory = httpClient.getSslContextFactory();
        sslContextFactory.setSslContext(contextParameters.createSSLContext());

        // set HTTP client parameters
        if (httpClientProperties != null && !httpClientProperties.isEmpty()) {
            IntrospectionSupport.setProperties(getCamelContext().getTypeConverter(),
                httpClient, new HashMap<String, Object>(httpClientProperties));
        }

        // set HTTP proxy settings
        if (this.httpProxyHost != null && httpProxyPort != null) {
            httpClient.setProxy(new Address(this.httpProxyHost, this.httpProxyPort));
        }
        if (this.httpProxyUsername != null && httpProxyPassword != null) {
            httpClient.setProxyAuthentication(new ProxyAuthorization(this.httpProxyUsername, this.httpProxyPassword));
        }

        // add redirect listener to handle Salesforce redirects
        // this is ok to do since the RedirectListener is in the same classloader as Jetty client
        String listenerClass = RedirectListener.class.getName();
        if (httpClient.getRegisteredListeners() == null
                || !httpClient.getRegisteredListeners().contains(listenerClass)) {
            httpClient.registerListener(listenerClass);
        }
        // SalesforceSecurityListener can't be registered the same way
        // since Jetty HttpClient's Class.forName() can't see it

        // start the Jetty client to initialize thread pool, etc.
        httpClient.start();

        // support restarts
        if (null == this.session) {
            this.session = new SalesforceSession(httpClient, loginConfig);
        }

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

    public SubscriptionHelper getSubscriptionHelper() throws Exception {
        if (subscriptionHelper == null) {
            // lazily create subscription helper
            subscriptionHelper = new SubscriptionHelper(this);

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
     * To use the shared SalesforceLoginConfig as login configuration
     */
    public void setLoginConfig(SalesforceLoginConfig loginConfig) {
        this.loginConfig = loginConfig;
    }

    public SalesforceEndpointConfig getConfig() {
        return config;
    }

    /**
     * To use the shared SalesforceLoginConfig as configuration
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

}
