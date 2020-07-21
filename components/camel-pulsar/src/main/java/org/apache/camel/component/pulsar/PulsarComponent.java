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
package org.apache.camel.component.pulsar;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.pulsar.utils.AutoConfiguration;
import org.apache.camel.component.pulsar.utils.PulsarPath;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.pulsar.client.api.PulsarClient;

@Component("pulsar")
public class PulsarComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private AutoConfiguration autoConfiguration;
    @Metadata(label = "advanced")
    private PulsarClient pulsarClient;
    @Metadata(label = "consumer,advanced")
    private PulsarMessageReceiptFactory pulsarMessageReceiptFactory = new DefaultPulsarMessageReceiptFactory();
    @Metadata
    private PulsarConfiguration configuration = new PulsarConfiguration();

    public PulsarComponent() {
    }

    public PulsarComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(final String uri, final String path, final Map<String, Object> parameters) throws Exception {
        if (autoConfiguration != null && autoConfiguration.isAutoConfigurable()) {
            autoConfiguration.ensureNameSpaceAndTenant(path);
        }

        final PulsarConfiguration copy = configuration.copy();

        PulsarEndpoint answer = new PulsarEndpoint(uri, this);
        answer.setPulsarConfiguration(copy);
        answer.setPulsarClient(pulsarClient);
        setProperties(answer, parameters);

        PulsarPath pp = new PulsarPath(path);
        if (pp.isAutoConfigurable()) {
            answer.setPersistence(pp.getPersistence());
            answer.setTenant(pp.getTenant());
            answer.setNamespace(pp.getNamespace());
            answer.setTopic(pp.getTopic());
        } else {
            throw new IllegalArgumentException("Pulsar name structure is invalid: was " + path);
        }

        return answer;
    }

    public PulsarConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Allows to pre-configure the Pulsar component with common options that the
     * endpoints will reuse.
     */
    public void setConfiguration(PulsarConfiguration configuration) {
        this.configuration = configuration;
    }

    public AutoConfiguration getAutoConfiguration() {
        return autoConfiguration;
    }

    /**
     * The pulsar auto configuration
     */
    public void setAutoConfiguration(AutoConfiguration autoConfiguration) {
        this.autoConfiguration = autoConfiguration;
    }

    public PulsarClient getPulsarClient() {
        return pulsarClient;
    }

    /**
     * The pulsar client
     */
    public void setPulsarClient(PulsarClient pulsarClient) {
        this.pulsarClient = pulsarClient;
    }

    public PulsarMessageReceiptFactory getPulsarMessageReceiptFactory() {
        return pulsarMessageReceiptFactory;
    }

    /**
     * Provide a factory to create an alternate implementation of
     * {@link PulsarMessageReceipt}.
     */
    public void setPulsarMessageReceiptFactory(PulsarMessageReceiptFactory pulsarMessageReceiptFactory) {
        this.pulsarMessageReceiptFactory = pulsarMessageReceiptFactory;
    }
}
