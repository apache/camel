/**
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

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriEndpoint(scheme = "pulsar", title = "Apache Pulsar", syntax = "pulsar:tenant/namespace/topic", label = "messaging")
public class PulsarEndpoint extends DefaultEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarEndpoint.class);

    @UriParam
    private final PulsarEndpointConfiguration pulsarEndpointConfiguration;
    @UriParam
    private final PulsarClient pulsarClient;
    @UriPath(label = "consumer,producer", description = "Topic uri path")
    private final String topic;

    private PulsarEndpoint(String uri, String path, PulsarEndpointConfiguration pulsarEndpointConfiguration, PulsarComponent component) throws PulsarClientException {
        super(uri, component);
        this.topic = path;
        this.pulsarEndpointConfiguration = pulsarEndpointConfiguration;
        this.pulsarClient = pulsarEndpointConfiguration.getPulsarClient();
    }

    public static PulsarEndpoint create(String uri, String path, PulsarEndpointConfiguration pulsarEndpointConfiguration, PulsarComponent component) throws PulsarClientException {
        if (pulsarEndpointConfiguration == null) {
            IllegalArgumentException illegalArgumentException = new IllegalArgumentException("Pulsar client and Pulsar Endpoint Configuration cannot be null");
            LOGGER.error("An exception occurred while creating Pulsar Endpoint :: {}", illegalArgumentException);
            throw illegalArgumentException;
        }
        return new PulsarEndpoint(uri, path, pulsarEndpointConfiguration, component);
    }

    @Override
    public Producer createProducer() {
        return PulsarProducer.create(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        PulsarConsumer consumer = PulsarConsumer.create(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    @Override
    public Exchange createExchange() {
        return super.createExchange();
    }

    public PulsarClient getPulsarClient() {
        return pulsarClient;
    }

    public PulsarEndpointConfiguration getConfiguration() {
        return pulsarEndpointConfiguration;
    }

    public String getTopic() {
        return topic;
    }
}
