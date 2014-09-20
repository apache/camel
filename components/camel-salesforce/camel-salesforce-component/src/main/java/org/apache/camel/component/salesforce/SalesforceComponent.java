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
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReflectionHelper;
import org.apache.camel.util.ServiceHelper;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.RedirectListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages {@link SalesforceEndpoint}.
 */
public class SalesforceComponent extends UriEndpointComponent implements EndpointCompleter {

    private static final Logger LOG = LoggerFactory.getLogger(SalesforceComponent.class);

    private static final int MAX_CONNECTIONS_PER_ADDRESS = 20;
    private static final int CONNECTION_TIMEOUT = 60000;
    private static final int RESPONSE_TIMEOUT = 60000;
    private static final Pattern SOBJECT_NAME_PATTERN = Pattern.compile("^.*[\\?&]sObjectName=([^&,]+).*$");

    private SalesforceLoginConfig loginConfig;
    private SalesforceEndpointConfig config;
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
        try {
            LOG.debug("Creating endpoint for: {}", remaining);
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

        final SalesforceEndpoint endpoint = new SalesforceEndpoint(uri, this, copy,
                operationName, topicName);

        // map remaining parameters to endpoint (specifically, synchronous)
        setProperties(endpoint, parameters);

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
                httpClient.setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);
                httpClient.setMaxConnectionsPerAddress(MAX_CONNECTIONS_PER_ADDRESS);
                httpClient.setConnectTimeout(CONNECTION_TIMEOUT);
                httpClient.setTimeout(RESPONSE_TIMEOUT);
            }
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
                httpClient.destroy();
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

    public void setLoginConfig(SalesforceLoginConfig loginConfig) {
        this.loginConfig = loginConfig;
    }

    public SalesforceEndpointConfig getConfig() {
        return config;
    }

    public void setConfig(SalesforceEndpointConfig config) {
        this.config = config;
    }

    public String[] getPackages() {
        return packages;
    }

    public void setPackages(String[] packages) {
        this.packages = packages;
    }

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
