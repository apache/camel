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

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelException;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.util.CastUtils;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaProducer extends DefaultAsyncProducer {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaProducer.class);
    
    private org.apache.kafka.clients.producer.KafkaProducer kafkaProducer;
    private final KafkaEndpoint endpoint;
    private ExecutorService workerPool;
    private boolean shutdownWorkerPool;

    public KafkaProducer(KafkaEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    <T> Class<T> loadClass(Object o, ClassResolver resolver, Class<T> type) {
        if (o == null || o instanceof Class) {
            return CastUtils.cast((Class<?>)o);
        }
        String name = o.toString();
        Class<T> c = resolver.resolveClass(name, type);
        if (c == null) {
            c = resolver.resolveClass(name, type, getClass().getClassLoader());
        }
        if (c == null) {
            c = resolver.resolveClass(name, type, org.apache.kafka.clients.producer.KafkaProducer.class.getClassLoader());
        }
        return c;
    }
    void replaceWithClass(Properties props, String key,  ClassResolver resolver, Class<?> type) {
        Class<?> c = loadClass(props.get(key), resolver, type);
        if (c != null) {
            props.put(key, c);
        }
    }
    
    Properties getProps() {
        Properties props = endpoint.getConfiguration().createProducerProperties();
        try {
            if (endpoint.getCamelContext() != null) {
                ClassResolver resolver = endpoint.getCamelContext().getClassResolver();
                replaceWithClass(props, "key.serializer", resolver, Serializer.class);
                replaceWithClass(props, "value.serializer", resolver, Serializer.class);
                
                try {
                    //doesn't exist in old version of Kafka client so detect and only call the method if
                    //the field/config actually exists
                    Field f = ProducerConfig.class.getDeclaredField("PARTITIONER_CLASS_CONFIG");
                    if (f != null) {
                        loadParitionerClass(resolver, props);
                    }
                } catch (NoSuchFieldException e) {
                    //ignore
                } catch (SecurityException e) {
                    //ignore
                }
            }
        } catch (Throwable t) {
            //can ignore and Kafka itself might be able to handle it, if not, it will throw an exception
            LOG.debug("Problem loading classes for Serializers", t);
        }
        if (endpoint.getBrokers() != null) {
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, endpoint.getBrokers());
        }
        return props;
    }

    private void loadParitionerClass(ClassResolver resolver, Properties props) {
        replaceWithClass(props, "partitioner.class", resolver, Partitioner.class);
    }


    public org.apache.kafka.clients.producer.KafkaProducer getKafkaProducer() {
        return kafkaProducer;
    }

    /**
     * To use a custom {@link org.apache.kafka.clients.producer.KafkaProducer} instance.
     */
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
    protected void doStart() throws Exception {
        Properties props = getProps();
        if (kafkaProducer == null) {
            kafkaProducer = new org.apache.kafka.clients.producer.KafkaProducer(props);
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

    @SuppressWarnings("unchecked")
    protected Iterator<ProducerRecord> createRecorder(Exchange exchange) throws CamelException {
        String topic = endpoint.getTopic();
        if (!endpoint.isBridgeEndpoint()) {
            topic = exchange.getIn().getHeader(KafkaConstants.TOPIC, topic, String.class);
        }
        if (topic == null) {
            throw new CamelExchangeException("No topic key set", exchange);
        }
        final Object partitionKey = exchange.getIn().getHeader(KafkaConstants.PARTITION_KEY);
        final boolean hasPartitionKey = partitionKey != null;

        final Object messageKey = exchange.getIn().getHeader(KafkaConstants.KEY);
        final boolean hasMessageKey = messageKey != null;

        Object msg = exchange.getIn().getBody();
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
                    if (hasPartitionKey && hasMessageKey) {
                        return new ProducerRecord(msgTopic, new Integer(partitionKey.toString()), messageKey, msgList.next());
                    } else if (hasMessageKey) {
                        return new ProducerRecord(msgTopic, messageKey, msgList.next());
                    }
                    return new ProducerRecord(msgTopic, msgList.next());
                }

                @Override
                public void remove() {
                    msgList.remove();
                }
            };
        }
        ProducerRecord record;
        if (hasPartitionKey && hasMessageKey) {
            record = new ProducerRecord(topic, new Integer(partitionKey.toString()), messageKey, msg);
        } else if (hasMessageKey) {
            record = new ProducerRecord(topic, messageKey, msg);
        } else {
            log.warn("No message key or partition key set");
            record = new ProducerRecord(topic, msg);
        }
        return Collections.singletonList(record).iterator();
    }

    @Override
    @SuppressWarnings("unchecked")
    // Camel calls this method if the endpoint isSynchronous(), as the KafkaEndpoint creates a SynchronousDelegateProducer for it
    public void process(Exchange exchange) throws Exception {
        Iterator<ProducerRecord> c = createRecorder(exchange);
        List<Future<ProducerRecord>> futures = new LinkedList<Future<ProducerRecord>>();
        while (c.hasNext()) {
            futures.add(kafkaProducer.send(c.next()));
        }
        for (Future<ProducerRecord> f : futures) {
            //wait for them all to be sent
            f.get();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            Iterator<ProducerRecord> c = createRecorder(exchange);
            KafkaProducerCallBack cb = new KafkaProducerCallBack(exchange, callback);
            while (c.hasNext()) {
                cb.increment();
                kafkaProducer.send(c.next(), cb);
            }
            return cb.allSent();
        } catch (Exception ex) {
            exchange.setException(ex);
        }
        callback.done(true);
        return true;
    }

    private final class KafkaProducerCallBack implements Callback {

        private final Exchange exchange;
        private final AsyncCallback callback;
        private final AtomicInteger count = new AtomicInteger(1);

        KafkaProducerCallBack(Exchange exchange, AsyncCallback callback) {
            this.exchange = exchange;
            this.callback = callback;
        }

        void increment() {
            count.incrementAndGet();
        }
        boolean allSent() {
            if (count.decrementAndGet() == 0) {
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
            if (count.decrementAndGet() == 0) {
                // use worker pool to continue routing the exchange
                // as this thread is from Kafka Callback and should not be used by Camel routing
                workerPool.submit(new Runnable() {
                    @Override
                    public void run() {
                        callback.done(false);
                    }
                });
            }
        }
    }

}
