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
package org.apache.camel.component.ibm.watsonx.data;

import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.WatsonxData;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.OAuthHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Interact with IBM watsonx.data lakehouse for catalog, schema, table, and engine management.
 */
@UriEndpoint(firstVersion = "4.19.0",
             scheme = "ibm-watsonx-data",
             title = "IBM watsonx.data",
             syntax = "ibm-watsonx-data:label",
             producerOnly = true,
             category = { Category.CLOUD, Category.DATABASE },
             headersClass = WatsonxDataConstants.class)
public class WatsonxDataEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriPath(description = "Logical name for the endpoint")
    @Metadata(required = true)
    private String label;

    @UriParam
    private WatsonxDataConfiguration configuration;

    private WatsonxData watsonxDataService;

    public WatsonxDataEndpoint(String uri, WatsonxDataComponent component, WatsonxDataConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new WatsonxDataProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for IBM watsonx.data");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        resolveOAuthToken();
        validateConfiguration();
    }

    private void resolveOAuthToken() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getOauthProfile())) {
            return;
        }
        configuration.setApiKey(OAuthHelper.resolveOAuthToken(getCamelContext(), configuration.getOauthProfile()));
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        watsonxDataService = null;
    }

    private void validateConfiguration() {
        if (configuration.getApiKey() == null || configuration.getApiKey().isEmpty()) {
            throw new IllegalArgumentException("API key is required");
        }
        if (configuration.getServiceUrl() == null || configuration.getServiceUrl().isEmpty()) {
            throw new IllegalArgumentException("Service URL is required");
        }
    }

    /**
     * Gets or creates the WatsonxData service instance.
     */
    public WatsonxData getWatsonxDataService() {
        if (watsonxDataService == null) {
            IamAuthenticator authenticator = new IamAuthenticator.Builder()
                    .apikey(configuration.getApiKey())
                    .build();
            watsonxDataService = new WatsonxData("watsonxdata", authenticator);
            watsonxDataService.setServiceUrl(configuration.getServiceUrl());
        }
        return watsonxDataService;
    }

    // Getters and Setters

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public WatsonxDataConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(WatsonxDataConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String getServiceUrl() {
        return configuration.getServiceUrl();
    }

    @Override
    public String getServiceProtocol() {
        return "https";
    }
}
