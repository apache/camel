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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.zendesk.internal.ZendeskApiCollection;
import org.apache.camel.component.zendesk.internal.ZendeskApiMethod;
import org.apache.camel.component.zendesk.internal.ZendeskApiName;
import org.apache.camel.component.zendesk.internal.ZendeskHelper;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.component.AbstractApiComponent;
import org.apache.camel.util.IOHelper;
import org.zendesk.client.v2.Zendesk;

@Component("zendesk")
public class ZendeskComponent extends AbstractApiComponent<ZendeskApiName, ZendeskConfiguration, ZendeskApiCollection> {

    @Metadata
    private String serverUrl;
    @Metadata(label = "security", secret = true)
    private String username;
    @Metadata(label = "security", secret = true)
    private String oauthToken;
    @Metadata(label = "security", secret = true)
    private String token;
    @Metadata(label = "security", secret = true)
    private String password;
    @Metadata(label = "advanced")
    private Zendesk zendesk;

    public ZendeskComponent() {
        super(ZendeskApiName.class, ZendeskApiCollection.getCollection());
    }

    public ZendeskComponent(CamelContext context) {
        super(context, ZendeskApiName.class, ZendeskApiCollection.getCollection());
    }

    @Override
    protected ZendeskApiName getApiName(String apiNameStr) {
        return getCamelContext().getTypeConverter().convertTo(ZendeskApiName.class, apiNameStr);
    }

    /**
     * To use the shared configuration
     */
    @Override
    public void setConfiguration(ZendeskConfiguration configuration) {
        super.setConfiguration(configuration);
    }

    /**
     * To use the shared configuration
     */
    @Override
    public ZendeskConfiguration getConfiguration() {
        return super.getConfiguration();
    }

    /**
     * To use a shared Zendesk instance.
     */
    public Zendesk getZendesk() {
        return zendesk;
    }

    /**
     * To use a shared Zendesk instance.
     */
    public void setZendesk(Zendesk zendesk) {
        this.zendesk = zendesk;
    }

    /**
     * The server URL to connect.
     */
    public String getServerUrl() {
        return serverUrl;
    }

    /**
     * The server URL to connect.
     */
    public void setServerUrl(String url) {
        this.serverUrl = url;
    }

    /**
     * The user name.
     */
    public String getUsername() {
        return username;
    }

    /**
     * The user name.
     */
    public void setUsername(String user) {
        this.username = user;
    }

    /**
     * The security token.
     */
    public String getToken() {
        return token;
    }

    /**
     * The security token.
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * The OAuth token.
     */
    public String getOauthToken() {
        return oauthToken;
    }

    /**
     * The OAuth token.
     */
    public void setOauthToken(String token) {
        this.oauthToken = token;
    }

    /**
     * The password.
     */
    public String getPassword() {
        return password;
    }

    /**
     * The password.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    protected Endpoint createEndpoint(
            String uri, String methodName, ZendeskApiName apiName,
            ZendeskConfiguration endpointConfiguration) {

        endpointConfiguration.setMethodName(getCamelContext().getTypeConverter().convertTo(ZendeskApiMethod.class, methodName));

        if (endpointConfiguration.getServerUrl() == null) {
            endpointConfiguration.setServerUrl(serverUrl);
        }
        if (endpointConfiguration.getUsername() == null) {
            endpointConfiguration.setUsername(username);
        }
        if (endpointConfiguration.getPassword() == null) {
            endpointConfiguration.setPassword(password);
        }
        if (endpointConfiguration.getOauthToken() == null) {
            endpointConfiguration.setOauthToken(oauthToken);
        }

        return new ZendeskEndpoint(uri, this, apiName, methodName, endpointConfiguration);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (zendesk == null && configuration != null) {
            zendesk = ZendeskHelper.create(configuration);
        }
    }

    @Override
    protected void doStop() throws Exception {
        IOHelper.close(zendesk);
        super.doStop();
    }

}
