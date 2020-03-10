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
package org.apache.camel.component.aws.translate;

import java.util.Map;
import java.util.Set;

import com.amazonaws.services.translate.AmazonTranslate;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * For working with Amazon Translate.
 */
@Component("aws-translate")
public class TranslateComponent extends DefaultComponent {

    @Metadata
    private TranslateConfiguration configuration = new TranslateConfiguration();

    public TranslateComponent() {
        this(null);
    }

    public TranslateComponent(CamelContext context) {
        super(context);

        registerExtension(new TranslateComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        TranslateConfiguration configuration = this.configuration != null ? this.configuration.copy() : new TranslateConfiguration();

        TranslateEndpoint endpoint = new TranslateEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration);
        if (configuration.getTranslateClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("Amazon translate client or accessKey and secretKey must be specified");
        }
        return endpoint;
    }

    public TranslateConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration
     */
    public void setConfiguration(TranslateConfiguration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(TranslateConfiguration configuration) {
        Set<AmazonTranslate> clients = getCamelContext().getRegistry().findByType(AmazonTranslate.class);
        if (clients.size() == 1) {
            configuration.setTranslateClient(clients.stream().findFirst().get());
        }
    }
}
