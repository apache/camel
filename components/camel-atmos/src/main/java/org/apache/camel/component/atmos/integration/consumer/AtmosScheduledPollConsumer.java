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
package org.apache.camel.component.atmos.integration.consumer;

import org.apache.camel.Processor;
import org.apache.camel.component.atmos.AtmosConfiguration;
import org.apache.camel.component.atmos.AtmosEndpoint;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class AtmosScheduledPollConsumer extends ScheduledPollConsumer {
    protected static final transient Logger LOG = LoggerFactory.getLogger(AtmosScheduledPollConsumer.class);
    protected AtmosEndpoint endpoint;
    protected AtmosConfiguration configuration;

    public AtmosScheduledPollConsumer(AtmosEndpoint endpoint, Processor processor, AtmosConfiguration configuration) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.configuration = configuration;
    }

    @Override
    protected abstract int poll() throws Exception;

    /**
     * Lifecycle method invoked when the consumer has created.
     * Internally create or reuse a connection to the low level atmos client
     * @throws Exception
     */
    @Override
    protected void doStart() throws Exception {
        if (configuration.getClient() == null) {
            //create atmos client
            configuration.createClient();

            LOG.info("consumer atmos client created");
        }

        super.doStart();
    }

    /**
     * Lifecycle method invoked when the consumer has destroyed.
     * Erase the reference to the atmos low level client
     * @throws Exception
     */
    @Override
    protected void doStop() throws Exception {
        if (configuration.getClient() == null) {
            configuration.setClient(null);

            LOG.info("consumer atmos client deleted");
        }
        super.doStop();
    }
}
