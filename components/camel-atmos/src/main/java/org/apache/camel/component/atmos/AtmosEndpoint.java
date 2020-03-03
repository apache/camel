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
package org.apache.camel.component.atmos;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.atmos.integration.consumer.AtmosScheduledPollConsumer;
import org.apache.camel.component.atmos.integration.consumer.AtmosScheduledPollGetConsumer;
import org.apache.camel.component.atmos.integration.producer.AtmosDelProducer;
import org.apache.camel.component.atmos.integration.producer.AtmosGetProducer;
import org.apache.camel.component.atmos.integration.producer.AtmosMoveProducer;
import org.apache.camel.component.atmos.integration.producer.AtmosPutProducer;
import org.apache.camel.component.atmos.util.AtmosException;
import org.apache.camel.component.atmos.util.AtmosOperation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

import static org.apache.camel.component.atmos.util.AtmosConstants.POLL_CONSUMER_DELAY;

/**
 * The atmos component is used for integrating with EMC's Atomos Storage.
 */
@UriEndpoint(firstVersion = "2.15.0", scheme = "atmos", title = "Atmos", syntax = "atmos:name/operation", label = "file,cloud")
public class AtmosEndpoint extends DefaultEndpoint {

    @UriParam
    private AtmosConfiguration configuration;

    public AtmosEndpoint() {
    }

    public AtmosEndpoint(String uri, AtmosComponent component, AtmosConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public AtmosConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(AtmosConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Create one of the camel producer available based on the configuration
     *
     * @return the camel producer
     */
    @Override
    public Producer createProducer() throws Exception {
        if (configuration.getOperation() == AtmosOperation.put) {
            return new AtmosPutProducer(this, configuration);
        } else if (this.configuration.getOperation() == AtmosOperation.del) {
            return new AtmosDelProducer(this, configuration);
        } else if (this.configuration.getOperation() == AtmosOperation.get) {
            return new AtmosGetProducer(this, configuration);
        } else if (this.configuration.getOperation() == AtmosOperation.move) {
            return new AtmosMoveProducer(this, configuration);
        } else {
            throw new AtmosException("operation specified is not valid for producer!");
        }
    }

    /**
     * Create one of the camel consumer available based on the configuration
     *
     * @param processor the given processor
     * @return the camel consumer
     */
    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        AtmosScheduledPollConsumer consumer;
        if (this.configuration.getOperation() == AtmosOperation.get) {
            consumer = new AtmosScheduledPollGetConsumer(this, processor, configuration);
            consumer.setDelay(POLL_CONSUMER_DELAY);
            configureConsumer(consumer);
            return consumer;
        } else {
            throw new AtmosException("operation specified is not valid for consumer!");
        }
    }

}
