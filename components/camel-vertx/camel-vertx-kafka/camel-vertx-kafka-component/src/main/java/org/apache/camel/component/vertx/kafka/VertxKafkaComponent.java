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
package org.apache.camel.component.vertx.kafka;

import java.util.Map;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.apache.camel.component.vertx.kafka.offset.DefaultVertxKafkaManualCommitFactory;
import org.apache.camel.component.vertx.kafka.offset.VertxKafkaManualCommitFactory;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;

@Component("vertx-kafka")
public class VertxKafkaComponent extends DefaultComponent {

    @Metadata
    private VertxKafkaConfiguration configuration = new VertxKafkaConfiguration();
    private boolean managedVertx;

    @Metadata(label = "advanced", autowired = true)
    private Vertx vertx;
    @Metadata(label = "advanced")
    private VertxOptions vertxOptions;
    @Metadata(label = "advanced", autowired = true)
    private VertxKafkaClientFactory vertxKafkaClientFactory;
    @Metadata(label = "consumer,advanced", autowired = true)
    private VertxKafkaManualCommitFactory kafkaManualCommitFactory;

    public VertxKafkaComponent() {
    }

    public VertxKafkaComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (ObjectHelper.isEmpty(remaining)) {
            throw new IllegalArgumentException("Topic must be configured on endpoint using syntax kafka:topic");
        }

        final VertxKafkaConfiguration configuration
                = this.configuration != null ? this.configuration.copy() : new VertxKafkaConfiguration();
        configuration.setTopic(remaining);

        final VertxKafkaEndpoint endpoint = new VertxKafkaEndpoint(uri, this, configuration);

        // extract the additional properties map
        if (PropertiesHelper.hasProperties(parameters, "additionalProperties.")) {
            final Map<String, Object> additionalProperties = endpoint.getConfiguration().getAdditionalProperties();

            // add and overwrite additional properties from endpoint to
            // pre-configured properties
            additionalProperties.putAll(PropertiesHelper.extractProperties(parameters, "additionalProperties."));
        }

        setProperties(endpoint, parameters);

        return endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (vertx == null) {
            if (vertxOptions != null) {
                vertx = Vertx.vertx(vertxOptions);
            } else {
                vertx = Vertx.vertx();
            }
            managedVertx = true;
        }

        if (vertxKafkaClientFactory == null) {
            vertxKafkaClientFactory = new DefaultVertxKafkaClientFactory();
        }

        if (kafkaManualCommitFactory == null) {
            kafkaManualCommitFactory = new DefaultVertxKafkaManualCommitFactory();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (managedVertx && vertx != null) {
            vertx.close();
            vertx = null;
        }

        super.doStop();
    }

    /**
     * The component configurations
     */
    public VertxKafkaConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(VertxKafkaConfiguration configuration) {
        this.configuration = configuration;
    }

    public Vertx getVertx() {
        return vertx;
    }

    /**
     * To use an existing vertx instead of creating a new instance
     */
    public void setVertx(Vertx vertx) {
        this.vertx = vertx;
    }

    public VertxOptions getVertxOptions() {
        return vertxOptions;
    }

    /**
     * To provide a custom set of vertx options for configuring vertx
     */
    public void setVertxOptions(VertxOptions vertxOptions) {
        this.vertxOptions = vertxOptions;
    }

    public VertxKafkaClientFactory getVertxKafkaClientFactory() {
        return vertxKafkaClientFactory;
    }

    /**
     * Factory to use for creating {@Link io.vertx.kafka.client.consumer.KafkaConsumer} and
     * {@Link io.vertx.kafka.client.consumer.KafkaProducer} instances. This allows to configure a custom factory to
     * create custom KafkaConsumer and KafkaProducer instances with logic that extends the vanilla VertX Kafka clients.
     */
    public void setVertxKafkaClientFactory(VertxKafkaClientFactory vertxKafkaClientFactory) {
        this.vertxKafkaClientFactory = vertxKafkaClientFactory;
    }

    /**
     * Factory to use for creating {@link org.apache.camel.component.vertx.kafka.offset.VertxKafkaManualCommit}
     * instances. This allows to plugin a custom factory to create custom
     * {@link org.apache.camel.component.vertx.kafka.offset.VertxKafkaManualCommit} instances in case special logic is
     * needed when doing manual commits that deviates from the default implementation that comes out of the box.
     */
    public VertxKafkaManualCommitFactory getKafkaManualCommitFactory() {
        return kafkaManualCommitFactory;
    }

    public void setKafkaManualCommitFactory(VertxKafkaManualCommitFactory kafkaManualCommitFactory) {
        this.kafkaManualCommitFactory = kafkaManualCommitFactory;
    }
}
