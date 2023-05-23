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
package org.apache.camel.component.google.sheets;

import java.util.Map;

import com.google.api.services.sheets.v4.Sheets;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.google.sheets.internal.GoogleSheetsApiCollection;
import org.apache.camel.component.google.sheets.internal.GoogleSheetsApiName;
import org.apache.camel.component.google.sheets.internal.GoogleSheetsConstants;
import org.apache.camel.component.google.sheets.internal.GoogleSheetsPropertiesHelper;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.component.AbstractApiEndpoint;
import org.apache.camel.support.component.ApiMethod;
import org.apache.camel.support.component.ApiMethodPropertiesHelper;

/**
 * Manage spreadsheets in Google Sheets.
 */
@UriEndpoint(firstVersion = "2.23.0", scheme = "google-sheets", title = "Google Sheets",
             syntax = "google-sheets:apiName/methodName", apiSyntax = "apiName/methodName",
             category = { Category.CLOUD, Category.DOCUMENT })
public class GoogleSheetsEndpoint extends AbstractApiEndpoint<GoogleSheetsApiName, GoogleSheetsConfiguration> {

    @UriParam
    private GoogleSheetsConfiguration configuration;

    private Object apiProxy;

    public GoogleSheetsEndpoint(String uri, GoogleSheetsComponent component, GoogleSheetsApiName apiName, String methodName,
                                GoogleSheetsConfiguration endpointConfiguration) {
        super(uri, component, apiName, methodName, GoogleSheetsApiCollection.getCollection().getHelper(apiName),
              endpointConfiguration);
        this.configuration = endpointConfiguration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new GoogleSheetsProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        // make sure inBody is not set for consumers
        if (inBody != null) {
            throw new IllegalArgumentException("Option inBody is not supported for consumer endpoint");
        }
        final GoogleSheetsConsumer consumer = new GoogleSheetsConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected ApiMethodPropertiesHelper<GoogleSheetsConfiguration> getPropertiesHelper() {
        return GoogleSheetsPropertiesHelper.getHelper(getCamelContext());
    }

    @Override
    protected String getThreadProfileName() {
        return GoogleSheetsConstants.THREAD_PROFILE_NAME;
    }

    @Override
    protected void afterConfigureProperties() {
        switch (apiName) {
            case SPREADSHEETS:
                apiProxy = getClient().spreadsheets();
                break;
            case DATA:
                apiProxy = getClient().spreadsheets().values();
                break;
            default:
                throw new IllegalArgumentException("Invalid API name " + apiName);
        }
    }

    public Sheets getClient() {
        return ((GoogleSheetsComponent) getComponent()).getClient(configuration);
    }

    @Override
    public Object getApiProxy(ApiMethod method, Map<String, Object> args) {
        return apiProxy;
    }

    public GoogleSheetsClientFactory getClientFactory() {
        return ((GoogleSheetsComponent) getComponent()).getClientFactory();
    }

    public void setClientFactory(GoogleSheetsClientFactory clientFactory) {
        ((GoogleSheetsComponent) getComponent()).setClientFactory(clientFactory);
    }
}
