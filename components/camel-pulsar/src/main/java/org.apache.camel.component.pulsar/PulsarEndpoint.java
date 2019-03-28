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
import org.apache.camel.spi.UriPath;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;

@UriEndpoint(scheme = "pulsar", title = "Apache Pulsar", syntax = "pulsar:[persistent|non-persistent]://tenant/namespace/topic", label = "messaging")
public class PulsarEndpoint extends DefaultEndpoint {

    private final PulsarEndpointConfiguration pulsarEndpointConfiguration;
    private final PulsarClient pulsarClient;
    @UriPath(label = "consumer,producer", description = "The Topic's full URI path including type, tenant and namespace")
    private final String topic;

    private PulsarEndpoint(String uri, String path, PulsarEndpointConfiguration pulsarEndpointConfiguration, PulsarComponent component, PulsarClient pulsarClient) throws PulsarClientException {
        super(uri, component);
        this.topic = path;
        this.pulsarEndpointConfiguration = pulsarEndpointConfiguration;
        this.pulsarClient = pulsarClient;
    }

    public static PulsarEndpoint create(final String uri,
                                        final String path,
                                        final PulsarEndpointConfiguration pulsarEndpointConfiguration,
                                        final PulsarComponent component, PulsarClient pulsarClient) throws PulsarClientException, IllegalArgumentException {

        if(null == pulsarEndpointConfiguration) {
            throw new IllegalArgumentException("PulsarEndpointConfiguration cannot be null");
        }

        return new PulsarEndpoint(uri, path, pulsarEndpointConfiguration, component, pulsarClient);
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
