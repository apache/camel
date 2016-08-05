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
package org.apache.camel.component.chronicle.engine;

import java.util.ArrayList;
import java.util.List;

import net.openhft.chronicle.engine.api.map.MapEvent;
import net.openhft.chronicle.engine.api.pubsub.InvalidSubscriberException;
import net.openhft.chronicle.engine.api.pubsub.Subscriber;
import net.openhft.chronicle.engine.api.pubsub.TopicSubscriber;
import net.openhft.chronicle.engine.api.tree.AssetTree;
import net.openhft.chronicle.engine.tree.TopologicalEvent;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultConsumer;

/**
 * The Chronicle Engine consumer.
 */
public class ChronicleEngineConsumer extends DefaultConsumer {
    private final String path;
    private AssetTree client;

    public ChronicleEngineConsumer(ChronicleEngineEndpoint endpoint, Processor processor) {
        super(endpoint, processor);

        this.path = endpoint.getPath();
    }

    @Override
    protected void doStart() throws Exception {
        if (client != null) {
            throw new IllegalStateException("AssetTree already configured");
        }

        ChronicleEngineEndpoint endpoint = (ChronicleEngineEndpoint)getEndpoint();
        ChronicleEngineConfiguration conf = endpoint.getConfiguration();
        client = endpoint.createRemoteAssetTree();

        if (conf.isSubscribeMapEvents()) {
            client.registerSubscriber(
                endpoint.getPath(),
                MapEvent.class,
                new EngineMapEventListener(conf.getFilteredMapEvents()));
        }

        if (conf.isSubscribeTopologicalEvents()) {
            client.registerSubscriber(
                endpoint.getPath(),
                TopologicalEvent.class,
                new EngineTopologicalEventListener());
        }

        if (conf.isSubscribeTopicEvents()) {
            client.registerTopicSubscriber(
                endpoint.getPath(),
                Object.class,
                Object.class,
                new EngineTopicEventListener());
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    // ****************************
    // MAP EVENT LISTENER
    // ****************************

    private class EngineMapEventListener implements Subscriber<MapEvent> {
        private List<Class<? extends MapEvent>> filteredEvents;

        EngineMapEventListener(String[] events) {
            this.filteredEvents = null;

            if (events != null && events.length > 0) {
                filteredEvents = new ArrayList<>(events.length);
                for (String event : events) {
                    filteredEvents.add(ChronicleEngineMapEventType.getType(event));
                }
            }
        }

        @Override
        public void onMessage(MapEvent event) throws InvalidSubscriberException {
            if (filteredEvents != null && filteredEvents.contains(event.getClass())) {
                return;
            }

            final Exchange exchange = getEndpoint().createExchange();
            final Message message = exchange.getIn();

            message.setHeader(ChronicleEngineConstants.PATH, path);
            message.setHeader(ChronicleEngineConstants.ASSET_NAME, event.assetName());
            message.setHeader(ChronicleEngineConstants.MAP_EVENT_TYPE, ChronicleEngineMapEventType.fromEvent(event));
            message.setHeader(ChronicleEngineConstants.KEY, event.getKey());
            message.setBody(event.getValue());

            if (event.oldValue() != null) {
                message.setHeader(ChronicleEngineConstants.OLD_VALUE, event.oldValue());
            }

            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        }
    }

    // ****************************
    // TOPOLOGICAL EVENT LISTENER
    // ****************************

    private class EngineTopologicalEventListener implements Subscriber<TopologicalEvent> {
        @Override
        public void onMessage(TopologicalEvent event) throws InvalidSubscriberException {
            final Exchange exchange = getEndpoint().createExchange();
            final Message message = exchange.getIn();

            message.setHeader(ChronicleEngineConstants.PATH, path);
            message.setHeader(ChronicleEngineConstants.ASSET_NAME, event.assetName());
            message.setHeader(ChronicleEngineConstants.TOPOLOGICAL_EVENT_NAME, event.name());
            message.setHeader(ChronicleEngineConstants.TOPOLOGICAL_EVENT_FULL_NAME, event.fullName());
            message.setHeader(ChronicleEngineConstants.TOPOLOGICAL_EVENT_ADDED, Boolean.toString(event.added()));

            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        }
    }

    // ****************************
    // TOPIC EVENT LISTENER
    // ****************************

    private class EngineTopicEventListener implements TopicSubscriber<Object, Object> {
        @Override
        public void onMessage(Object topic, Object dataMessage) throws InvalidSubscriberException {
            final Exchange exchange = getEndpoint().createExchange();
            final Message message = exchange.getIn();

            message.setHeader(ChronicleEngineConstants.PATH, path);
            message.setHeader(ChronicleEngineConstants.TOPIC, topic);
            message.setBody(dataMessage);

            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        }
    }
}
