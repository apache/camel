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
import java.nio.ByteBuffer;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.kafka.serde.KafkaHeaderSerializer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.KeyValueHolder;
import org.apache.camel.util.URISupport;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.utils.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        String brokers = endpoint.getConfiguration().getBrokers();
        if (brokers == null) {
            throw new IllegalArgumentException("URL to the Kafka brokers must be configured with the brokers option.");
        }
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);

        return props;
    }

    @SuppressWarnings("rawtypes")
    public org.apache.kafka.clients.producer.KafkaProducer getKafkaProducer() {
        return kafkaProducer;
    }

    /**
     * To use a custom {@link org.apache.kafka.clients.producer.KafkaProducer}
     * instance.
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
            ClassLoader threadClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                // Kafka uses reflection for loading authentication settings,
                // use its classloader
                Thread.currentThread().setContextClassLoader(org.apache.kafka.clients.producer.KafkaProducer.class.getClassLoader());
                LOG.trace("Creating KafkaProducer");
                kafkaProducer = new org.apache.kafka.clients.producer.KafkaProducer(props);
                closeKafkaProducer = true;
            } finally {
                Thread.currentThread().setContextClassLoader(threadClassLoader);
            }
            LOG.debug("Created KafkaProducer: {}", kafkaProducer);
        }

        // if we are in asynchronous mode we need a worker pool
        if (!endpoint.isSynchronous() && workerPool == null) {
            workerPool = endpoint.createProducerExecutor();
            // we create a thread pool so we should also shut it down
            shutdownWorkerPool = true;
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (kafkaProducer != null && closeKafkaProducer) {
            LOG.debug("Closing KafkaProducer: {}", kafkaProducer);
            kafkaProducer.close();
            kafkaProducer = null;
        }

        if (shutdownWorkerPool && workerPool != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdown(workerPool);
            workerPool = null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Iterator<KeyValueHolder<Object, ProducerRecord>> createRecorder(Exchange exchange) throws Exception {
        String topic = endpoint.getConfiguration().getTopic();

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

        // extracting headers which need to be propagated
        List<Header> propagatedHeaders = getPropagatedHeaders(exchange, endpoint.getConfiguration());

        Object msg = exchange.getIn().getBody();

        // is the message body a list or something that contains multiple values
        Iterator<Object> iterator = null;
        if (msg instanceof Iterable) {
            iterator = ((Iterable<Object>)msg).iterator();
        } else if (msg instanceof Iterator) {
            iterator = (Iterator<Object>)msg;
        }
        if (iterator != null) {
            final Iterator<Object> msgList = iterator;
            final String msgTopic = topic;
            return new Iterator<KeyValueHolder<Object, ProducerRecord>>() {
                @Override
                public boolean hasNext() {
                    return msgList.hasNext();
                }

                @Override
                public KeyValueHolder<Object, ProducerRecord> next() {
                    // must convert each entry of the iterator into the value
                    // according to the serializer
                    Object next = msgList.next();
                    String innerTopic = msgTopic;
                    Object innerKey = null;
                    Integer innerPartitionKey = null;
                    boolean hasPartitionKey = false;
                    boolean hasMessageKey = false;

                    Object value = next;
                    Exchange ex = null;
                    Object body = next;

                    if (next instanceof Exchange || next instanceof Message) {
                        Exchange innerExchange = null;
                        Message innerMmessage = null;
                        if (next instanceof Exchange) {
                            innerExchange = (Exchange)next;
                            innerMmessage = innerExchange.getIn();
                        } else {
                            innerMmessage = (Message)next;
                        }

                        if (innerMmessage.getHeader(KafkaConstants.OVERRIDE_TOPIC) != null) {
                            innerTopic = (String)innerMmessage.removeHeader(KafkaConstants.OVERRIDE_TOPIC);
                        }

                        if (innerMmessage.getHeader(KafkaConstants.PARTITION_KEY) != null) {
                            innerPartitionKey = endpoint.getConfiguration().getPartitionKey() != null
                                ? endpoint.getConfiguration().getPartitionKey() : innerMmessage.getHeader(KafkaConstants.PARTITION_KEY, Integer.class);
                            hasPartitionKey = innerPartitionKey != null;
                        }

                        if (innerMmessage.getHeader(KafkaConstants.KEY) != null) {
                            innerKey = endpoint.getConfiguration().getKey() != null ? endpoint.getConfiguration().getKey() : innerMmessage.getHeader(KafkaConstants.KEY);

                            final Object messageKey = innerKey != null
                                ? tryConvertToSerializedType(innerExchange, innerKey, endpoint.getConfiguration().getKeySerializerClass()) : null;
                            hasMessageKey = messageKey != null;
                        }

                        ex = innerExchange == null ? exchange : innerExchange;
                        value = tryConvertToSerializedType(ex, innerMmessage.getBody(), endpoint.getConfiguration().getSerializerClass());

                    }

                    if (hasPartitionKey && hasMessageKey) {
                        return new KeyValueHolder(body, new ProducerRecord(innerTopic, innerPartitionKey, null, innerKey, value, propagatedHeaders));
                    } else if (hasMessageKey) {
                        return new KeyValueHolder(body, new ProducerRecord(innerTopic, null, null, innerKey, value, propagatedHeaders));
                    } else {
                        return new KeyValueHolder(body, new ProducerRecord(innerTopic, null, null, null, value, propagatedHeaders));
                    }
                }

                @Override
                public void remove() {
                    msgList.remove();
                }
            };
        }

        // endpoint take precedence over header configuration
        final Integer partitionKey = endpoint.getConfiguration().getPartitionKey() != null
            ? endpoint.getConfiguration().getPartitionKey() : exchange.getIn().getHeader(KafkaConstants.PARTITION_KEY, Integer.class);
        final boolean hasPartitionKey = partitionKey != null;

        // endpoint take precedence over header configuration
        Object key = endpoint.getConfiguration().getKey() != null ? endpoint.getConfiguration().getKey() : exchange.getIn().getHeader(KafkaConstants.KEY);
        final Object messageKey = key != null ? tryConvertToSerializedType(exchange, key, endpoint.getConfiguration().getKeySerializerClass()) : null;
        final boolean hasMessageKey = messageKey != null;

        // must convert each entry of the iterator into the value according to
        // the serializer
        Object value = tryConvertToSerializedType(exchange, msg, endpoint.getConfiguration().getSerializerClass());

        ProducerRecord record;
        if (hasPartitionKey && hasMessageKey) {
            record = new ProducerRecord(topic, partitionKey, null, key, value, propagatedHeaders);
        } else if (hasMessageKey) {
            record = new ProducerRecord(topic, null, null, key, value, propagatedHeaders);
        } else {
            record = new ProducerRecord(topic, null, null, null, value, propagatedHeaders);
        }
        return Collections.singletonList(new KeyValueHolder<Object, ProducerRecord>((Object)exchange, record)).iterator();
    }

    private List<Header> getPropagatedHeaders(Exchange exchange, KafkaConfiguration getConfiguration) {
        HeaderFilterStrategy headerFilterStrategy = getConfiguration.getHeaderFilterStrategy();
        KafkaHeaderSerializer headerSerializer = getConfiguration.getKafkaHeaderSerializer();
        return exchange.getIn().getHeaders().entrySet().stream().filter(entry -> shouldBeFiltered(entry, exchange, headerFilterStrategy))
            .map(entry -> getRecordHeader(entry, headerSerializer)).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private boolean shouldBeFiltered(Map.Entry<String, Object> entry, Exchange exchange, HeaderFilterStrategy headerFilterStrategy) {
        return !headerFilterStrategy.applyFilterToExternalHeaders(entry.getKey(), entry.getValue(), exchange);
    }

    private RecordHeader getRecordHeader(Map.Entry<String, Object> entry, KafkaHeaderSerializer headerSerializer) {
        byte[] headerValue = headerSerializer.serialize(entry.getKey(), entry.getValue());
        if (headerValue == null) {
            return null;
        }
        return new RecordHeader(entry.getKey(), headerValue);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    // Camel calls this method if the endpoint isSynchronous(), as the
    // KafkaEndpoint creates a SynchronousDelegateProducer for it
    public void process(Exchange exchange) throws Exception {
        Iterator<KeyValueHolder<Object, ProducerRecord>> c = createRecorder(exchange);
        List<KeyValueHolder<Object, Future<RecordMetadata>>> futures = new LinkedList<>();
        List<RecordMetadata> recordMetadatas = new ArrayList<>();

        if (endpoint.getConfiguration().isRecordMetadata()) {
            if (exchange.hasOut()) {
                exchange.getOut().setHeader(KafkaConstants.KAFKA_RECORDMETA, recordMetadatas);
            } else {
                exchange.getIn().setHeader(KafkaConstants.KAFKA_RECORDMETA, recordMetadatas);
            }
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
                innerExchange = (Exchange)f.getKey();
                if (innerExchange != null) {
                    if (endpoint.getConfiguration().isRecordMetadata()) {
                        if (innerExchange.hasOut()) {
                            innerExchange.getOut().setHeader(KafkaConstants.KAFKA_RECORDMETA, metadata);
                        } else {
                            innerExchange.getIn().setHeader(KafkaConstants.KAFKA_RECORDMETA, metadata);
                        }
                    }
                }
            }
            Message innerMessage = null;
            if (f.getKey() instanceof Message) {
                innerMessage = (Message)f.getKey();
                if (innerMessage != null) {
                    if (endpoint.getConfiguration().isRecordMetadata()) {
                        innerMessage.setHeader(KafkaConstants.KAFKA_RECORDMETA, metadata);
                    }
                }
            }
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            Iterator<KeyValueHolder<Object, ProducerRecord>> c = createRecorder(exchange);
            KafkaProducerCallBack cb = new KafkaProducerCallBack(exchange, callback);
            while (c.hasNext()) {
                cb.increment();
                KeyValueHolder<Object, ProducerRecord> exrec = c.next();
                ProducerRecord rec = exrec.getValue();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Sending message to topic: {}, partition: {}, key: {}", rec.topic(), rec.partition(), rec.key());
                }
                List<Callback> delegates = new ArrayList<>(Arrays.asList(cb));
                if (exrec.getKey() != null) {
                    delegates.add(new KafkaProducerCallBack(exrec.getKey()));
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

    /**
     * Attempts to convert the object to the same type as the serialized class
     * specified
     */
    protected Object tryConvertToSerializedType(Exchange exchange, Object object, String serializerClass) {
        Object answer = null;

        if (exchange == null) {
            return object;
        }

        if (KafkaConstants.KAFKA_DEFAULT_SERIALIZER.equals(serializerClass)) {
            answer = exchange.getContext().getTypeConverter().tryConvertTo(String.class, exchange, object);
        } else if ("org.apache.kafka.common.serialization.ByteArraySerializer".equals(serializerClass)) {
            answer = exchange.getContext().getTypeConverter().tryConvertTo(byte[].class, exchange, object);
        } else if ("org.apache.kafka.common.serialization.ByteBufferSerializer".equals(serializerClass)) {
            answer = exchange.getContext().getTypeConverter().tryConvertTo(ByteBuffer.class, exchange, object);
        } else if ("org.apache.kafka.common.serialization.BytesSerializer".equals(serializerClass)) {
            // we need to convert to byte array first
            byte[] array = exchange.getContext().getTypeConverter().tryConvertTo(byte[].class, exchange, object);
            if (array != null) {
                answer = new Bytes(array);
            }
        }

        return answer != null ? answer : object;
    }

    private final class DelegatingCallback implements Callback {

        private final List<Callback> callbacks;

        public DelegatingCallback(Callback... callbacks) {
            this.callbacks = Arrays.asList(callbacks);
        }

        @Override
        public void onCompletion(RecordMetadata metadata, Exception exception) {
            callbacks.forEach(c -> c.onCompletion(metadata, exception));
        }
    }

    private final class KafkaProducerCallBack implements Callback {

        private final Object body;
        private final AsyncCallback callback;
        private final AtomicInteger count = new AtomicInteger(1);
        private final List<RecordMetadata> recordMetadatas = new ArrayList<>();

        KafkaProducerCallBack(Object body, AsyncCallback callback) {
            this.body = body;
            this.callback = callback;
            if (endpoint.getConfiguration().isRecordMetadata()) {
                if (body instanceof Exchange) {
                    Exchange ex = (Exchange)body;
                    if (ex.hasOut()) {
                        ex.getOut().setHeader(KafkaConstants.KAFKA_RECORDMETA, recordMetadatas);
                    } else {
                        ex.getIn().setHeader(KafkaConstants.KAFKA_RECORDMETA, recordMetadatas);
                    }
                }
                if (body instanceof Message) {
                    Message msg = (Message)body;
                    msg.setHeader(KafkaConstants.KAFKA_RECORDMETA, recordMetadatas);
                }
            }
        }

        public KafkaProducerCallBack(Exchange exchange) {
            this(exchange, null);
        }

        public KafkaProducerCallBack(Message message) {
            this(message, null);
        }

        public KafkaProducerCallBack(Object body) {
            this(body, null);
        }

        void increment() {
            count.incrementAndGet();
        }

        boolean allSent() {
            if (count.decrementAndGet() == 0) {
                LOG.trace("All messages sent, continue routing.");
                // was able to get all the work done while queuing the requests
                if (callback != null) {
                    callback.done(true);
                }
                return true;
            }
            return false;
        }

        @Override
        public void onCompletion(RecordMetadata recordMetadata, Exception e) {
            if (e != null) {
                if (body instanceof Exchange) {
                    ((Exchange)body).setException(e);
                }
                if (body instanceof Message && ((Message)body).getExchange() != null) {
                    ((Message)body).getExchange().setException(e);
                }
            }

            recordMetadatas.add(recordMetadata);

            if (count.decrementAndGet() == 0) {
                // use worker pool to continue routing the exchange
                // as this thread is from Kafka Callback and should not be used
                // by Camel routing
                workerPool.submit(new Runnable() {
                    @Override
                    public void run() {
                        LOG.trace("All messages sent, continue routing.");
                        if (callback != null) {
                            callback.done(false);
                        }
                    }
                });
            }
        }
    }

}
