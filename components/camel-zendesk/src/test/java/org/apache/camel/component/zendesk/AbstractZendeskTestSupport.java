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
package org.apache.camel.component.zendesk;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.test.junit4.CamelTestSupport;

/**
 * A base class for Zendesk integration tests.
 * <p>
 * The camel-api-component-maven-plugin automatically generates a skeleton code of
 * API integration tests in target/generated-test-sources/camel-component.
 * To cover more API methods in integration tests, you can copy the test methods and
 * routes from the skeleton code into one of the subclass of this class.
 * </p>
 */
public class AbstractZendeskTestSupport extends CamelTestSupport {
    public static final String TEST_OPTIONS_PROPERTIES = "/test-options.properties";
    public static final String SYSPROP_ZENDESK_SERVER_URL = "zendesk.serverUrl";
    public static final String SYSPROP_ZENDESK_USERNAME = "zendesk.username";
    public static final String SYSPROP_ZENDESK_PASSWORD = "zendesk.password";
    public static final String SYSPROP_ZENDESK_TOKEN = "zendesk.token";
    public static final String SYSPROP_ZENDESK_OAUTH_TOKEN = "zendesk.oauthToken";
    public static final String ENV_ZENDESK_SERVER_URL = "ZENDESK_SERVER_URL";
    public static final String ENV_ZENDESK_USERNAME = "ZENDESK_SERVER_URL";
    public static final String ENV_ZENDESK_PASSWORD = "zendesk.password";
    public static final String ENV_ZENDESK_TOKEN = "zendesk.token";
    public static final String ENV_ZENDESK_OAUTH_TOKEN = "zendesk.oauthToken";

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();
        final ZendeskConfiguration configuration = new ZendeskConfiguration();
        final Properties properties = new Properties();
        try {
            properties.load(getClass().getResourceAsStream(TEST_OPTIONS_PROPERTIES));
            Map<String, Object> options = new HashMap<>();
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                options.put(entry.getKey().toString(), entry.getValue());
            }

            PropertyBindingSupport.bindProperties(context, configuration, options);
        } catch (Exception e) {
            // ignore - system property or ENV may be supplied
        }
        configuration.setServerUrl(System.getenv(ENV_ZENDESK_SERVER_URL) != null ? System.getenv(ENV_ZENDESK_SERVER_URL) : configuration.getServerUrl());
        configuration.setUsername(System.getenv(ENV_ZENDESK_USERNAME) != null ? System.getenv(ENV_ZENDESK_USERNAME) : configuration.getUsername());
        configuration.setPassword(System.getenv(ENV_ZENDESK_PASSWORD) != null ? System.getenv(ENV_ZENDESK_PASSWORD) : configuration.getPassword());
        configuration.setToken(System.getenv(ENV_ZENDESK_TOKEN) != null ? System.getenv(ENV_ZENDESK_TOKEN) : configuration.getToken());
        configuration.setOauthToken(System.getenv(ENV_ZENDESK_OAUTH_TOKEN) != null ? System.getenv(ENV_ZENDESK_OAUTH_TOKEN) : configuration.getOauthToken());
        configuration.setServerUrl(System.getProperty(SYSPROP_ZENDESK_SERVER_URL, configuration.getServerUrl()));
        configuration.setUsername(System.getProperty(SYSPROP_ZENDESK_USERNAME, configuration.getUsername()));
        configuration.setPassword(System.getProperty(SYSPROP_ZENDESK_PASSWORD, configuration.getPassword()));
        configuration.setToken(System.getProperty(SYSPROP_ZENDESK_TOKEN, configuration.getToken()));
        configuration.setOauthToken(System.getProperty(SYSPROP_ZENDESK_OAUTH_TOKEN, configuration.getOauthToken()));
        if (configuration.getServerUrl() == null || configuration.getUsername() == null
            || (configuration.getPassword() == null && configuration.getToken() == null && configuration.getOauthToken() == null)) {
            throw new IllegalArgumentException("Zendesk configuration is missing");
        }

        // add ZendeskComponent to Camel context
        final ZendeskComponent component = new ZendeskComponent(context);
        component.setConfiguration(configuration);
        context.addComponent("zendesk", component);

        return context;
    }

    @Override
    public boolean isCreateCamelContextPerClass() {
        // only create the context once for this class
        return true;
    }

    @SuppressWarnings("unchecked")
    protected <T> T requestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers) {
        return (T) template.requestBodyAndHeaders(endpointUri, body, headers);
    }

    @SuppressWarnings("unchecked")
    protected <T> T requestBody(String endpointUri, Object body) {
        return (T) template.requestBody(endpointUri, body);
    }

}
