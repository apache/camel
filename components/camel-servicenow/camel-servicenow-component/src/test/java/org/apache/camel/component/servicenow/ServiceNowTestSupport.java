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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ServiceNowTestSupport extends CamelTestSupport {
    protected static final Logger LOGGER = LoggerFactory.getLogger(ServiceNowTestSupport.class);

    private final boolean setUpComponent;

    ServiceNowTestSupport() {
        this(true);
    }

    ServiceNowTestSupport(boolean setUpComponent) {
        this.setUpComponent = setUpComponent;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        if (setUpComponent) {
            configureServicenowComponent(context);
        }

        return context;
    }

    protected Map<String, Object> getParameters() {
        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("instanceName", getSystemPropertyOrEnvVar("servicenow.instance"));
        parameters.put("userName", getSystemPropertyOrEnvVar("servicenow.username"));
        parameters.put("password", getSystemPropertyOrEnvVar("servicenow.password"));
        parameters.put("oauthClientId", getSystemPropertyOrEnvVar("servicenow.oauth2.client.id"));
        parameters.put("oauthClientSecret", getSystemPropertyOrEnvVar("servicenow.oauth2.client.secret"));

        return parameters;
    }

    public void configureServicenowComponent(CamelContext camelContext) throws Exception {
        String userName = getSystemPropertyOrEnvVar("servicenow.username");
        String password = getSystemPropertyOrEnvVar("servicenow.password");
        String oauthClientId = getSystemPropertyOrEnvVar("servicenow.oauth2.client.id");
        String oauthClientSecret = getSystemPropertyOrEnvVar("servicenow.oauth2.client.secret");

        if (ObjectHelper.isNotEmpty(userName) && ObjectHelper.isNotEmpty(password)) {
            ServiceNowComponent component = new ServiceNowComponent();
            component.getConfiguration().setUserName(userName);
            component.getConfiguration().setPassword(password);

            if (ObjectHelper.isNotEmpty(oauthClientId) && ObjectHelper.isNotEmpty(oauthClientSecret)) {
                component.getConfiguration().setOauthClientId(oauthClientId);
                component.getConfiguration().setOauthClientSecret(oauthClientSecret);
            }

            camelContext.addComponent("servicenow", component);
        }
    }

    public static String getSystemPropertyOrEnvVar(String systemProperty) {
        String answer = System.getProperty(systemProperty);
        if (ObjectHelper.isEmpty(answer)) {
            String envProperty = systemProperty.toUpperCase().replaceAll("[.-]", "_");
            answer = System.getenv(envProperty);
        }

        return answer;
    }

    protected static KVBuilder kvBuilder() {
        return new KVBuilder(new HashMap<>());
    }

    protected static KVBuilder kvBuilder(Map<String, Object> headers) {
        return new KVBuilder(headers);
    }

    protected static final class KVBuilder {
        private final Map<String, Object> headers;

        private KVBuilder(Map<String, Object> headers) {
            this.headers = new HashMap<>(headers);
        }

        public KVBuilder put(String key, Object val) {
            headers.put(key, val);
            return this;
        }

        public KVBuilder put(ServiceNowParam key, Object val) {
            headers.put(key.getHeader(), val);
            return this;
        }

        public Map<String, Object> build() {
            return Collections.unmodifiableMap(this.headers);
        }
    }
}
