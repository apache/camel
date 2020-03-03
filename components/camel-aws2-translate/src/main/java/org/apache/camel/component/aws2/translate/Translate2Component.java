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
package org.apache.camel.component.aws2.translate;

import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import software.amazon.awssdk.services.translate.TranslateClient;

/**
 * For working with Amazon Translate SDK v2.
 */
@Component("aws2-translate")
public class Translate2Component extends DefaultComponent {

    @Metadata
    private Translate2Configuration configuration = new Translate2Configuration();

    public Translate2Component() {
        this(null);
    }

    public Translate2Component(CamelContext context) {
        super(context);

        registerExtension(new Translate2ComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Translate2Configuration configuration = this.configuration != null ? this.configuration.copy() : new Translate2Configuration();

        Translate2Endpoint endpoint = new Translate2Endpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration);
        if (configuration.getTranslateClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("Amazon translate client or accessKey and secretKey must be specified");
        }
        return endpoint;
    }

    public Translate2Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration
     */
    public void setConfiguration(Translate2Configuration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(Translate2Configuration configuration) {
        Set<TranslateClient> clients = getCamelContext().getRegistry().findByType(TranslateClient.class);
        if (clients.size() == 1) {
            configuration.setTranslateClient(clients.stream().findFirst().get());
        }
    }
}
