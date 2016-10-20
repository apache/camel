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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.IntrospectionSupport;

/**
 * Represents the component that manages {@link ServiceNowEndpoint}.
 */
public class ServiceNowComponent extends UriEndpointComponent {

    private String userName;
    private String password;
    private String oauthClientId;
    private String oauthClientSecret;
    private String oauthTokenUrl;
    private String apiUrl;

    public ServiceNowComponent() {
        super(ServiceNowEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final CamelContext context = getCamelContext();
        final ServiceNowConfiguration configuration = new ServiceNowConfiguration();

        Map<String, Object> models = IntrospectionSupport.extractProperties(parameters, "model.");
        for (Map.Entry<String, Object> entry : models.entrySet()) {
            configuration.addModel(
                entry.getKey(),
                EndpointHelper.resolveParameter(context, (String)entry.getValue(), Class.class));
        }

        setProperties(configuration, parameters);

        if (configuration.getUserName() == null) {
            configuration.setUserName(userName);
        }
        if (configuration.getPassword() == null) {
            configuration.setPassword(password);
        }
        if (configuration.getOauthClientId() == null) {
            configuration.setOauthClientId(oauthClientId);
        }
        if (configuration.getOauthClientSecret() == null) {
            configuration.setOauthClientSecret(oauthClientSecret);
        }

        String instanceName = getCamelContext().resolvePropertyPlaceholders(remaining);
        if (!configuration.hasApiUrl()) {
            configuration.setApiUrl(apiUrl != null
                ? apiUrl
                : String.format("https://%s.service-now.com/api", instanceName)
            );
        }
        if (!configuration.hasOautTokenUrl()) {
            configuration.setOauthTokenUrl(oauthTokenUrl != null
                ? oauthTokenUrl
                : String.format("https://%s.service-now.com/oauth_token.do", instanceName)
            );
        }

        return new ServiceNowEndpoint(uri, this, configuration, instanceName);
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

    public String getUserName() {
        return userName;
    }

    /**
     * ServiceNow user account name
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    /**
     * ServiceNow account password
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

    /**
     * OAuth2 ClientSecret
     */
    public void setOauthClientSecret(String oauthClientSecret) {
        this.oauthClientSecret = oauthClientSecret;
    }

    public String getOauthTokenUrl() {
        return oauthTokenUrl;
    }

    /**
     * OAuth token Url
     */
    public void setOauthTokenUrl(String oauthTokenUrl) {
        this.oauthTokenUrl = oauthTokenUrl;
    }
}
