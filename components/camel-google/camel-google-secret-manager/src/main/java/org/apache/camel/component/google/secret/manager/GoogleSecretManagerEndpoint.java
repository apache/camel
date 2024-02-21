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
package org.apache.camel.component.google.secret.manager;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Manage Google Secret Manager Secrets
 *
 * Google Secret Manager Endpoint.
 */
@UriEndpoint(firstVersion = "3.16.0", scheme = "google-secret-manager", title = "Google Secret Manager",
             syntax = "google-secret-manager:project", category = {
                     Category.CLOUD },
             producerOnly = true, headersClass = GoogleSecretManagerConstants.class)
public class GoogleSecretManagerEndpoint extends DefaultEndpoint {

    @UriParam
    private GoogleSecretManagerConfiguration configuration;

    private SecretManagerServiceClient secretManagerServiceClient;

    public GoogleSecretManagerEndpoint(String uri, GoogleSecretManagerComponent component,
                                       GoogleSecretManagerConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public Producer createProducer() throws Exception {
        return new GoogleSecretManagerProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException(
                "Cannot consume from the google-secret-manager endpoint: " + getEndpointUri());
    }

    public GoogleSecretManagerConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Setup configuration
     */
    public void setConfiguration(GoogleSecretManagerConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (configuration.getClient() != null) {
            secretManagerServiceClient = configuration.getClient();
        } else {
            secretManagerServiceClient = GoogleSecretManagerClientFactory.create(this.getCamelContext(), configuration);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (configuration.getClient() == null && secretManagerServiceClient != null) {
            secretManagerServiceClient.close();
        }
    }

    public SecretManagerServiceClient getClient() {
        return secretManagerServiceClient;
    }

}
