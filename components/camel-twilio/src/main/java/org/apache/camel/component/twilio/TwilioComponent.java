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
package org.apache.camel.component.twilio;

import com.twilio.http.TwilioRestClient;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.twilio.internal.TwilioApiCollection;
import org.apache.camel.component.twilio.internal.TwilioApiName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.component.AbstractApiComponent;

/**
 * Represents the component that manages {@link TwilioEndpoint}.
 */
public class TwilioComponent extends AbstractApiComponent<TwilioApiName, TwilioConfiguration, TwilioApiCollection> {

    @Metadata(label = "advanced")
    private TwilioConfiguration configuration = new TwilioConfiguration();

    @Metadata(label = "advanced")
    private TwilioRestClient restClient;

    public TwilioComponent() {
        super(TwilioEndpoint.class, TwilioApiName.class, TwilioApiCollection.getCollection());
    }

    public TwilioComponent(CamelContext context) {
        super(context, TwilioEndpoint.class, TwilioApiName.class, TwilioApiCollection.getCollection());
    }

    @Override
    protected TwilioApiName getApiName(String apiNameStr) throws IllegalArgumentException {
        return TwilioApiName.fromValue(apiNameStr);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String methodName, TwilioApiName apiName,
                                      TwilioConfiguration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        return new TwilioEndpoint(uri, this, apiName, methodName, endpointConfiguration);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (restClient == null) {
            if (configuration == null) {
                throw new IllegalStateException("Unable to initialise Twilio, Twilio component configuration is missing");
            }
            restClient = new TwilioRestClient.Builder(configuration.getUsername(), configuration.getPassword())
                .accountSid(configuration.getAccountSid())
                .build();
        }
    }

    @Override
    public void doShutdown() throws Exception {
        restClient = null;
        super.doShutdown();
    }

    public TwilioConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use the shared configuration
     */
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
}
