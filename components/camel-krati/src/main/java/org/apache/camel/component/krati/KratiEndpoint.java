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
package org.apache.camel.component.krati;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import krati.core.segment.ChannelSegmentFactory;
import krati.core.segment.SegmentFactory;
import krati.io.Serializer;
import krati.store.DataStore;
import krati.util.FnvHashFunction;
import krati.util.HashFunction;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.krati.serializer.KratiDefaultSerializer;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * The krati allows the use krati datastores and datasets inside Camel.
 */
@UriEndpoint(firstVersion = "2.9.0", scheme = "krati", title = "Krati", syntax = "krati:path", consumerClass = KratiConsumer.class, label = "database,nosql")
public class KratiEndpoint extends ScheduledPollEndpoint {

    protected static Map<String, KratiDataStoreRegistration> dataStoreRegistry = new HashMap<String, KratiDataStoreRegistration>();

    @UriPath @Metadata(required = "true")
    protected String path;
    @UriParam(label = "producer", enums = "CamelKratiPut,CamelKratiGet,CamelKratiDelete,CamelKratiDeleteAll")
    protected String operation;
    @UriParam(label = "producer")
    protected String key;
    @UriParam(label = "producer")
    protected String value;
    @UriParam(defaultValue = "100")
    protected int initialCapacity = 100;
    @UriParam(defaultValue = "64")
    protected int segmentFileSize = 64;
    @UriParam
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Serializer<Object> keySerializer = new KratiDefaultSerializer();
    @SuppressWarnings({"unchecked", "rawtypes"})
    @UriParam
    protected Serializer<Object> valueSerializer = new KratiDefaultSerializer();
    @UriParam
    protected SegmentFactory segmentFactory = new ChannelSegmentFactory();
    @UriParam
    protected HashFunction<byte[]> hashFunction = new FnvHashFunction();
    @UriParam(label = "consumer")
    protected int maxMessagesPerPoll;

    public KratiEndpoint(String uri, KratiComponent component) throws URISyntaxException {
        super(uri, component);
        this.path = getPath(uri);
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        KratiDataStoreRegistration registration = dataStoreRegistry.get(path);
        if (registration != null) {
            registration.unregister();
        }
    }

    public Producer createProducer() throws Exception {
        DataStore<Object, Object> dataStore = null;
        KratiDataStoreRegistration registration = dataStoreRegistry.get(path);
        if (registration != null) {
            dataStore = registration.getDataStore();
        }
        if (dataStore == null || !dataStore.isOpen()) {
            dataStore = KratiHelper.<Object, Object>createDataStore(path, initialCapacity, segmentFileSize, segmentFactory, hashFunction, keySerializer, valueSerializer);
            dataStoreRegistry.put(path, new KratiDataStoreRegistration(dataStore));
        }
        return new KratiProducer(this, dataStore);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        DataStore<Object, Object> dataStore = null;
        KratiDataStoreRegistration registration = dataStoreRegistry.get(path);
        if (registration != null) {
            dataStore = registration.getDataStore();
        }
        if (dataStore == null || !dataStore.isOpen()) {
            dataStore = KratiHelper.createDataStore(path, initialCapacity, segmentFileSize, segmentFactory, hashFunction, keySerializer, valueSerializer);
            dataStoreRegistry.put(path, new KratiDataStoreRegistration(dataStore));
        }
        KratiConsumer answer = new KratiConsumer(this, processor, dataStore);
        answer.setMaxMessagesPerPoll(getMaxMessagesPerPoll());
        configureConsumer(answer);
        return answer;
    }

    public boolean isSingleton() {
        return true;
    }

    /**
     * Returns the path from the URI.
     */
    protected String getPath(String uri) throws URISyntaxException {
        URI u = new URI(uri);
        StringBuilder pathBuilder = new StringBuilder();
        if (u.getHost() != null) {
            pathBuilder.append(u.getHost());
        }
        if (u.getPath() != null) {
            pathBuilder.append(u.getPath());
        }
        return pathBuilder.toString();
    }

    public String getKey() {
        return key;
    }

    /**
     * The key.
     */
    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    /**
     * The Value.
     */
    public void setValue(String value) {
        this.value = value;
    }

    public String getOperation() {
        return operation;
    }

    /**
     * Specifies the type of operation that will be performed to the datastore.
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public int getInitialCapacity() {
        return initialCapacity;
    }

    /**
     * The inital capcity of the store.
     */
    public void setInitialCapacity(int initialCapacity) {
        this.initialCapacity = initialCapacity;
    }

    public int getSegmentFileSize() {
        return segmentFileSize;
    }

    /**
     * Data store segments size in MB.
     */
    public void setSegmentFileSize(int segmentFileSize) {
        this.segmentFileSize = segmentFileSize;
    }

    public SegmentFactory getSegmentFactory() {
        return segmentFactory;
    }

    /**
     * Sets the segment factory of the target store.
     */
    public void setSegmentFactory(SegmentFactory segmentFactory) {
        this.segmentFactory = segmentFactory;
    }

    public HashFunction<byte[]> getHashFunction() {
        return hashFunction;
    }

    /**
     * The hash function to use.
     */
    public void setHashFunction(HashFunction<byte[]> hashFunction) {
        this.hashFunction = hashFunction;
    }

    /**
     * Path of the datastore is the relative path of the folder that krati will use for its datastore.
     */
    public String getPath() {
        return path;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    /**
     * The maximum number of messages which can be received in one poll. This can be used to avoid reading in too much data and taking up too much memory.
     */
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public Serializer<Object> getKeySerializer() {
        return keySerializer;
    }

    /**
     * The serializer that will be used to serialize the key.
     */
    public void setKeySerializer(Serializer<Object> keySerializer) {
        this.keySerializer = keySerializer;
    }

    public Serializer<Object> getValueSerializer() {
        return valueSerializer;
    }

    /**
     * The serializer that will be used to serialize the value.
     */
    public void setValueSerializer(Serializer<Object> valueSerializer) {
        this.valueSerializer = valueSerializer;
    }
}
