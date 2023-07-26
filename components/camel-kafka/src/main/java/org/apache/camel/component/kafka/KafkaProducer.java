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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.kafka.producer.support.DelegatingCallback;
import org.apache.camel.component.kafka.producer.support.KafkaProducerCallBack;
import org.apache.camel.component.kafka.producer.support.KafkaProducerMetadataCallBack;
import org.apache.camel.component.kafka.producer.support.KeyValueHolderIterator;
import org.apache.camel.component.kafka.producer.support.ProducerUtil;
import org.apache.camel.component.kafka.serde.KafkaHeaderSerializer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.KeyValueHolder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.kafka.producer.support.ProducerUtil.tryConvertToSerializedType;

public class KafkaProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaProducer.class);

    @SuppressWarnings("rawtypes")
    private org.apache.kafka.clients.producer.Producer kafkaProducer;
    private final KafkaEndpoint endpoint;
    private final KafkaConfiguration configuration;
    private ExecutorService workerPool;
    private boolean shutdownWorkerPool;
    private volatile boolean closeKafkaProducer;
    private final String endpointTopic;
    private final Integer configPartitionKey;
    private final String configKey;

    public KafkaProducer(KafkaEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.configuration = endpoint.getConfiguration();

        endpointTopic = URISupport.extractRemainderPath(URI.create(endpoint.getEndpointUri()), true);
        configPartitionKey = configuration.getPartitionKey();
        configKey = configuration.getKey();
    }

    Properties getProps() {
        Properties props = configuration.createProducerProperties();
        endpoint.updateClassProperties(props);

        String brokers = endpoint.getKafkaClientFactory().getBrokers(configuration);
        if (brokers != null) {
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        }

        return props;
    }

    @SuppressWarnings("rawtypes")
    public org.apache.kafka.clients.producer.Producer getKafkaProducer() {
        return kafkaProducer;
    }

    /**
     * To use a custom {@link org.apache.kafka.clients.producer.KafkaProducer} instance.
     */
    @SuppressWarnings("rawtypes")
    public void setKafkaProducer(org.apache.kafka.clients.producer.Producer kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
    }

    public ExecutorService getWorkerPool() {
        return workerPool;
    }

    public void setWorkerPool(ExecutorService workerPool) {
        this.workerPool = workerPool;
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected void doStart() throws Exception {
        Properties props = getProps();
        if (kafkaProducer == null) {
            createProducer(props);
        }

        // if we are in asynchronous mode we need a worker pool
        if (!configuration.isSynchronous() && workerPool == null) {
            // If custom worker pool is provided, then use it, else create a new one.
            if (configuration.getWorkerPool() != null) {
                workerPool = configuration.getWorkerPool();
                shutdownWorkerPool = false;
            } else {
                workerPool = endpoint.createProducerExecutor();
                // we create a thread pool so we should also shut it down
                shutdownWorkerPool = true;
            }
        }
    }

    private void createProducer(Properties props) {
        ClassLoader threadClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            // Kafka uses reflection for loading authentication settings,
            // use its classloader
            Thread.currentThread()
                    .setContextClassLoader(org.apache.kafka.clients.producer.KafkaProducer.class.getClassLoader());
            LOG.trace("Creating KafkaProducer");
            kafkaProducer = endpoint.getKafkaClientFactory().getProducer(props);
            closeKafkaProducer = true;
        } finally {
            Thread.currentThread().setContextClassLoader(threadClassLoader);
        }
        LOG.debug("Created KafkaProducer: {}", kafkaProducer);
    }

    @Override
    protected void doStop() throws Exception {
        if (kafkaProducer != null && closeKafkaProducer) {
            LOG.debug("Closing KafkaProducer: {}", kafkaProducer);
            kafkaProducer.close();
            kafkaProducer = null;
        }

        if (shutdownWorkerPool && workerPool != null) {
            int timeout = configuration.getShutdownTimeout();
            LOG.debug("Shutting down Kafka producer worker threads with timeout {} millis", timeout);
            endpoint.getCamelContext().getExecutorServiceManager().shutdownGraceful(workerPool, timeout);
            workerPool = null;
        }
    }

    protected Iterator<KeyValueHolder<Object, ProducerRecord<Object, Object>>> createRecordIterable(
            Exchange exchange, Message message) {
        String topic = evaluateTopic(message);

        // extracting headers which need to be propagated
        List<Header> propagatedHeaders = getPropagatedHeaders(exchange, message);

        Object body = message.getBody();

        Iterator<Object> iterator = getObjectIterator(body);

        return new KeyValueHolderIterator(iterator, exchange, configuration, topic, propagatedHeaders);
    }

    protected ProducerRecord<Object, Object> createRecord(Exchange exchange, Message message) {
        String topic = evaluateTopic(message);

        Long timeStamp = null;
        Object overrideTimeStamp = message.removeHeader(KafkaConstants.OVERRIDE_TIMESTAMP);
        if (overrideTimeStamp != null) {
            timeStamp = exchange.getContext().getTypeConverter().convertTo(Long.class, exchange, overrideTimeStamp);
            LOG.debug("Using override TimeStamp: {}", overrideTimeStamp);
        }

        // extracting headers which need to be propagated
        List<Header> propagatedHeaders = getPropagatedHeaders(exchange, message);

        final Integer msgPartitionKey = getOverridePartitionKey(message);
        Object msgKey = getOverrideKey(message);

        if (msgKey != null) {
            msgKey = tryConvertToSerializedType(exchange, msgKey, configuration.getKeySerializer());
        }

        // must convert each entry of the iterator into the value according to
        // the serializer
        Object value = tryConvertToSerializedType(exchange, message.getBody(), configuration.getValueSerializer());

        return new ProducerRecord<>(topic, msgPartitionKey, timeStamp, msgKey, value, propagatedHeaders);
    }

    private Object getOverrideKey(Message message) {
        if (ObjectHelper.isEmpty(configKey)) {
            return message.getHeader(KafkaConstants.KEY);
        }

        return configKey;
    }

    private Integer getOverridePartitionKey(Message message) {
        if (ObjectHelper.isEmpty(configPartitionKey)) {
            return message.getHeader(KafkaConstants.PARTITION_KEY, Integer.class);
        }

        return configPartitionKey;
    }

    protected KeyValueHolder<Object, ProducerRecord<Object, Object>> createKeyValueHolder(Exchange exchange, Message message) {
        ProducerRecord<Object, Object> record = createRecord(exchange, message);

        return new KeyValueHolder<>(exchange, record);
    }

    private String evaluateTopic(Message message) {
        // must remove header so it's not propagated.
        Object overrideTopic = message.removeHeader(KafkaConstants.OVERRIDE_TOPIC);
        if (overrideTopic != null) {
            LOG.debug("Using override topic: {}", overrideTopic);
            return overrideTopic.toString();
        }

        String topic = configuration.getTopic();
        if (topic != null) {
            return topic;
        }

        return endpointTopic;
    }

    private boolean isIterable(Object body) {
        if (body instanceof Iterable || body instanceof Iterator) {
            return true;
        }

        return false;
    }

    private Iterator<Object> getObjectIterator(Object body) {
        Iterator<Object> iterator = null;
        if (body instanceof Iterable) {
            iterator = ((Iterable<Object>) body).iterator();
        } else if (body instanceof Iterator) {
            iterator = (Iterator<Object>) body;
        }
        return iterator;
    }

    private List<Header> getPropagatedHeaders(Exchange exchange, Message message) {
        Map<String, Object> messageHeaders = message.getHeaders();
        List<Header> propagatedHeaders = new ArrayList<>(messageHeaders.size());

        for (Map.Entry<String, Object> header : messageHeaders.entrySet()) {
            RecordHeader recordHeader = getRecordHeader(header, exchange);
            if (recordHeader != null) {
                propagatedHeaders.add(recordHeader);
            }
        }

        return propagatedHeaders;
    }

    private boolean shouldBeFiltered(String key, Object value, Exchange exchange, HeaderFilterStrategy headerFilterStrategy) {
        return !headerFilterStrategy.applyFilterToCamelHeaders(key, value, exchange);
    }

    private RecordHeader getRecordHeader(
            Map.Entry<String, Object> entry, Exchange exchange) {

        final HeaderFilterStrategy headerFilterStrategy = configuration.getHeaderFilterStrategy();

        final String key = entry.getKey();
        final Object value = entry.getValue();

        if (shouldBeFiltered(key, value, exchange, headerFilterStrategy)) {
            final KafkaHeaderSerializer headerSerializer = configuration.getHeaderSerializer();
            final byte[] headerValue = headerSerializer.serialize(key, value);

            if (headerValue == null) {
                return null;
            }
            return new RecordHeader(key, headerValue);
        }

        return null;
    }

    @Override
    // Camel calls this method if the endpoint isSynchronous(), as the
    // KafkaEndpoint creates a SynchronousDelegateProducer for it
    public void process(Exchange exchange) throws Exception {
        // is the message body a list or something that contains multiple values
        Message message = exchange.getIn();

        if (isIterable(message.getBody())) {
            processIterableSync(exchange, message);
        } else {
            processSingleMessageSync(exchange, message);
        }
    }

    private void processSingleMessageSync(Exchange exchange, Message message) throws InterruptedException, ExecutionException {
        final ProducerRecord<Object, Object> producerRecord = createRecord(exchange, message);

        final Future<RecordMetadata> future = kafkaProducer.send(producerRecord);

        postProcessMetadata(exchange, future);
    }

    private void processIterableSync(Exchange exchange, Message message) throws ExecutionException, InterruptedException {
        List<KeyValueHolder<Object, Future<RecordMetadata>>> futures = new ArrayList<>();

        Iterator<KeyValueHolder<Object, ProducerRecord<Object, Object>>> recordIterable
                = createRecordIterable(exchange, message);

        // This sets an empty metadata for the very first message on the batch
        List<RecordMetadata> recordMetadata = new ArrayList<>();
        if (configuration.isRecordMetadata()) {
            exchange.getMessage().setHeader(KafkaConstants.KAFKA_RECORDMETA, recordMetadata);
        }

        while (recordIterable.hasNext()) {
            KeyValueHolder<Object, ProducerRecord<Object, Object>> exchangeRecord = recordIterable.next();
            ProducerRecord<Object, Object> rec = exchangeRecord.getValue();

            if (LOG.isDebugEnabled()) {
                LOG.debug("Sending message to topic: {}, partition: {}, key: {}", rec.topic(), rec.partition(), rec.key());
            }

            futures.add(new KeyValueHolder<>(exchangeRecord.getKey(), kafkaProducer.send(rec)));
        }

        postProcessMetadata(futures, recordMetadata);
    }

    private void postProcessMetadata(
            List<KeyValueHolder<Object, Future<RecordMetadata>>> futures, List<RecordMetadata> metadataList)
            throws InterruptedException, ExecutionException {
        for (KeyValueHolder<Object, Future<RecordMetadata>> f : futures) {
            metadataList.addAll(postProcessMetadata(f.getKey(), f.getValue()));
        }
    }

    private List<RecordMetadata> postProcessMetadata(Object key, Future<RecordMetadata> f)
            throws InterruptedException, ExecutionException {
        // wait for them all to be sent
        RecordMetadata metadata = f.get();

        if (configuration.isRecordMetadata()) {
            List<RecordMetadata> metadataList = Collections.singletonList(metadata);

            ProducerUtil.setRecordMetadata(key, metadataList);

            return metadataList;
        }

        return Collections.EMPTY_LIST;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        final KafkaProducerCallBack producerCallBack
                = new KafkaProducerCallBack(exchange, callback, workerPool, configuration.isRecordMetadata());

        Message message = exchange.getMessage();
        Object body = message.getBody();

        try {
            // is the message body a list or something that contains multiple values
            if (isIterable(body)) {
                processIterableAsync(exchange, producerCallBack, message);
            } else {
                final ProducerRecord<Object, Object> record = createRecord(exchange, message);

                doSend(exchange, record, producerCallBack);
            }

            return producerCallBack.allSent();
        } catch (Exception e) {
            exchange.setException(e);
        }

        callback.done(true);
        return true;
    }

    private void processIterableAsync(Exchange exchange, KafkaProducerCallBack producerCallBack, Message message) {
        final Iterator<KeyValueHolder<Object, ProducerRecord<Object, Object>>> c = createRecordIterable(exchange, message);

        while (c.hasNext()) {
            doSend(c, producerCallBack);
        }
    }

    private void doSend(Iterator<KeyValueHolder<Object, ProducerRecord<Object, Object>>> kvIterator, KafkaProducerCallBack cb) {
        doSend(kvIterator.next(), cb);
    }

    private void doSend(KeyValueHolder<Object, ProducerRecord<Object, Object>> exchangeRecord, KafkaProducerCallBack cb) {
        doSend(exchangeRecord.getKey(), exchangeRecord.getValue(), cb);
    }

    private void doSend(Object key, ProducerRecord<Object, Object> record, KafkaProducerCallBack cb) {
        cb.increment();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Sending message to topic: {}, partition: {}, key: {}", record.topic(), record.partition(),
                    record.key());
        }

        if (key != null) {
            KafkaProducerMetadataCallBack metadataCallBack = new KafkaProducerMetadataCallBack(
                    key, configuration.isRecordMetadata());

            DelegatingCallback delegatingCallback = new DelegatingCallback(cb, metadataCallBack);

            kafkaProducer.send(record, delegatingCallback);
        } else {
            kafkaProducer.send(record, cb);
        }
    }
}
