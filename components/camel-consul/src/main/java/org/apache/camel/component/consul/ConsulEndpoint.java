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
package org.apache.camel.component.consul;

import java.util.Optional;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.kiwiproject.consul.Consul;

/**
 * Integrate with <a href="https://www.consul.io/">Consul</a> service discovery and configuration store.
 */
@UriEndpoint(firstVersion = "2.18.0", scheme = "consul", title = "Consul", syntax = "consul:apiEndpoint",
             category = { Category.CLOUD, Category.API }, headersClass = ConsulConstants.class)
public class ConsulEndpoint extends DefaultEndpoint {

    @UriParam
    private final ConsulConfiguration configuration;

    @UriPath(description = "The API endpoint")
    @Metadata(required = true)
    private final String apiEndpoint;

    private final Optional<ConsulFactories.ProducerFactory> producerFactory;
    private final Optional<ConsulFactories.ConsumerFactory> consumerFactory;

    private Consul consul;

    public ConsulEndpoint(String apiEndpoint, String uri, ConsulComponent component, ConsulConfiguration configuration,
                          Optional<ConsulFactories.ProducerFactory> producerFactory,
                          Optional<ConsulFactories.ConsumerFactory> consumerFactory) {

        super(uri, component);

        this.configuration = ObjectHelper.notNull(configuration, "configuration");
        this.apiEndpoint = ObjectHelper.notNull(apiEndpoint, "apiEndpoint");
        this.producerFactory = producerFactory;
        this.consumerFactory = consumerFactory;
    }

    @Override
    public Producer createProducer() throws Exception {
        ConsulFactories.ProducerFactory factory
                = producerFactory.orElseThrow(() -> new IllegalArgumentException("No producer for " + apiEndpoint));

        return factory.create(this, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        ConsulFactories.ConsumerFactory factory
                = consumerFactory.orElseThrow(() -> new IllegalArgumentException("No consumer for " + apiEndpoint));

        Consumer consumer = factory.create(this, configuration, processor);
        configureConsumer(consumer);
        return consumer;
    }

    // *************************************************************************
    //
    // *************************************************************************

    public ConsulConfiguration getConfiguration() {
        return this.configuration;
    }

    public String getApiEndpoint() {
        return this.apiEndpoint;
    }

    public synchronized Consul getConsul() throws Exception {
        if (consul == null && ObjectHelper.isEmpty(getConfiguration().getConsulClient())) {
            consul = configuration.createConsulClient(getCamelContext());
        } else if (ObjectHelper.isNotEmpty(getConfiguration().getConsulClient())) {
            consul = getConfiguration().getConsulClient();
        }

        return consul;
    }
}
