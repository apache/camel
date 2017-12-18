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
package org.apache.camel.component.kafka;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.URISupport;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.utils.Bytes;

public class KafkaProducer extends DefaultAsyncProducer {

    @SuppressWarnings("rawtypes")
    private org.apache.kafka.clients.producer.KafkaProducer kafkaProducer;
    private final KafkaEndpoint endpoint;
    private ExecutorService workerPool;
    private boolean shutdownWorkerPool;

    public KafkaProducer(KafkaEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    Properties getProps() {
        Properties props = endpoint.getConfiguration().createProducerProperties();
        endpoint.updateClassProperties(props);

        // brokers can be configured on endpoint or component level
        String brokers = endpoint.getConfiguration().getBrokers();
        if (brokers == null) {
            brokers = endpoint.getComponent().getBrokers();
        }
        if (brokers == null) {
            throw new IllegalArgumentException("URL to the Kafka brokers must be configured with the brokers option on either the component or endpoint.");
        }
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);

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
            ClassLoader threadClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                // Kafka uses reflection for loading authentication settings, use its classloader
                Thread.currentThread().setContextClassLoader(org.apache.kafka.clients.producer.KafkaProducer.class.getClassLoader());
                kafkaProducer = new org.apache.kafka.clients.producer.KafkaProducer(props);
            } finally {
                Thread.currentThread().setContextClassLoader(threadClassLoader);
            }
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
        if (kafkaProducer != null) {
            kafkaProducer.close();
        }

        if (shutdownWorkerPool && workerPool != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdown(workerPool);
            workerPool = null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Iterator<ProducerRecord> createRecorder(Exchange exchange) throws Exception {
        String topic = endpoint.getConfiguration().getTopic();

        if (!endpoint.getConfiguration().isBridgeEndpoint()) {
            String headerTopic = exchange.getIn().getHeader(KafkaConstants.TOPIC, String.class);
            boolean allowHeader = true;

            // when we do not bridge then detect if we try to send back to ourselves
            // which we most likely do not want to do
            if (headerTopic != null && endpoint.getConfiguration().isCircularTopicDetection()) {
                Endpoint from = exchange.getFromEndpoint();
                if (from instanceof KafkaEndpoint) {
                    String fromTopic = ((KafkaEndpoint) from).getConfiguration().getTopic();
                    allowHeader = !headerTopic.equals(fromTopic);
                    if (!allowHeader) {
                        log.debug("Circular topic detected from message header."
                            + " Cannot send to same topic as the message comes from: {}"
                            + ". Will use endpoint configured topic: {}", from, topic);
                    }
                }
            }
            if (allowHeader && headerTopic != null) {
                topic = headerTopic;
            }
        }

        if (topic == null) {
            // if topic property was not received from configuration or header parameters take it from the remaining URI
            topic = URISupport.extractRemainderPath(new URI(endpoint.getEndpointUri()), true);
        }

        // endpoint take precedence over header configuration
        final Integer partitionKey = endpoint.getConfiguration().getPartitionKey() != null
            ? endpoint.getConfiguration().getPartitionKey() : exchange.getIn().getHeader(KafkaConstants.PARTITION_KEY, Integer.class);
        final boolean hasPartitionKey = partitionKey != null;

        // endpoint take precedence over header configuration
        Object key = endpoint.getConfiguration().getKey() != null
            ? endpoint.getConfiguration().getKey() : exchange.getIn().getHeader(KafkaConstants.KEY);
        final Object messageKey = key != null
            ? tryConvertToSerializedType(exchange, key, endpoint.getConfiguration().getKeySerializerClass()) : null;
        final boolean hasMessageKey = messageKey != null;

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
            return new Iterator<ProducerRecord>() {
                @Override
                public boolean hasNext() {
                    return msgList.hasNext();
                }

                @Override
                public ProducerRecord next() {
                    // must convert each entry of the iterator into the value according to the serializer
                    Object next = msgList.next();
                    Object value = tryConvertToSerializedType(exchange, next, endpoint.getConfiguration().getSerializerClass());

                    if (hasPartitionKey && hasMessageKey) {
                        return new ProducerRecord(msgTopic, partitionKey, key, value);
                    } else if (hasMessageKey) {
                        return new ProducerRecord(msgTopic, key, value);
                    } else {
                        return new ProducerRecord(msgTopic, value);
                    }
                }

                @Override
                public void remove() {
                    msgList.remove();
                }
            };
        }

        // must convert each entry of the iterator into the value according to the serializer
        Object value = tryConvertToSerializedType(exchange, msg, endpoint.getConfiguration().getSerializerClass());

        ProducerRecord record;
        if (hasPartitionKey && hasMessageKey) {
            record = new ProducerRecord(topic, partitionKey, key, value);
        } else if (hasMessageKey) {
            record = new ProducerRecord(topic, key, value);
        } else {
            record = new ProducerRecord(topic, value);
        }
        return Collections.singletonList(record).iterator();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    // Camel calls this method if the endpoint isSynchronous(), as the KafkaEndpoint creates a SynchronousDelegateProducer for it
    public void process(Exchange exchange) throws Exception {
        Iterator<ProducerRecord> c = createRecorder(exchange);
        List<Future<RecordMetadata>> futures = new LinkedList<Future<RecordMetadata>>();
        List<RecordMetadata> recordMetadatas = new ArrayList<RecordMetadata>();

        if (endpoint.getConfiguration().isRecordMetadata()) {
            if (exchange.hasOut()) {
                exchange.getOut().setHeader(KafkaConstants.KAFKA_RECORDMETA, recordMetadatas);
            } else {
                exchange.getIn().setHeader(KafkaConstants.KAFKA_RECORDMETA, recordMetadatas);
            }
        }

        while (c.hasNext()) {
            ProducerRecord rec = c.next();
            if (log.isDebugEnabled()) {
                log.debug("Sending message to topic: {}, partition: {}, key: {}", rec.topic(), rec.partition(), rec.key());
            }
            futures.add(kafkaProducer.send(rec));
        }
        for (Future<RecordMetadata> f : futures) {
            //wait for them all to be sent
            recordMetadatas.add(f.get());
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            Iterator<ProducerRecord> c = createRecorder(exchange);
            KafkaProducerCallBack cb = new KafkaProducerCallBack(exchange, callback);
            while (c.hasNext()) {
                cb.increment();
                ProducerRecord rec = c.next();
                if (log.isDebugEnabled()) {
                    log.debug("Sending message to topic: {}, partition: {}, key: {}", rec.topic(), rec.partition(), rec.key());
                }
                kafkaProducer.send(rec, cb);
            }
            return cb.allSent();
        } catch (Exception ex) {
            exchange.setException(ex);
        }
        callback.done(true);
        return true;
    }

    /**
     * Attempts to convert the object to the same type as the serialized class specified
     */
    protected Object tryConvertToSerializedType(Exchange exchange, Object object, String serializerClass) {
        Object answer = null;

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

    private final class KafkaProducerCallBack implements Callback {

        private final Exchange exchange;
        private final AsyncCallback callback;
        private final AtomicInteger count = new AtomicInteger(1);
        private final List<RecordMetadata> recordMetadatas = new ArrayList<>();

        KafkaProducerCallBack(Exchange exchange, AsyncCallback callback) {
            this.exchange = exchange;
            this.callback = callback;
            if (endpoint.getConfiguration().isRecordMetadata()) {
                if (exchange.hasOut()) {
                    exchange.getOut().setHeader(KafkaConstants.KAFKA_RECORDMETA, recordMetadatas);
                } else {
                    exchange.getIn().setHeader(KafkaConstants.KAFKA_RECORDMETA, recordMetadatas);
                }
            }
        }

        void increment() {
            count.incrementAndGet();
        }

        boolean allSent() {
            if (count.decrementAndGet() == 0) {
                log.trace("All messages sent, continue routing.");
                //was able to get all the work done while queuing the requests
                callback.done(true);
                return true;
            }
            return false;
        }

        @Override
        public void onCompletion(RecordMetadata recordMetadata, Exception e) {
            if (e != null) {
                exchange.setException(e);
            }

            recordMetadatas.add(recordMetadata);

            if (count.decrementAndGet() == 0) {
                // use worker pool to continue routing the exchange
                // as this thread is from Kafka Callback and should not be used by Camel routing
                workerPool.submit(new Runnable() {
                    @Override
                    public void run() {
                        log.trace("All messages sent, continue routing.");
                        callback.done(false);
                    }
                });
            }
        }
    }

}
