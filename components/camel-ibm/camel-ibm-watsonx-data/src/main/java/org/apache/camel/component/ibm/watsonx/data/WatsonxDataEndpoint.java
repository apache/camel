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

import com.ibm.cloud.sdk.core.security.Authenticator;
import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.WatsonxData;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
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
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = @BodyType.When(option = "operation", value = "updateTable",
                                                            carriers = {
                                                                    Carrier.MAP, Carrier.TEXT, Carrier.BYTES, Carrier.STREAM,
                                                                    Carrier.FILE }),
                                  description = "Only updateTable reads the body: the patch of the table, as a Map or as a JSON"
                                                + " object in text, bytes, a stream or a file, sent as a JSON merge patch. No"
                                                + " body, an empty patch or a body that is not a JSON object fails. The other"
                                                + " operations take their input from the options and headers"),
           producerOut = @BodyType(value = Carrier.POJO,
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "deleteCatalog",
                                                          carriers = Carrier.NULL),
                                           @BodyType.When(option = "operation", value = "deleteSchema",
                                                          carriers = Carrier.NULL),
                                           @BodyType.When(option = "operation", value = "deleteTable",
                                                          carriers = Carrier.NULL) },
                                   description = "The response of the IBM watsonx.data SDK, such as a CatalogCollection for"
                                                 + " listCatalogs or a Table for getTable and updateTable; converted to text, it"
                                                 + " is its JSON. The delete operations set no body"))
public class WatsonxDataEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriPath(description = "Logical name for the endpoint")
    @Metadata(required = true)
    private String label;

    @UriParam
    private WatsonxDataConfiguration configuration;

    private WatsonxData watsonxDataService;
    // the Bearer token authenticator of the oauthProfile, created when the endpoint starts
    private OAuthProfileAuthenticator oauthAuthenticator;

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
        validateConfiguration();
        if (ObjectHelper.isNotEmpty(configuration.getOauthProfile())) {
            OAuthProfileAuthenticator authenticator
                    = new OAuthProfileAuthenticator(getCamelContext(), configuration.getOauthProfile());
            // fail at startup rather than at the first exchange when the OAuth profile cannot give a token
            authenticator.token();
            synchronized (this) {
                oauthAuthenticator = authenticator;
                // a client created before startup is created again with this authenticator
                watsonxDataService = null;
            }
        }
    }

    @Override
    protected synchronized void doStop() throws Exception {
        super.doStop();
        watsonxDataService = null;
        oauthAuthenticator = null;
    }

    private void validateConfiguration() {
        if (ObjectHelper.isEmpty(configuration.getApiKey()) && ObjectHelper.isEmpty(configuration.getOauthProfile())) {
            throw new IllegalArgumentException("API key is required (or set oauthProfile)");
        }
        if (configuration.getServiceUrl() == null || configuration.getServiceUrl().isEmpty()) {
            throw new IllegalArgumentException("Service URL is required");
        }
    }

    /**
     * Gets or creates the WatsonxData service instance: it authenticates with the Bearer token of the OAuth profile
     * when {@code oauthProfile} is set, else with an IBM Cloud IAM token for the {@code apiKey}.
     */
    public synchronized WatsonxData getWatsonxDataService() {
        if (watsonxDataService == null) {
            Authenticator authenticator;
            if (ObjectHelper.isNotEmpty(configuration.getOauthProfile())) {
                if (oauthAuthenticator == null) {
                    oauthAuthenticator = new OAuthProfileAuthenticator(getCamelContext(), configuration.getOauthProfile());
                }
                authenticator = oauthAuthenticator;
            } else {
                authenticator = new IamAuthenticator.Builder().apikey(configuration.getApiKey()).build();
            }
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
