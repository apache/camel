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
package org.apache.camel.component.atomix.client.set;

import java.util.ArrayList;
import java.util.List;

import io.atomix.catalyst.concurrent.Listener;
import io.atomix.collections.DistributedSet;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.atomix.client.AbstractAtomixClientConsumer;
import org.apache.camel.component.atomix.client.AtomixClientConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AtomixSetConsumer extends AbstractAtomixClientConsumer<AtomixSetEndpoint> {

    private static final Logger LOG = LoggerFactory.getLogger(AtomixSetConsumer.class);

    private final List<Listener<DistributedSet.ValueEvent<Object>>> listeners;
    private final String resourceName;
    private final String resultHeader;
    private DistributedSet<Object> set;

    public AtomixSetConsumer(AtomixSetEndpoint endpoint, Processor processor, String resourceName) {
        super(endpoint, processor);
        this.listeners = new ArrayList<>();
        this.resourceName = resourceName;
        this.resultHeader = endpoint.getConfiguration().getResultHeader();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.set = getAtomixEndpoint()
            .getAtomix()
            .getSet(
                resourceName,
                new DistributedSet.Config(getAtomixEndpoint().getConfiguration().getResourceOptions(resourceName)),
                new DistributedSet.Options(getAtomixEndpoint().getConfiguration().getResourceConfig(resourceName)))
            .join();


        LOG.debug("Subscribe to events for set: {}", resourceName);
        this.listeners.add(this.set.onAdd(this::onEvent).join());
        this.listeners.add(this.set.onRemove(this::onEvent).join());
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

    private void onEvent(DistributedSet.ValueEvent<Object> event) {
        Exchange exchange = getEndpoint().createExchange();
        exchange.getIn().setHeader(AtomixClientConstants.EVENT_TYPE, event.type());

        if (resultHeader == null) {
            exchange.getIn().setBody(event.value());
        } else {
            exchange.getIn().setHeader(resultHeader, event.value());
        }

        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            getExceptionHandler().handleException(e);
        }
    }
}
