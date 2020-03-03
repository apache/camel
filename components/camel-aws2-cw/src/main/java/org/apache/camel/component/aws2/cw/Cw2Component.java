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
package org.apache.camel.component.aws2.cw;

import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;

/**
 * For working with Amazon CloudWatch SDK v2.
 */
@Component("aws2-cw")
public class Cw2Component extends DefaultComponent {

    @Metadata
    private Cw2Configuration configuration = new Cw2Configuration();

    public Cw2Component() {
        this(null);
    }

    public Cw2Component(CamelContext context) {
        super(context);
        registerExtension(new Cw2ComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Metric namespace must be specified.");
        }

        Cw2Configuration configuration = this.configuration != null ? this.configuration.copy() : new Cw2Configuration();
        configuration.setNamespace(remaining);

        Cw2Endpoint endpoint = new Cw2Endpoint(uri, this, configuration);
        // set component level options before overriding from endpoint
        // parameters
        setProperties(endpoint, parameters);

        checkAndSetRegistryClient(configuration);
        if (configuration.getAmazonCwClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("AmazonCwClient or accessKey and secretKey must be specified");
        }

        return endpoint;
    }

    public Cw2Configuration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration
     */
    public void setConfiguration(Cw2Configuration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(Cw2Configuration configuration) {
        Set<CloudWatchClient> clients = getCamelContext().getRegistry().findByType(CloudWatchClient.class);
        if (clients.size() == 1) {
            configuration.setAmazonCwClient(clients.stream().findFirst().get());
        }
    }
}
