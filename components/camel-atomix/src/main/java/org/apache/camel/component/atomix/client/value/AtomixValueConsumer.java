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
package org.apache.camel.component.atomix.client.value;

import java.util.ArrayList;
import java.util.List;

import io.atomix.catalyst.concurrent.Listener;
import io.atomix.variables.DistributedValue;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.atomix.client.AbstractAtomixClientConsumer;
import org.apache.camel.component.atomix.client.AtomixClientConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AtomixValueConsumer extends AbstractAtomixClientConsumer<AtomixValueEndpoint> {

    private static final Logger LOG = LoggerFactory.getLogger(AtomixValueConsumer.class);

    private final List<Listener<DistributedValue.ChangeEvent<Object>>> listeners;
    private final String resourceName;
    private final String resultHeader;
    private DistributedValue<Object> value;

    public AtomixValueConsumer(AtomixValueEndpoint endpoint, Processor processor, String resourceName) {
        super(endpoint, processor);
        this.listeners = new ArrayList<>();
        this.resourceName = resourceName;
        this.resultHeader = endpoint.getConfiguration().getResultHeader();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.value = getAtomixEndpoint()
            .getAtomix()
            .getValue(
                resourceName,
                new DistributedValue.Config(getAtomixEndpoint().getConfiguration().getResourceOptions(resourceName)),
                new DistributedValue.Options(getAtomixEndpoint().getConfiguration().getResourceConfig(resourceName)))
            .join();


        LOG.debug("Subscribe to events for value: {}", resourceName);
        this.listeners.add(this.value.onChange(this::onEvent).join());
    }

    @Override
    protected void doStop() throws Exception {
        // close listeners
        listeners.forEach(Listener::close);

        super.doStart();
    }

    // ********************************************
    // Event handler
    // ********************************************

    private void onEvent(DistributedValue.ChangeEvent<Object> event) {
        Exchange exchange = getEndpoint().createExchange();
        exchange.getIn().setHeader(AtomixClientConstants.EVENT_TYPE, event.type());
        exchange.getIn().setHeader(AtomixClientConstants.RESOURCE_OLD_VALUE, event.oldValue());

        if (resultHeader == null) {
            exchange.getIn().setBody(event.newValue());
        } else {
            exchange.getIn().setHeader(resultHeader, event.newValue());
        }

        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            getExceptionHandler().handleException(e);
        }
    }
}
