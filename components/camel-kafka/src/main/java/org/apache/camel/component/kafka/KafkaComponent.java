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
package org.apache.camel.component.kafka;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;

public class KafkaComponent extends DefaultComponent implements SSLContextParametersAware {

    private KafkaConfiguration configuration;

    @Metadata(label = "advanced")
    private ExecutorService workerPool;
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;
    @Metadata(label = "consumer", defaultValue = "false")
    private boolean breakOnFirstError;
    @Metadata(label = "consumer", defaultValue = "false")
    private boolean allowManualCommit;
    @Metadata(label = "consumer,advanced")
    private KafkaManualCommitFactory kafkaManualCommitFactory = new DefaultKafkaManualCommitFactory();

    public KafkaComponent() {
    }

    public KafkaComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected KafkaEndpoint createEndpoint(String uri, String remaining, Map<String, Object> params) throws Exception {
        if (ObjectHelper.isEmpty(remaining)) {
            throw new IllegalArgumentException("Topic must be configured on endpoint using syntax kafka:topic");
        }

        KafkaEndpoint endpoint = new KafkaEndpoint(uri, this);

        if (configuration != null) {
            KafkaConfiguration copy = configuration.copy();
            endpoint.setConfiguration(copy);
        }

        endpoint.getConfiguration().setTopic(remaining);
        endpoint.getConfiguration().setWorkerPool(getWorkerPool());
        endpoint.getConfiguration().setBreakOnFirstError(isBreakOnFirstError());
        endpoint.getConfiguration().setAllowManualCommit(isAllowManualCommit());

        // brokers can be configured on either component or endpoint level
        // and the consumer and produce is aware of this and act accordingly

        setProperties(endpoint.getConfiguration(), params);
        setProperties(endpoint, params);

        if (endpoint.getConfiguration().getSslContextParameters() == null) {
            endpoint.getConfiguration().setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        return endpoint;
    }

    public KafkaConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Allows to pre-configure the Kafka component with common options that the endpoints will reuse.
     */
    public void setConfiguration(KafkaConfiguration configuration) {
        this.configuration = configuration;
    }

    public String getBrokers() {
        return configuration != null ? configuration.getBrokers() : null;
    }

    /**
     * URL of the Kafka brokers to use.
     * The format is host1:port1,host2:port2, and the list can be a subset of brokers or a VIP pointing to a subset of brokers.
     * <p/>
     * This option is known as <tt>bootstrap.servers</tt> in the Kafka documentation.
     */
    public void setBrokers(String brokers) {
        if (configuration == null) {
            configuration = new KafkaConfiguration();
        }
        configuration.setBrokers(brokers);
    }


    public ExecutorService getWorkerPool() {
        return workerPool;
    }

    /**
     * To use a shared custom worker pool for continue routing {@link Exchange} after kafka server has acknowledge
     * the message that was sent to it from {@link KafkaProducer} using asynchronous non-blocking processing.
     * If using this option then you must handle the lifecycle of the thread pool to shut the pool down when no longer needed.
     */
    public void setWorkerPool(ExecutorService workerPool) {
        this.workerPool = workerPool;
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    /**
     * Enable usage of global SSL context parameters.
     */
    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

    public boolean isBreakOnFirstError() {
        return breakOnFirstError;
    }

    /**
     * This options controls what happens when a consumer is processing an exchange and it fails.
     * If the option is <tt>false</tt> then the consumer continues to the next message and processes it.
     * If the option is <tt>true</tt> then the consumer breaks out, and will seek back to offset of the
     * message that caused a failure, and then re-attempt to process this message. However this can lead
     * to endless processing of the same message if its bound to fail every time, eg a poison message.
     * Therefore its recommended to deal with that for example by using Camel's error handler.
     */
    public void setBreakOnFirstError(boolean breakOnFirstError) {
        this.breakOnFirstError = breakOnFirstError;
    }


    public boolean isAllowManualCommit() {
        return allowManualCommit;
    }

    /**
     * Whether to allow doing manual commits via {@link KafkaManualCommit}.
     * <p/>
     * If this option is enabled then an instance of {@link KafkaManualCommit} is stored on the {@link Exchange} message header,
     * which allows end users to access this API and perform manual offset commits via the Kafka consumer.
     */
    public void setAllowManualCommit(boolean allowManualCommit) {
        this.allowManualCommit = allowManualCommit;
    }

    public KafkaManualCommitFactory getKafkaManualCommitFactory() {
        return kafkaManualCommitFactory;
    }

    /**
     * Factory to use for creating {@link KafkaManualCommit} instances. This allows to plugin a custom factory
     * to create custom {@link KafkaManualCommit} instances in case special logic is needed when doing manual commits
     * that deviates from the default implementation that comes out of the box.
     */
    public void setKafkaManualCommitFactory(KafkaManualCommitFactory kafkaManualCommitFactory) {
        this.kafkaManualCommitFactory = kafkaManualCommitFactory;
    }
}
