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
package org.apache.camel.component.atomix.client.map;

import java.util.ArrayList;
import java.util.List;

import io.atomix.catalyst.concurrent.Listener;
import io.atomix.collections.DistributedMap;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.atomix.client.AbstractAtomixClientConsumer;
import org.apache.camel.component.atomix.client.AtomixClientConstants;

public class AtomixClientMapConsumer extends AbstractAtomixClientConsumer<AtomixClientMapEndpoint> {
    private final List<Listener<DistributedMap.EntryEvent<Object, Object>>> listeners;
    private DistributedMap<Object, Object> map;

    public AtomixClientMapConsumer(AtomixClientMapEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.listeners = new ArrayList<>();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.map = getAtomixEndpoint()
            .getAtomix()
            .getMap(
                getAtomixEndpoint().getMapName())
                //getAtomixEndpoint().getAtomixConfiguration().getConfig(),
                //getAtomixEndpoint().getAtomixConfiguration().getOptions())
            .join();

        this.listeners.add(this.map.onAdd(this::onEvent).join());
        this.listeners.add(this.map.onRemove(this::onEvent).join());
        this.listeners.add(this.map.onUpdate(this::onEvent).join());
    }

    @Override
    protected void doStop() throws Exception {
        // close listeners
        listeners.forEach(Listener::close);

        // close the map
        if (this.map == null) {
            this.map.close().join();
            this.map = null;
        }

        super.doStart();
    }

    // ********************************************
    // Event handler
    // ********************************************

    private void onEvent(DistributedMap.EntryEvent<Object, Object> event) {
        Exchange exchange = getEndpoint().createExchange();
        exchange.getIn().setHeader(AtomixClientConstants.EVENT_TYPE, event.type());
        exchange.getIn().setHeader(AtomixClientConstants.RESOURCE_KEY, event.entry().getKey());
        exchange.getIn().setBody(event.entry().getValue());

        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            getExceptionHandler().handleException(e);
        }
    }
}
