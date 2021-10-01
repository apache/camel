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
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.kafka.producer.support.DelegatingCallback;
import org.apache.camel.component.kafka.producer.support.KafkaProducerCallBack;
import org.apache.camel.component.kafka.producer.support.KeyValueHolderIterator;
import org.apache.camel.component.kafka.serde.KafkaHeaderSerializer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.KeyValueHolder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.kafka.clients.producer.Callback;
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
    private org.apache.kafka.clients.producer.KafkaProducer kafkaProducer;
    private final KafkaEndpoint endpoint;
    private ExecutorService workerPool;
    private boolean shutdownWorkerPool;
    private volatile boolean closeKafkaProducer;

    public KafkaProducer(KafkaEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    Properties getProps() {
        Properties props = endpoint.getConfiguration().createProducerProperties();
        endpoint.updateClassProperties(props);

        String brokers = endpoint.getKafkaClientFactory().getBrokers(endpoint.getConfiguration());
        if (brokers != null) {
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        }

        return props;
    }

    @SuppressWarnings("rawtypes")
    public org.apache.kafka.clients.producer.KafkaProducer getKafkaProducer() {
        return kafkaProducer;
    }

    /**
     * To use a custom {@link org.apache.kafka.clients.producer.KafkaProducer} instance.
     */
    @SuppressWarnings("rawtypes")
    public void setKafkaProducer(org.apache.kafka.clients.producer.KafkaProducer kafkaProducer) {
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
        if (!endpoint.getConfiguration().isSynchronous() && workerPool == null) {
            workerPool = endpoint.createProducerExecutor();
            // we create a thread pool so we should also shut it down
            shutdownWorkerPool = true;
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
            int timeout = endpoint.getConfiguration().getShutdownTimeout();
            LOG.debug("Shutting down Kafka producer worker threads with timeout {} millis", timeout);
            endpoint.getCamelContext().getExecutorServiceManager().shutdownGraceful(workerPool, timeout);
            workerPool = null;
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected Iterator<KeyValueHolder<Object, ProducerRecord>> createRecorder(Exchange exchange) throws Exception {
        String topic = endpoint.getConfiguration().getTopic();
        Long timeStamp = null;

        // must remove header so its not propagated
        Object overrideTopic = exchange.getIn().removeHeader(KafkaConstants.OVERRIDE_TOPIC);
        if (overrideTopic != null) {
            LOG.debug("Using override topic: {}", overrideTopic);
            topic = overrideTopic.toString();
        }

        if (topic == null) {
            // if topic property was not received from configuration or header
            // parameters take it from the remaining URI
            topic = URISupport.extractRemainderPath(new URI(endpoint.getEndpointUri()), true);
        }

        Object overrideTimeStamp = exchange.getIn().removeHeader(KafkaConstants.OVERRIDE_TIMESTAMP);
        if (overrideTimeStamp instanceof Long) {
            LOG.debug("Using override TimeStamp: {}", overrideTimeStamp);
            timeStamp = (Long) overrideTimeStamp;
        }

        // extracting headers which need to be propagated
        List<Header> propagatedHeaders = getPropagatedHeaders(exchange, endpoint.getConfiguration());

        Object msg = exchange.getIn().getBody();

        // is the message body a list or something that contains multiple values
        Iterator<Object> iterator = null;
        if (msg instanceof Iterable) {
            iterator = ((Iterable<Object>) msg).iterator();
        } else if (msg instanceof Iterator) {
            iterator = (Iterator<Object>) msg;
        }
        if (iterator != null) {
            final Iterator<Object> msgList = iterator;
            final String msgTopic = topic;

            return new KeyValueHolderIterator(msgList, exchange, endpoint.getConfiguration(), msgTopic, propagatedHeaders);
        }

        // endpoint take precedence over header configuration
        final Integer partitionKey = ObjectHelper.supplyIfEmpty(endpoint.getConfiguration().getPartitionKey(),
                () -> exchange.getIn().getHeader(KafkaConstants.PARTITION_KEY, Integer.class));

        // endpoint take precedence over header configuration
        Object key = ObjectHelper.supplyIfEmpty(endpoint.getConfiguration().getKey(),
                () -> exchange.getIn().getHeader(KafkaConstants.KEY));

        if (key != null) {
            key = tryConvertToSerializedType(exchange, key, endpoint.getConfiguration().getKeySerializer());
        }

        // must convert each entry of the iterator into the value according to
        // the serializer
        Object value = tryConvertToSerializedType(exchange, msg, endpoint.getConfiguration().getValueSerializer());

        ProducerRecord record = new ProducerRecord(topic, partitionKey, timeStamp, key, value, propagatedHeaders);
        return Collections.singletonList(new KeyValueHolder<Object, ProducerRecord>((Object) exchange, record)).iterator();
    }

    private List<Header> getPropagatedHeaders(Exchange exchange, KafkaConfiguration getConfiguration) {
        HeaderFilterStrategy headerFilterStrategy = getConfiguration.getHeaderFilterStrategy();
        KafkaHeaderSerializer headerSerializer = getConfiguration.getHeaderSerializer();
        return exchange.getIn().getHeaders().entrySet().stream()
                .filter(entry -> shouldBeFiltered(entry, exchange, headerFilterStrategy))
                .map(entry -> getRecordHeader(entry, headerSerializer)).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private boolean shouldBeFiltered(
            Map.Entry<String, Object> entry, Exchange exchange, HeaderFilterStrategy headerFilterStrategy) {
        return !headerFilterStrategy.applyFilterToCamelHeaders(entry.getKey(), entry.getValue(), exchange);
    }

    private RecordHeader getRecordHeader(Map.Entry<String, Object> entry, KafkaHeaderSerializer headerSerializer) {
        byte[] headerValue = headerSerializer.serialize(entry.getKey(), entry.getValue());
        if (headerValue == null) {
            return null;
        }
        return new RecordHeader(entry.getKey(), headerValue);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    // Camel calls this method if the endpoint isSynchronous(), as the
    // KafkaEndpoint creates a SynchronousDelegateProducer for it
    public void process(Exchange exchange) throws Exception {
        Iterator<KeyValueHolder<Object, ProducerRecord>> c = createRecorder(exchange);
        List<KeyValueHolder<Object, Future<RecordMetadata>>> futures = new LinkedList<>();
        List<RecordMetadata> recordMetadatas = new ArrayList<>();

        if (endpoint.getConfiguration().isRecordMetadata()) {
            exchange.getMessage().setHeader(KafkaConstants.KAFKA_RECORDMETA, recordMetadatas);
        }

        while (c.hasNext()) {
            KeyValueHolder<Object, ProducerRecord> exrec = c.next();
            ProducerRecord rec = exrec.getValue();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Sending message to topic: {}, partition: {}, key: {}", rec.topic(), rec.partition(), rec.key());
            }
            futures.add(new KeyValueHolder(exrec.getKey(), kafkaProducer.send(rec)));
        }
        for (KeyValueHolder<Object, Future<RecordMetadata>> f : futures) {
            // wait for them all to be sent
            List<RecordMetadata> metadata = Collections.singletonList(f.getValue().get());
            recordMetadatas.addAll(metadata);
            Exchange innerExchange = null;
            if (f.getKey() instanceof Exchange) {
                innerExchange = (Exchange) f.getKey();
                if (innerExchange != null) {
                    if (endpoint.getConfiguration().isRecordMetadata()) {
                        innerExchange.getMessage().setHeader(KafkaConstants.KAFKA_RECORDMETA, metadata);
                    }
                }
            }
            Message innerMessage = null;
            if (f.getKey() instanceof Message) {
                innerMessage = (Message) f.getKey();
                if (innerMessage != null) {
                    if (endpoint.getConfiguration().isRecordMetadata()) {
                        innerMessage.setHeader(KafkaConstants.KAFKA_RECORDMETA, metadata);
                    }
                }
            }
        }
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            Iterator<KeyValueHolder<Object, ProducerRecord>> c = createRecorder(exchange);
            KafkaProducerCallBack cb = new KafkaProducerCallBack(exchange, callback, workerPool, endpoint.getConfiguration());
            while (c.hasNext()) {
                cb.increment();
                KeyValueHolder<Object, ProducerRecord> exrec = c.next();
                ProducerRecord rec = exrec.getValue();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Sending message to topic: {}, partition: {}, key: {}", rec.topic(), rec.partition(), rec.key());
                }
                List<Callback> delegates = new ArrayList<>(Arrays.asList(cb));
                if (exrec.getKey() != null) {
                    delegates.add(new KafkaProducerCallBack(exrec.getKey(), workerPool, endpoint.getConfiguration()));
                }
                kafkaProducer.send(rec, new DelegatingCallback(delegates.toArray(new Callback[0])));
            }
            return cb.allSent();
        } catch (Exception ex) {
            exchange.setException(ex);
        }
        callback.done(true);
        return true;
    }
}
