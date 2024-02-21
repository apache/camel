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
package org.apache.camel.component.google.drive;

import java.util.Map;

import com.google.api.services.drive.Drive;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiCollection;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiName;
import org.apache.camel.component.google.drive.internal.GoogleDriveConstants;
import org.apache.camel.component.google.drive.internal.GoogleDrivePropertiesHelper;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.component.AbstractApiEndpoint;
import org.apache.camel.support.component.ApiMethod;
import org.apache.camel.support.component.ApiMethodPropertiesHelper;

/**
 * Manage files in Google Drive.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "google-drive", title = "Google Drive",
             syntax = "google-drive:apiName/methodName", apiSyntax = "apiName/methodName",
             consumerPrefix = "consumer", category = { Category.FILE, Category.CLOUD, Category.API })
public class GoogleDriveEndpoint extends AbstractApiEndpoint<GoogleDriveApiName, GoogleDriveConfiguration> {
    private Object apiProxy;

    @UriParam
    private GoogleDriveConfiguration configuration;

    @UriParam
    private GoogleDriveClientFactory clientFactory;

    public GoogleDriveEndpoint(String uri, GoogleDriveComponent component,
                               GoogleDriveApiName apiName, String methodName, GoogleDriveConfiguration endpointConfiguration) {
        super(uri, component, apiName, methodName, GoogleDriveApiCollection.getCollection().getHelper(apiName),
              endpointConfiguration);
        this.configuration = endpointConfiguration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new GoogleDriveProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        // make sure inBody is not set for consumers
        if (inBody != null) {
            throw new IllegalArgumentException("Option inBody is not supported for consumer endpoint");
        }
        final GoogleDriveConsumer consumer = new GoogleDriveConsumer(this, processor);
        // also set consumer.* properties
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected ApiMethodPropertiesHelper<GoogleDriveConfiguration> getPropertiesHelper() {
        return GoogleDrivePropertiesHelper.getHelper(getCamelContext());
    }

    @Override
    protected String getThreadProfileName() {
        return GoogleDriveConstants.THREAD_PROFILE_NAME;
    }

    @Override
    protected void afterConfigureProperties() {
        switch (apiName) {
            case DRIVE_ABOUT:
                apiProxy = getClient().about();
                break;
            case DRIVE_CHANGES:
                apiProxy = getClient().changes();
                break;
            case DRIVE_CHANNELS:
                apiProxy = getClient().channels();
                break;
            case DRIVE_COMMENTS:
                apiProxy = getClient().comments();
                break;
            case DRIVE_DRIVES:
                apiProxy = getClient().drives();
                break;
            case DRIVE_FILES:
                apiProxy = getClient().files();
                break;
            case DRIVE_PERMISSIONS:
                apiProxy = getClient().permissions();
                break;
            case DRIVE_REPLIES:
                apiProxy = getClient().replies();
                break;
            case DRIVE_REVISIONS:
                apiProxy = getClient().revisions();
                break;
            case DRIVE_TEAMDRIVES:
                apiProxy = getClient().teamdrives();
                break;
            default:
                throw new IllegalArgumentException("Invalid API name " + apiName);
        }
    }

    public Drive getClient() {
        return ((GoogleDriveComponent) getComponent()).getClient(configuration);
    }

    @Override
    public Object getApiProxy(ApiMethod method, Map<String, Object> args) {
        return apiProxy;
    }

    public GoogleDriveClientFactory getClientFactory() {
        return clientFactory;
    }

    /**
     * To use the GoogleCalendarClientFactory as factory for creating the client. Will by default use
     * {@link BatchGoogleDriveClientFactory}
     */
    public void setClientFactory(GoogleDriveClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }
}
