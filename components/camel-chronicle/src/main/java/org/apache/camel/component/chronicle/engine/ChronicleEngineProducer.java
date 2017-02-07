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

import java.util.Map;

import net.openhft.chronicle.engine.api.map.MapView;
import net.openhft.chronicle.engine.api.pubsub.Publisher;
import net.openhft.chronicle.engine.api.pubsub.TopicPublisher;
import net.openhft.chronicle.engine.api.tree.AssetTree;
import net.openhft.chronicle.engine.tree.QueueView;
import org.apache.camel.InvokeOnHeader;
import org.apache.camel.Message;
import org.apache.camel.impl.HeaderSelectorProducer;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.chronicle.engine.ChronicleEngineHelper.WeakRef;
import static org.apache.camel.component.chronicle.engine.ChronicleEngineHelper.mandatoryBody;
import static org.apache.camel.component.chronicle.engine.ChronicleEngineHelper.mandatoryKey;

public class ChronicleEngineProducer extends HeaderSelectorProducer {
    private final String uri;
    private WeakRef<TopicPublisher<Object, Object>> topicPublisher;
    private WeakRef<Publisher<Object>> publisher;
    private WeakRef<MapView<Object, Object>> mapView;
    private WeakRef<QueueView<Object, Object>> queueView;
    private AssetTree client;

    public ChronicleEngineProducer(ChronicleEngineEndpoint endpoint) {
        super(endpoint, ChronicleEngineConstants.ACTION, endpoint.getConfiguration().getAction());

        this.uri = endpoint.getUri();
        this.topicPublisher = WeakRef.create(() -> client.acquireTopicPublisher(uri, Object.class, Object.class));
        this.publisher = WeakRef.create(() -> client.acquirePublisher(uri, Object.class));
        this.mapView = WeakRef.create(() -> client.acquireMap(uri, Object.class, Object.class));
        this.queueView = WeakRef.create(() -> client.acquireQueue(uri, Object.class, Object.class, endpoint.getConfiguration().getClusterName()));
    }

    // ***************************
    // AssetTree
    // ***************************

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (client != null) {
            throw new IllegalStateException("AssetTree already configured");
        }

        client = ((ChronicleEngineEndpoint)getEndpoint()).createRemoteAssetTree();
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            client.close();
            client = null;
        }

        super.doStop();
    }

    // ***************************
    // Actions / Topic
    // ***************************

    @InvokeOnHeader(ChronicleEngineConstants.ACTION_PUBLISH)
    public void onPublish(Message message) {
        final Object key = message.getHeader(ChronicleEngineConstants.KEY);
        final Object val = mandatoryBody(message);

        if (key == null) {
            publisher.get().publish(val);
        } else {
            topicPublisher.get().publish(key, val);
        }
    }
    @InvokeOnHeader(ChronicleEngineConstants.ACTION_PUBLISH_AND_INDEX)
    public void onPublishAndIndex(Message message) {
        message.setHeader(
            ChronicleEngineConstants.QUEUE_INDEX,
            queueView.get().publishAndIndex(mandatoryKey(message), mandatoryBody(message))
        );
    }

    // ***************************
    // Actions / Map / Put
    // ***************************

    @InvokeOnHeader(ChronicleEngineConstants.ACTION_PUT)
    public void onPut(Message message) {
        message.setHeader(
            ChronicleEngineConstants.OLD_VALUE,
            mapView.get().put(mandatoryKey(message), mandatoryBody(message))
        );
    }

    @InvokeOnHeader(ChronicleEngineConstants.ACTION_GET_AND_PUT)
    public void onGetAndPut(Message message) {
        message.setBody(
            mapView.get().getAndPut(mandatoryKey(message), mandatoryBody(message))
        );
    }

    @InvokeOnHeader(ChronicleEngineConstants.ACTION_PUT_ALL)
    public void onPutAll(Message message) {
        mapView.get().putAll(
            ObjectHelper.notNull(message.getBody(Map.class), ChronicleEngineConstants.VALUE)
        );
    }

    @InvokeOnHeader(ChronicleEngineConstants.ACTION_PUT_IF_ABSENT)
    public void onPutIfAbsent(Message message) {
        message.setHeader(
            ChronicleEngineConstants.RESULT,
            mapView.get().putIfAbsent(mandatoryKey(message), mandatoryBody(message))
        );
    }

    // ***************************
    // Actions / Map / Get
    // ***************************

    @InvokeOnHeader(ChronicleEngineConstants.ACTION_GET)
    public void onGet(Message message) {
        final Long index = message.getHeader(ChronicleEngineConstants.QUEUE_INDEX, Long.class);

        if (index == null) {
            message.setBody(
                mapView.get().getOrDefault(
                    mandatoryKey(message),
                    message.getHeader(ChronicleEngineConstants.DEFAULT_VALUE))
            );
        } else {
            QueueView.Excerpt<Object, Object> excerpt = queueView.get().getExcerpt(index.longValue());

            message.setHeader(ChronicleEngineConstants.PATH, excerpt.topic());
            message.setBody(excerpt.message());
        }
    }

    @InvokeOnHeader(ChronicleEngineConstants.ACTION_GET_AND_REMOVE)
    public void onGetAndRemove(Message message) {
        message.setBody(
            mapView.get().getAndRemove(mandatoryKey(message))
        );
    }

    // ***************************
    // Actions / Map
    // ***************************

    @InvokeOnHeader(ChronicleEngineConstants.ACTION_REMOVE)
    public void onRemove(Message message) {
        Object oldValue = message.getHeader(ChronicleEngineConstants.OLD_VALUE);
        if (oldValue != null) {
            message.setHeader(
                ChronicleEngineConstants.RESULT,
                mapView.get().remove(mandatoryKey(message), oldValue)
            );
        } else {
            message.setHeader(
                ChronicleEngineConstants.OLD_VALUE,
                mapView.get().remove(mandatoryKey(message))
            );
        }
    }

    @InvokeOnHeader(ChronicleEngineConstants.ACTION_IS_EMPTY)
    public void onIsEmpty(Message message) {
        message.setHeader(
            ChronicleEngineConstants.RESULT,
            mapView.get().isEmpty()
        );
    }

    @InvokeOnHeader(ChronicleEngineConstants.ACTION_IS_SIZE)
    public void onSize(Message message) {
        message.setHeader(
            ChronicleEngineConstants.RESULT,
            mapView.get().size()
        );
    }

}
