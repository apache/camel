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
package org.apache.camel.component.atomix.client.map;

import java.util.ArrayList;
import java.util.List;

import io.atomix.catalyst.concurrent.Listener;
import io.atomix.collections.DistributedMap;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.atomix.client.AbstractAtomixClientConsumer;
import org.apache.camel.component.atomix.client.AtomixClientConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AtomixMapConsumer extends AbstractAtomixClientConsumer<AtomixMapEndpoint> {

    private static final Logger LOG = LoggerFactory.getLogger(AtomixMapConsumer.class);

    private final List<Listener<DistributedMap.EntryEvent<Object, Object>>> listeners;
    private final String resourceName;
    private final String resultHeader;
    private DistributedMap<Object, Object> map;

    public AtomixMapConsumer(AtomixMapEndpoint endpoint, Processor processor, String resourceName) {
        super(endpoint, processor);
        this.listeners = new ArrayList<>();
        this.resourceName = resourceName;
        this.resultHeader = endpoint.getConfiguration().getResultHeader();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.map = getAtomixEndpoint()
            .getAtomix()
            .getMap(
                resourceName,
                new DistributedMap.Config(getAtomixEndpoint().getConfiguration().getResourceOptions(resourceName)),
                new DistributedMap.Options(getAtomixEndpoint().getConfiguration().getResourceConfig(resourceName)))
            .join();


        Object key = getAtomixEndpoint().getConfiguration().getKey();
        if (key == null) {
            LOG.debug("Subscribe to events for map: {}", resourceName);
            this.listeners.add(this.map.onAdd(this::onEvent).join());
            this.listeners.add(this.map.onRemove(this::onEvent).join());
            this.listeners.add(this.map.onUpdate(this::onEvent).join());
        } else {
            LOG.debug("Subscribe to events for map: {}, key: {}", resourceName, key);
            this.listeners.add(this.map.onAdd(key, this::onEvent).join());
            this.listeners.add(this.map.onRemove(key, this::onEvent).join());
            this.listeners.add(this.map.onUpdate(key, this::onEvent).join());
        }
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

    private void onEvent(DistributedMap.EntryEvent<Object, Object> event) {
        Exchange exchange = getEndpoint().createExchange();
        exchange.getIn().setHeader(AtomixClientConstants.EVENT_TYPE, event.type());
        exchange.getIn().setHeader(AtomixClientConstants.RESOURCE_KEY, event.entry().getKey());

        if (resultHeader == null) {
            exchange.getIn().setBody(event.entry().getValue());
        } else {
            exchange.getIn().setHeader(resultHeader, event.entry().getValue());
        }

        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            getExceptionHandler().handleException(e);
        }
    }
}
