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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.api.services.drive.Drive;
import org.apache.camel.*;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiCollection;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiName;
import org.apache.camel.component.google.drive.internal.GoogleDriveConstants;
import org.apache.camel.component.google.drive.internal.GoogleDrivePropertiesHelper;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.component.AbstractApiEndpoint;
import org.apache.camel.support.component.ApiMethod;
import org.apache.camel.support.component.ApiMethodPropertiesHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Manage files in Google Drive.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "google-drive", title = "Google Drive",
             syntax = "google-drive:apiName/methodName", apiSyntax = "apiName/methodName",
             consumerPrefix = "consumer", category = { Category.FILE, Category.CLOUD, Category.API })
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO,
                                   description = "One message per poll with the return value of the method: a Google"
                                                 + " Drive model object, such as the FileList of drive-files/list, which is"
                                                 + " one message and not split per file. The model classes are GenericJson,"
                                                 + " which is also a Map of the JSON fields"),
           producerIn = @BodyType(value = Carrier.ANY, dependsOn = "inBody",
                                  description = "The body is used only when inBody names a parameter of the method, and"
                                                + " must then be of the type of that parameter: a File for content of"
                                                + " drive-files/create, or text for fileId. JSON text or a Map is not turned"
                                                + " into a model object: build it first. To upload a file, convertBodyTo"
                                                + " com.google.api.services.drive.model.File after a file consumer: it also"
                                                + " sets the CamelGoogleDrive.content and CamelGoogleDrive.mediaContent"
                                                + " headers, so drive-files/create sends the content of the file. Without"
                                                + " inBody the arguments come from the options and the CamelGoogleDrive."
                                                + " headers"),
           producerOut = @BodyType(value = { Carrier.POJO, Carrier.NULL },
                                   description = "The return value of the method: a Google Drive model object, such as the"
                                                 + " File of drive-files/create and drive-files/get or the FileList of"
                                                 + " drive-files/list, never the content of a file; null for the methods"
                                                 + " without a response, such as the deletes and drive-files/emptyTrash, and"
                                                 + " for drive-files/export. The model classes are GenericJson, which is also"
                                                 + " a Map of the JSON fields"))
public class GoogleDriveEndpoint extends AbstractApiEndpoint<GoogleDriveApiName, GoogleDriveConfiguration>
        implements EndpointServiceLocation {
    private Object apiProxy;

    @UriParam
    private GoogleDriveConfiguration configuration;
    private final Lock clientLock = new ReentrantLock();
    private volatile Drive client;

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

    /**
     * The client of this endpoint, created on first use from the options of this endpoint and kept for the lifetime of
     * the endpoint.
     */
    public Drive getClient() {
        Drive answer = client;
        if (answer == null) {
            clientLock.lock();
            try {
                if (client == null) {
                    client = ((GoogleDriveComponent) getComponent()).getClient(configuration, clientFactory);
                }
                answer = client;
            } finally {
                clientLock.unlock();
            }
        }
        return answer;
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
        return "drive";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getApplicationName() != null) {
            return Map.of("applicationName", configuration.getApplicationName());
        }
        return null;
    }
}
