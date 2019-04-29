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

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.pulsar.configuration.PulsarConfiguration;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;

@UriEndpoint(scheme = "pulsar", title = "Apache Pulsar", syntax = "pulsar:persistence://tenant/namespace/topic", label = "messaging")
public class PulsarEndpoint extends DefaultEndpoint {

    private PulsarClient pulsarClient;
    @UriParam
    private PulsarConfiguration pulsarConfiguration;
    @UriPath(label = "consumer,producer", description = "The Topic's full URI path including type, tenant and namespace")
    private final String topic;

    public PulsarEndpoint(String uri, String path, PulsarConfiguration pulsarConfiguration, PulsarComponent component, PulsarClient pulsarClient) throws PulsarClientException {
        super(uri, component);
        this.topic = path;
        this.pulsarConfiguration = pulsarConfiguration;
        this.pulsarClient = pulsarClient;
    }

    public static PulsarEndpoint create(final String uri, final String path, final PulsarConfiguration pulsarConfiguration, final PulsarComponent component,
                                        final PulsarClient pulsarClient)
        throws PulsarClientException, IllegalArgumentException {

        if (null == pulsarConfiguration) {
            throw new IllegalArgumentException("PulsarEndpointConfiguration cannot be null");
        }

        return new PulsarEndpoint(uri, path, pulsarConfiguration, component, pulsarClient);
    }

    @Override
    public Producer createProducer() {
        return new PulsarProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        PulsarConsumer consumer = new PulsarConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public Exchange createExchange() {
        return super.createExchange();
    }

    public PulsarClient getPulsarClient() {
        return pulsarClient;
    }

    public PulsarConfiguration getPulsarConfiguration() {
        return pulsarConfiguration;
    }

    public String getTopic() {
        return topic;
    }

}
