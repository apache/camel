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
package org.apache.camel.component.google.firestore;

import java.util.Map;

import com.google.cloud.firestore.Firestore;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Store and retrieve data from Google Cloud Firestore NoSQL database.
 *
 * Google Firestore Endpoint represents a collection within Firestore and contains configuration to customize the
 * behavior of Consumer and Producer.
 */
@UriEndpoint(firstVersion = "4.18.0", scheme = "google-firestore", title = "Google Firestore",
             syntax = "google-firestore:collectionName",
             category = { Category.CLOUD, Category.DATABASE }, headersClass = GoogleFirestoreConstants.class)
public class GoogleFirestoreEndpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleFirestoreEndpoint.class);

    @UriParam
    private GoogleFirestoreConfiguration configuration;

    private Firestore firestoreClient;

    public GoogleFirestoreEndpoint(String uri, GoogleFirestoreComponent component,
                                   GoogleFirestoreConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new GoogleFirestoreProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer = new GoogleFirestoreConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public GoogleFirestoreComponent getComponent() {
        return (GoogleFirestoreComponent) super.getComponent();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.firestoreClient = configuration.getFirestoreClient();
        if (this.firestoreClient == null) {
            this.firestoreClient = GoogleFirestoreConnectionFactory.create(this.getCamelContext(), configuration);
        }

        LOG.debug("Firestore endpoint started for collection: {}", configuration.getCollectionName());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        // Only close if we created the client (not if it was provided externally)
        if (configuration.getFirestoreClient() == null && firestoreClient != null) {
            firestoreClient.close();
            LOG.debug("Firestore client closed");
        }
    }

    public GoogleFirestoreConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Setup configuration
     */
    public void setConfiguration(GoogleFirestoreConfiguration configuration) {
        this.configuration = configuration;
    }

    public Firestore getFirestoreClient() {
        return firestoreClient;
    }

    @Override
    public String getServiceUrl() {
        if (ObjectHelper.isNotEmpty(configuration.getCollectionName())) {
            StringBuilder url = new StringBuilder(getServiceProtocol()).append(":");
            if (ObjectHelper.isNotEmpty(configuration.getProjectId())) {
                url.append(configuration.getProjectId()).append(":");
            }
            if (ObjectHelper.isNotEmpty(configuration.getDatabaseId())) {
                url.append(configuration.getDatabaseId()).append(":");
            }
            url.append(configuration.getCollectionName());
            return url.toString();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "firestore";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getProjectId() != null) {
            return Map.of("projectId", configuration.getProjectId());
        }
        return null;
    }
}
