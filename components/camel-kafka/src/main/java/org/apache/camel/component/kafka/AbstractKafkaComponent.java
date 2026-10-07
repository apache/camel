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
package org.apache.camel.component.kafka;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedStartupListener;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.HealthCheckComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for the Kafka components, holding the component options that are not tied to a specific Kafka client: the
 * poll exception strategy, the backoff used when creating a consumer, global SSL, and the deferred start of consumers
 * until the CamelContext is started.
 */
public abstract class AbstractKafkaComponent extends HealthCheckComponent
        implements SSLContextParametersAware, ExtendedStartupListener {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractKafkaComponent.class);

    private final List<Runnable> pendingConsumers = new CopyOnWriteArrayList<>();

    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;
    @Metadata(autowired = true, label = "consumer,advanced")
    private PollExceptionStrategy pollExceptionStrategy;
    @Metadata(label = "consumer,advanced")
    private int createConsumerBackoffMaxAttempts;
    @Metadata(label = "consumer,advanced", defaultValue = "5000")
    private long createConsumerBackoffInterval = 5000;

    protected AbstractKafkaComponent() {
    }

    protected AbstractKafkaComponent(CamelContext context) {
        super(context);
    }

    void pendingConsumer(Runnable task) {
        pendingConsumers.add(task);
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

    public PollExceptionStrategy getPollExceptionStrategy() {
        return pollExceptionStrategy;
    }

    /**
     * To use a custom strategy with the consumer to control how to handle exceptions thrown from the Kafka broker while
     * pooling messages.
     */
    public void setPollExceptionStrategy(PollExceptionStrategy pollExceptionStrategy) {
        this.pollExceptionStrategy = pollExceptionStrategy;
    }

    public int getCreateConsumerBackoffMaxAttempts() {
        return createConsumerBackoffMaxAttempts;
    }

    /**
     * Maximum attempts to create the kafka consumer (kafka-client), before eventually giving up and failing.
     *
     * Error during creating the consumer may be fatal due to invalid configuration and as such recovery is not
     * possible. However, one part of the validation is DNS resolution of the bootstrap broker hostnames. This may be a
     * temporary networking problem, and could potentially be recoverable. While other errors are fatal, such as some
     * invalid kafka configurations. Unfortunately, kafka-client does not separate this kind of errors.
     *
     * Camel will by default retry forever, and therefore never give up. If you want to give up after many attempts then
     * set this option and Camel will then when giving up terminate the consumer. To try again, you can manually restart
     * the consumer by stopping, and starting the route.
     */
    public void setCreateConsumerBackoffMaxAttempts(int createConsumerBackoffMaxAttempts) {
        this.createConsumerBackoffMaxAttempts = createConsumerBackoffMaxAttempts;
    }

    public long getCreateConsumerBackoffInterval() {
        return createConsumerBackoffInterval;
    }

    /**
     * The delay in millis seconds to wait before trying again to create the kafka consumer (kafka-client).
     */
    public void setCreateConsumerBackoffInterval(long createConsumerBackoffInterval) {
        this.createConsumerBackoffInterval = createConsumerBackoffInterval;
    }

    @Override
    public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) throws Exception {
        if (alreadyStarted) {
            startPendingConsumers();
        }
    }

    @Override
    public void onCamelContextFullyStarted(CamelContext context, boolean alreadyStarted) throws Exception {
        startPendingConsumers();
    }

    private void startPendingConsumers() {
        if (!pendingConsumers.isEmpty()) {
            LOG.info("Starting {} pending Kafka consumers as CamelContext is fully started", pendingConsumers.size());
            pendingConsumers.forEach(Runnable::run);
            pendingConsumers.clear();
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        pendingConsumers.clear();
    }

}
