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
package org.apache.camel.component.knative;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.component.knative.spi.Knative;
import org.apache.camel.component.knative.spi.KnativeConsumerFactory;
import org.apache.camel.component.knative.spi.KnativeEnvironment;
import org.apache.camel.component.knative.spi.KnativeProducerFactory;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(KnativeConstants.SCHEME)
public class KnativeComponent extends DefaultComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(KnativeComponent.class);

    @Metadata
    private KnativeConfiguration configuration;

    @Metadata
    private String environmentPath;

    @Metadata(defaultValue = "http")
    private Knative.Protocol protocol = Knative.Protocol.http;

    @Metadata
    private KnativeProducerFactory producerFactory;
    @Metadata
    private KnativeConsumerFactory consumerFactory;

    private boolean managedProducer;
    private boolean managedConsumer;

    public KnativeComponent() {
        this(null);
    }

    public KnativeComponent(CamelContext context) {
        super(context);

        this.configuration = new KnativeConfiguration();
        this.configuration.setTransportOptions(new HashMap<>());
    }

    // ************************
    //
    // Properties
    //
    // ************************

    public KnativeConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Set the configuration.
     */
    public void setConfiguration(KnativeConfiguration configuration) {
        this.configuration = ObjectHelper.notNull(configuration, "configuration");
    }

    public String getEnvironmentPath() {
        return environmentPath;
    }

    /**
     * The path ot the environment definition
     */
    public void setEnvironmentPath(String environmentPath) {
        this.environmentPath = environmentPath;
    }

    public KnativeEnvironment getEnvironment() {
        return configuration.getEnvironment();
    }

    /**
     * The environment
     */
    public void setEnvironment(KnativeEnvironment environment) {
        configuration.setEnvironment(environment);
    }

    public String getCloudEventsSpecVersion() {
        return configuration.getCloudEventsSpecVersion();
    }

    /**
     * Set the version of the cloudevents spec.
     */
    public void setCloudEventsSpecVersion(String cloudEventSpecVersion) {
        configuration.setCloudEventsSpecVersion(cloudEventSpecVersion);
    }

    public Knative.Protocol getProtocol() {
        return protocol;
    }

    /**
     * Protocol.
     */
    public KnativeComponent setProtocol(Knative.Protocol protocol) {
        this.protocol = protocol;
        return this;
    }

    public KnativeProducerFactory getProducerFactory() {
        return producerFactory;
    }

    /**
     * The protocol producer factory.
     */
    public void setProducerFactory(KnativeProducerFactory producerFactory) {
        this.producerFactory = producerFactory;
    }

    public KnativeConsumerFactory getConsumerFactory() {
        return consumerFactory;
    }

    /**
     * The protocol consumer factory.
     */
    public void setConsumerFactory(KnativeConsumerFactory consumerFactory) {
        this.consumerFactory = consumerFactory;
    }

    public Map<String, Object> getTransportOptions() {
        return configuration.getTransportOptions();
    }

    /**
     * Transport options.
     */
    public void setTransportOptions(Map<String, Object> transportOptions) {
        configuration.setTransportOptions(transportOptions);
    }

    // ************************
    //
    // Lifecycle
    //
    // ************************

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        setUpProducerFactory();
        setUpConsumerFactory();

        if (this.producerFactory != null && managedProducer) {
            ServiceHelper.initService(this.producerFactory);
        }
        if (this.consumerFactory != null && managedConsumer) {
            ServiceHelper.initService(this.consumerFactory);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (this.producerFactory != null && managedProducer) {
            ServiceHelper.startService(this.producerFactory);
        }
        if (this.consumerFactory != null && managedConsumer) {
            ServiceHelper.startService(this.consumerFactory);
        }

        if (this.producerFactory == null && this.consumerFactory == null) {
            throw new IllegalStateException("No producer or consumer factory has been configured");
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (this.producerFactory != null && managedProducer) {
            ServiceHelper.stopService(this.producerFactory);
        }
        if (this.consumerFactory != null && managedConsumer) {
            ServiceHelper.stopService(this.consumerFactory);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (ObjectHelper.isEmpty(remaining)) {
            throw new IllegalArgumentException("Expecting URI in the form of: 'knative:type/name', got '" + uri + "'");
        }

        final String type = ObjectHelper.supplyIfEmpty(StringHelper.before(remaining, "/"), () -> remaining);
        final String name = StringHelper.after(remaining, "/");
        final KnativeConfiguration conf = getKnativeConfiguration();

        conf.getFilters().putAll(
                (Map) PropertiesHelper.extractProperties(parameters, "filter.", true));
        conf.getCeOverride().putAll(
                (Map) PropertiesHelper.extractProperties(parameters, "ce.override.", true));
        conf.getTransportOptions().putAll(
                PropertiesHelper.extractProperties(parameters, "transport.", true));

        KnativeEndpoint endpoint = new KnativeEndpoint(uri, this, Knative.Type.valueOf(type), name, conf);
        setProperties(endpoint, parameters);

        return endpoint;
    }

    // ************************
    //
    // Helpers
    //
    // ************************

    private KnativeConfiguration getKnativeConfiguration() throws Exception {
        final String envConfig = System.getenv(KnativeConstants.CONFIGURATION_ENV_VARIABLE);
        final KnativeConfiguration conf = configuration.copy();

        if (conf.getTransportOptions() == null) {
            conf.setTransportOptions(new HashMap<>());
        }
        if (conf.getFilters() == null) {
            conf.setFilters(new HashMap<>());
        }
        if (conf.getCeOverride() == null) {
            conf.setCeOverride(new HashMap<>());
        }

        if (conf.getEnvironment() == null) {
            KnativeEnvironment env;

            if (environmentPath != null) {
                env = KnativeEnvironment.mandatoryLoadFromResource(getCamelContext(), this.environmentPath);
            } else if (envConfig != null) {
                env = envConfig.startsWith("file:") || envConfig.startsWith("classpath:")
                        ? KnativeEnvironment.mandatoryLoadFromResource(getCamelContext(), envConfig)
                        : KnativeEnvironment.mandatoryLoadFromSerializedString(envConfig);
            } else {
                env = CamelContextHelper.findSingleByType(getCamelContext(), KnativeEnvironment.class);
            }

            if (env == null) {
                throw new IllegalStateException("Cannot load Knative configuration from file or env variable");
            }

            conf.setEnvironment(env);
        }

        return conf;
    }

    private void setUpProducerFactory() throws Exception {
        if (producerFactory == null) {
            this.producerFactory = CamelContextHelper.lookup(getCamelContext(), protocol.name(), KnativeProducerFactory.class);

            if (this.producerFactory == null) {
                this.producerFactory = getCamelContext()
                        .getCamelContextExtension()
                        .getBootstrapFactoryFinder(Knative.KNATIVE_TRANSPORT_RESOURCE_PATH)
                        .newInstance(protocol.name() + "-producer", KnativeProducerFactory.class)
                        .orElse(null);

                if (this.producerFactory == null) {
                    return;
                }

                if (configuration.getTransportOptions() != null) {
                    setProperties(producerFactory, new HashMap<>(configuration.getTransportOptions()));
                }

                this.managedProducer = true;

                CamelContextAware.trySetCamelContext(this.producerFactory, getCamelContext());
            }

            LOGGER.info("Using Knative producer factory: {} for protocol: {}", producerFactory, protocol.name());
        }
    }

    private void setUpConsumerFactory() throws Exception {
        if (consumerFactory == null) {
            this.consumerFactory = CamelContextHelper.lookup(getCamelContext(), protocol.name(), KnativeConsumerFactory.class);

            if (this.consumerFactory == null) {
                this.consumerFactory = getCamelContext()
                        .getCamelContextExtension()
                        .getBootstrapFactoryFinder(Knative.KNATIVE_TRANSPORT_RESOURCE_PATH)
                        .newInstance(protocol.name() + "-consumer", KnativeConsumerFactory.class)
                        .orElse(null);

                if (this.consumerFactory == null) {
                    return;
                }
                if (configuration.getTransportOptions() != null) {
                    setProperties(consumerFactory, new HashMap<>(configuration.getTransportOptions()));
                }

                this.managedConsumer = true;

                CamelContextAware.trySetCamelContext(this.consumerFactory, getCamelContext());
            }

            LOGGER.info("Using Knative consumer factory: {} for protocol: {}", consumerFactory, protocol.name());
        }
    }
}
