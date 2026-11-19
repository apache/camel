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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.services.sheets.v4.Sheets;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.google.sheets.internal.GoogleSheetsApiCollection;
import org.apache.camel.component.google.sheets.internal.GoogleSheetsApiName;
import org.apache.camel.component.google.sheets.internal.GoogleSheetsConstants;
import org.apache.camel.component.google.sheets.internal.GoogleSheetsPropertiesHelper;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.component.AbstractApiEndpoint;
import org.apache.camel.support.component.ApiMethod;
import org.apache.camel.support.component.ApiMethodPropertiesHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Manage spreadsheets in Google Sheets.
 */
@UriEndpoint(firstVersion = "2.23.0", scheme = "google-sheets", title = "Google Sheets",
             syntax = "google-sheets:apiName/methodName", apiSyntax = "apiName/methodName",
             category = { Category.CLOUD, Category.DOCUMENT })
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO,
                                   description = "One message per poll with the return value of the method: a Google"
                                                 + " Sheets model object, such as the ValueRange of data/get, which is one"
                                                 + " message and not split per row. No method returns a list, so"
                                                 + " splitResult has no effect: google-sheets-stream with splitResults"
                                                 + " gives one message per row. The model classes are GenericJson, which"
                                                 + " is also a Map of the JSON fields"),
           producerIn = @BodyType(value = Carrier.ANY, dependsOn = "inBody",
                                  description = "The body is used only when inBody names a parameter of the method, and"
                                                + " must then be of the type of that parameter: a ValueRange for values of"
                                                + " data/append and data/update, a Spreadsheet for content of"
                                                + " spreadsheets/create, or text for spreadsheetId. A List of rows, JSON text"
                                                + " or a Map is not turned into a model object: put the rows in a ValueRange,"
                                                + " or use the google-sheets:application-x-struct transformer on JSON."
                                                + " Without inBody the arguments come from the options and the"
                                                + " CamelGoogleSheets. headers"),
           producerOut = @BodyType(value = Carrier.POJO,
                                   description = "The return value of the method: a Google Sheets model object, such as the"
                                                 + " ValueRange of data/get, the AppendValuesResponse of data/append or the"
                                                 + " Spreadsheet of spreadsheets/get. The model classes are GenericJson, which"
                                                 + " is also a Map of the JSON fields"))
public class GoogleSheetsEndpoint extends AbstractApiEndpoint<GoogleSheetsApiName, GoogleSheetsConfiguration>
        implements EndpointServiceLocation {

    /**
     * The options that make up the credential of the client. The client sends the credential in the Authorization
     * header, so they are not set on the request, where accessToken would become the access_token query parameter. The
     * names are matched ignoring case, as a header such as CamelGoogleSheets.AccessToken sets the same property.
     */
    private static final Set<String> CREDENTIAL_OPTIONS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    static {
        CREDENTIAL_OPTIONS.addAll(
                List.of("clientId", "clientSecret", "accessToken", "refreshToken", "serviceAccountKey", "delegate"));
    }

    @UriParam
    private GoogleSheetsConfiguration configuration;
    private final Lock clientLock = new ReentrantLock();
    private volatile Sheets client;

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

    /**
     * The client of this endpoint, created on first use from the options of this endpoint and kept for the lifetime of
     * the endpoint.
     */
    public Sheets getClient() {
        Sheets answer = client;
        if (answer == null) {
            clientLock.lock();
            try {
                if (client == null) {
                    client = ((GoogleSheetsComponent) getComponent()).getClient(configuration);
                }
                answer = client;
            } finally {
                clientLock.unlock();
            }
        }
        return answer;
    }

    /**
     * Sets the options and headers on the request of the method, such as the standard parameters of the Google API,
     * except the options of the credential, which the client already sends in the Authorization header.
     */
    void configureRequest(AbstractGoogleClientRequest<?> request, Map<String, Object> properties) throws Exception {
        BeanIntrospection beanIntrospection = PluginHelper.getBeanIntrospection(getCamelContext());
        for (Map.Entry<String, Object> p : properties.entrySet()) {
            if (!CREDENTIAL_OPTIONS.contains(p.getKey())) {
                beanIntrospection.setProperty(getCamelContext(), request, p.getKey(), p.getValue());
            }
        }
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

    @Override
    public String getServiceUrl() {
        if (ObjectHelper.isNotEmpty(ObjectHelper.isNotEmpty(configuration.getApiName())
                && ObjectHelper.isNotEmpty(configuration.getMethodName()))) {
            return getServiceProtocol() + ":" + configuration.getApiName() + ":" + configuration.getMethodName();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "sheets";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getApplicationName() != null) {
            return Map.of("applicationName", configuration.getApplicationName());
        }
        return null;
    }
}
