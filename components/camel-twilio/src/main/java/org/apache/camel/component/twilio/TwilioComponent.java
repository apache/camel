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
package org.apache.camel.component.twilio;

import com.twilio.http.TwilioRestClient;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.twilio.internal.TwilioApiCollection;
import org.apache.camel.component.twilio.internal.TwilioApiName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.component.AbstractApiComponent;
import org.apache.camel.util.ObjectHelper;

@Component("twilio")
public class TwilioComponent extends AbstractApiComponent<TwilioApiName, TwilioConfiguration, TwilioApiCollection> {

    @Metadata
    private TwilioConfiguration configuration = new TwilioConfiguration();
    @Metadata(label = "common,security", secret = true)
    private String username;
    @Metadata(label = "common,security", secret = true)
    private String password;
    @Metadata(label = "common,security", secret = true)
    private String accountSid;
    @Metadata(label = "advanced", autowired = true)
    private TwilioRestClient restClient;

    public TwilioComponent() {
        super(TwilioApiName.class, TwilioApiCollection.getCollection());
    }

    public TwilioComponent(CamelContext context) {
        super(context, TwilioApiName.class, TwilioApiCollection.getCollection());
    }

    @Override
    protected TwilioApiName getApiName(String apiNameStr) {
        return getCamelContext().getTypeConverter().convertTo(TwilioApiName.class, apiNameStr);
    }

    @Override
    protected Endpoint createEndpoint(
            String uri, String methodName, TwilioApiName apiName,
            TwilioConfiguration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        return new TwilioEndpoint(uri, this, apiName, methodName, endpointConfiguration);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (restClient == null) {
            if (ObjectHelper.isEmpty(username) && ObjectHelper.isEmpty(password)) {
                throw new IllegalStateException("Unable to initialise Twilio, Twilio component configuration is missing");
            }
            if (ObjectHelper.isEmpty(accountSid)) {
                accountSid = username;
            }
            restClient = new TwilioRestClient.Builder(username, password)
                    .accountSid(accountSid)
                    .build();
        }
    }

    @Override
    public void doShutdown() throws Exception {
        restClient = null;
        super.doShutdown();
    }

    @Override
    public TwilioConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use the shared configuration
     */
    @Override
    public void setConfiguration(TwilioConfiguration configuration) {
        this.configuration = configuration;
    }

    public TwilioRestClient getRestClient() {
        return restClient;
    }

    /**
     * To use the shared REST client
     */
    public void setRestClient(TwilioRestClient restClient) {
        this.restClient = restClient;
    }

    public String getUsername() {
        return username;
    }

    /**
     * The account to use.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Auth token for the account.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getAccountSid() {
        return accountSid;
    }

    /**
     * The account SID to use.
     */
    public void setAccountSid(String accountSid) {
        this.accountSid = accountSid;
    }
}
