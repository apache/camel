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
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.kafka.consumer.DefaultKafkaManualCommit;
import org.apache.camel.component.kafka.consumer.KafkaExactlyOnceMetadata;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.producer.support.DelegatingCallback;
import org.apache.camel.component.kafka.producer.support.KafkaProducerCallBack;
import org.apache.camel.component.kafka.producer.support.KafkaProducerMetadataCallBack;
import org.apache.camel.component.kafka.producer.support.KeyValueHolderIterator;
import org.apache.camel.component.kafka.producer.support.ProducerUtil;
import org.apache.camel.component.kafka.producer.support.PropagatedHeadersProvider;
import org.apache.camel.component.kafka.serde.KafkaHeaderSerializer;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.KeyValueHolder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReflectionHelper;
import org.apache.camel.util.URISupport;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.kafka.producer.support.ProducerUtil.tryConvertToSerializedType;

public class KafkaProducer extends DefaultAsyncProducer implements RouteIdAware {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaProducer.class);
    private static final String JACKSON3_JSON_NODE = "tools.jackson.databind.JsonNode";
    private static final String JACKSON3_ARRAY_NODE = "tools.jackson.databind.node.ArrayNode";

    @SuppressWarnings("rawtypes")
    private Producer kafkaProducer;
    private KafkaProducerHealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;
    private String clientId;
    private String transactionId;
    private final KafkaEndpoint endpoint;
    private final KafkaConfiguration configuration;
    private ExecutorService workerPool;
    private boolean shutdownWorkerPool;
    private volatile boolean closeKafkaProducer;
    // set when a fatal transactional error closed the shared producer, so the next transaction rebuilds it (CAMEL-24782)
    private volatile boolean producerClosedForRecreation;
    // A Kafka transactional producer supports only one active transaction. Do not allow
    // independent Camel exchanges to interleave exactly-once read-process-write transactions.
    private final AtomicReference<String> activeExactlyOnceExchange = new AtomicReference<>();
    private final String endpointTopic;
    private final Integer configPartitionKey;
    private final String configKey;
    private String routeId;

    public KafkaProducer(KafkaEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.configuration = endpoint.getConfiguration();

        endpointTopic = URISupport.extractRemainderPath(URI.create(endpoint.getEndpointUri()), true);
        configPartitionKey = configuration.getPartitionKey();
        configKey = configuration.getKey();
    }

    @Override
    public KafkaEndpoint getEndpoint() {
        return (KafkaEndpoint) super.getEndpoint();
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

    public boolean isReady() {
        return KafkaNetworkHealthHelper.producerHasReadyNodes(kafkaProducer);
    }

    @SuppressWarnings("rawtypes")
    public Producer getKafkaProducer() {
        return kafkaProducer;
    }

    /**
     * To use a custom {@link org.apache.kafka.clients.producer.KafkaProducer} instance.
     */
    @SuppressWarnings("rawtypes")
    public void setKafkaProducer(Producer kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
    }

    public ExecutorService getWorkerPool() {
        return workerPool;
    }

    public void setWorkerPool(ExecutorService workerPool) {
        this.workerPool = workerPool;
    }

    static String resolveExactlyOnceTransactionalId(String configuredTransactionId, String routeId) {
        return ObjectHelper.isEmpty(routeId) ? configuredTransactionId : configuredTransactionId + "-" + routeId;
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected void doStart() throws Exception {
        CamelContextAware.trySetCamelContext(configuration.getHeaderSerializer(), getEndpoint().getCamelContext());

        Properties props = getProps();
        String configuredTransactionId = ObjectHelper.isNotEmpty(configuration.getTransactionalId())
                ? configuration.getTransactionalId() : props.getProperty(ProducerConfig.TRANSACTIONAL_ID_CONFIG);
        if (configuration.isExactlyOnce() && ObjectHelper.isEmpty(configuredTransactionId)) {
            throw new IllegalArgumentException(
                    "exactlyOnce=true requires an explicit transactionalId unique to this producer instance; "
                                               + "transacted=true alone is not sufficient");
        }
        transactionId = configuredTransactionId;
        if (configuration.isExactlyOnce()) {
            // Each route has its own Camel Producer instance even when routes reuse the same Kafka endpoint.
            // Scope the configured base id by route so those producer instances do not fence each other.
            transactionId = resolveExactlyOnceTransactionalId(configuredTransactionId, routeId);
            props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, transactionId);
        }
        if (ObjectHelper.isEmpty(transactionId) && configuration.isTransacted()) {
            transactionId = getEndpoint().getId() + "-" + getRouteId();
            props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, transactionId);
        }
        if (kafkaProducer == null) {
            createProducer(props);
        }

        // init kafka transaction
        if (transactionId != null) {
            kafkaProducer.initTransactions();
        }

        // if we are in asynchronous mode we need a worker pool
        if (!configuration.isSynchronous() && workerPool == null) {
            // If custom worker pool is provided, then use it, else create a new one.
            if (configuration.getWorkerPool() != null) {
                workerPool = configuration.getWorkerPool();
                shutdownWorkerPool = false;
            } else {
                workerPool = endpoint.createProducerExecutor(this);
                // we create a thread pool so we should also shut it down
                shutdownWorkerPool = true;
            }
        }

        // init client id which we may need to get from the kafka producer via reflection
        if (clientId == null) {
            clientId = getProps().getProperty(CommonClientConfigs.CLIENT_ID_CONFIG);
            if (clientId == null) {
                try {
                    clientId = (String) ReflectionHelper
                            .getField(kafkaProducer.getClass().getDeclaredField("clientId"), kafkaProducer);
                } catch (Exception e) {
                    // ignore
                    clientId = "";
                }
            }
        }

        // health-check is optional so discover and resolve
        healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(
                endpoint.getCamelContext(),
                "producers",
                WritableHealthCheckRepository.class);

        if (healthCheckRepository != null) {
            producerHealthCheck = new KafkaProducerHealthCheck(this, clientId);
            producerHealthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckProducerEnabled());
            healthCheckRepository.addHealthCheck(producerHealthCheck);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (healthCheckRepository != null && producerHealthCheck != null) {
            healthCheckRepository.removeHealthCheck(producerHealthCheck);
            producerHealthCheck = null;
        }

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

    protected Iterator<KeyValueHolder<Object, ProducerRecord<Object, Object>>> createRecordIterable(
            Exchange exchange, Message message) {
        String topic = evaluateTopic(message);

        PropagatedHeadersProvider propagatedHeadersProvider
                = new PropagatedHeadersProvider(this, configuration, exchange, message);

        Object body = message.getBody();

        Iterator<Object> iterator = getObjectIterator(body);

        return new KeyValueHolderIterator(iterator, exchange, configuration, topic, propagatedHeadersProvider);
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
        String overrideTopicString = endpoint.getCamelContext().getTypeConverter().tryConvertTo(String.class, overrideTopic);
        if (overrideTopicString != null) {
            LOG.debug("Using override topic: {}", overrideTopicString);
            return overrideTopicString;
        }

        String topic = configuration.getTopic();
        if (topic != null) {
            return topic;
        }

        return endpointTopic;
    }

    private boolean isIterable(Object body) {
        // a JSON node is an Iterable of its elements, but only an array is split: an object node (whose elements
        // are its field values) and a scalar node are sent as one record
        if (body instanceof JsonNode node) {
            return node.isArray();
        }

        if (body instanceof Iterable) {
            return !isJackson3NonArrayNode(body.getClass());
        }

        return body instanceof Iterator;
    }

    /**
     * Whether the class is a Jackson 3 node other than an array node. Jackson 3 is recognized by class name, as
     * camel-kafka does not depend on it.
     */
    private static boolean isJackson3NonArrayNode(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            String name = c.getName();
            if (JACKSON3_ARRAY_NODE.equals(name)) {
                return false;
            }
            if (JACKSON3_JSON_NODE.equals(name)) {
                return true;
            }
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

    public List<Header> getPropagatedHeaders(Exchange exchange, Message message) {
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
        try {
            validateExactlyOnceDestination(exchange);
            // is the message body a list or something that contains multiple values
            Message message = exchange.getIn();

            if (transactionId != null) {
                startKafkaTransaction(exchange);
            }

            if (endpoint.getConfiguration().isUseIterator() && isIterable(message.getBody())) {
                processIterableSync(exchange, message);
            } else {
                processSingleMessageSync(exchange, message);
            }
        } catch (Exception e) {
            KafkaExactlyOnceMetadata.recordSendFailure(exchange, e);
            throw e;
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
            exchange.getMessage().setHeader(KafkaConstants.KAFKA_RECORD_META, recordMetadata);
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

        return Collections.emptyList();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        final KafkaProducerCallBack producerCallBack
                = new KafkaProducerCallBack(exchange, callback, workerPool, configuration.isRecordMetadata());

        Message message = exchange.getMessage();

        try {
            Object body = message.getBody();

            validateExactlyOnceDestination(exchange);
            // Start the transaction inside the try so that a failure to begin it (or a failing lazy body
            // conversion) still completes the async callback instead of escaping process() (CAMEL-24780).
            if (transactionId != null) {
                startKafkaTransaction(exchange);
            }

            // is the message body a list or something that contains multiple values
            if (endpoint.getConfiguration().isUseIterator() && isIterable(body)) {
                processIterableAsync(exchange, producerCallBack, message);
            } else {
                final ProducerRecord<Object, Object> record = createRecord(exchange, message);
                // Single message: the parent KafkaProducerCallBack already records the metadata and any
                // exception on this exchange, so pass a null key to skip the redundant per-record metadata
                // callback (avoids two short-lived allocations per message) (CAMEL-24779).
                doSend(null, record, producerCallBack);
            }

            return producerCallBack.allSent();
        } catch (Exception e) {
            KafkaExactlyOnceMetadata.recordSendFailure(exchange, e);
            exchange.setException(e);
            // Do not continue routing immediately. In the batch/iterator path, records already dispatched before the
            // failure have in-flight Kafka callbacks, and completing the exchange now would let those late callbacks
            // mutate a continued - and, with exchange pooling, possibly recycled - exchange. Arm completion instead:
            // allSent() releases the initial hold and, when sends are still in flight, defers done() to the last
            // callback; when nothing is in flight (the common non-batch failure) it completes here (CAMEL-24783).
            return producerCallBack.allSent();
        }
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

        try {
            if (key != null) {
                KafkaProducerMetadataCallBack metadataCallBack = new KafkaProducerMetadataCallBack(
                        key, configuration.isRecordMetadata());

                // make sure to cb is last in the order here
                DelegatingCallback delegatingCallback = new DelegatingCallback(metadataCallBack, cb);

                kafkaProducer.send(record, delegatingCallback);
            } else {
                kafkaProducer.send(record, cb);
            }
        } catch (RuntimeException dispatchFailure) {
            // send() threw synchronously rather than reporting through the callback: a SerializationException (or other
            // non-API KafkaException), an IllegalStateException from a closed producer, or an InterruptException. In
            // those cases no Kafka callback fires for this record. (An ApiException - a max.block.ms/buffer-exhaustion
            // TimeoutException, RecordTooLargeException, etc. - is NOT thrown here: Kafka reports it through the
            // callback, which balances the count via onCompletion.) Undo the increment above to keep the completion
            // counter accurate; otherwise a mid-batch failure would leave it above zero and routing would never
            // continue. The exception propagates to process(), which records it and arms completion for the records
            // already in flight.
            //
            // Narrow known exposure, left as-is: with a transactional producer whose transaction is already in an
            // error state, transactionManager.maybeAddPartition() can throw *after* the record's callback has been
            // registered, so that callback still fires later (on abort). Decrementing here then undercounts by one and
            // routing can continue one callback early. This matches the pre-existing behaviour and only arises for an
            // already-failed transaction (CAMEL-24783).
            cb.decrement();
            throw dispatchFailure;
        }
    }

    private void startKafkaTransaction(Exchange exchange) {
        UnitOfWork uow = exchange.getUnitOfWork();

        if (!uow.isTransactedBy(transactionId)) {
            // Resolve and validate source metadata before opening the broker transaction. A failure here must not
            // leave an open transaction without a registered synchronization.
            KafkaExactlyOnceMetadata exactlyOnceMetadata
                    = configuration.isExactlyOnce() ? createExactlyOnceMetadata(exchange) : null;
            if (exactlyOnceMetadata != null) {
                String registeredProducer = exchange.getProperty(
                        KafkaConstants.EXACTLY_ONCE_PRODUCER_TRANSACTION_ID_PROPERTY, String.class);
                if (registeredProducer != null && !registeredProducer.equals(transactionId)) {
                    String reason = "Only one exactlyOnce Kafka producer is supported per exchange/unit of work";
                    exactlyOnceMetadata.rejectClaim(reason);
                    throw new KafkaExactlyOnceMetadata.ClaimRejectedException(reason);
                }
                if (!activeExactlyOnceExchange.compareAndSet(null, exchange.getExchangeId())) {
                    throw new KafkaExactlyOnceMetadata.ClaimRejectedException(
                            "A Kafka exactlyOnce transaction is already active for exchange "
                                                                              + activeExactlyOnceExchange.get()
                                                                              + "; concurrent exchanges cannot share this transactional "
                                                                              + "producer. Serialize processing before the destination producer.");
                }
            }

            boolean transactionStarted = false;
            boolean unitOfWorkMarked = false;
            boolean synchronizationRegistered = false;
            boolean metadataClaimed = false;
            try {
                if (exactlyOnceMetadata != null) {
                    // The metadata object is shared by shallow exchange copies. Bind it to the original source exchange
                    // and allow only one producer transaction to claim its offsets.
                    exactlyOnceMetadata.claim(exchange.getExchangeId());
                    metadataClaimed = true;
                }
                // A prior transaction may have hit a fatal error that closed the shared producer; rebuild it before
                // starting a new transaction so the route recovers instead of staying wedged on a dead producer
                // (CAMEL-24782).
                recreateProducerIfClosed();
                LOG.debug("Starting kafka transaction {} with exchange {}", transactionId, exchange.getExchangeId());
                // Begin the broker transaction first, then mark the unit of work and register the
                // synchronization. This way a failure in beginTransaction() does not leave the unit of work
                // flagged as transacted without a synchronization to commit or roll it back (CAMEL-24780).
                kafkaProducer.beginTransaction();
                transactionStarted = true;
                uow.beginTransactedBy(transactionId);
                unitOfWorkMarked = true;
                uow.addSynchronization(new KafkaTransactionSynchronization(
                        transactionId, kafkaProducer, this, exactlyOnceMetadata, exchange.getExchangeId()));
                synchronizationRegistered = true;
                if (exactlyOnceMetadata != null) {
                    // A marker from an earlier attempt that failed before synchronization registration does not
                    // belong to this transaction. Start the newly registered attempt with a clean send-failure state.
                    exactlyOnceMetadata.clearSendFailures();
                    exchange.removeProperty(KafkaExactlyOnceMetadata.SEND_FAILURE_PROPERTY);
                    exchange.removeProperty(KafkaExactlyOnceMetadata.SECONDARY_SEND_FAILURE_PROPERTY);
                    exchange.setProperty(KafkaConstants.EXACTLY_ONCE_PRODUCER_TRANSACTION_ID_PROPERTY, transactionId);
                }
            } catch (RuntimeException | Error startFailure) {
                // If no synchronization owns the transaction yet, clean up here. Once registered, onDone()
                // owns both transaction completion and release of the exactly-once producer slot.
                if (!synchronizationRegistered) {
                    if (metadataClaimed) {
                        exactlyOnceMetadata.releaseClaim();
                    }
                    if (unitOfWorkMarked) {
                        try {
                            uow.endTransactedBy(transactionId);
                        } catch (RuntimeException cleanupFailure) {
                            startFailure.addSuppressed(cleanupFailure);
                        }
                    }
                    if (transactionStarted) {
                        try {
                            kafkaProducer.abortTransaction();
                        } catch (Exception abortFailure) {
                            startFailure.addSuppressed(abortFailure);
                            try {
                                kafkaProducer.close();
                            } catch (Exception closeFailure) {
                                startFailure.addSuppressed(closeFailure);
                            } finally {
                                markProducerClosedForRecreation();
                            }
                        }
                    } else if (KafkaTransactionSynchronization.isFatal(startFailure)) {
                        // beginTransaction() can itself fence or otherwise fatally invalidate the producer.
                        try {
                            kafkaProducer.close();
                        } catch (Exception closeFailure) {
                            startFailure.addSuppressed(closeFailure);
                        } finally {
                            markProducerClosedForRecreation();
                        }
                    }
                    if (exactlyOnceMetadata != null) {
                        releaseExactlyOnceTransaction(exchange.getExchangeId());
                    }
                }
                throw startFailure;
            }
        } else {
            if (configuration.isExactlyOnce()) {
                Throwable previousSendFailure = exchange.getProperty(
                        KafkaExactlyOnceMetadata.SEND_FAILURE_PROPERTY, Throwable.class);
                if (previousSendFailure != null) {
                    // Do not restart the transaction here: Camel redelivers from the failed processor, so output
                    // records sent by earlier route steps in this transaction would be discarded without replay.
                    // Roll back the whole source exchange and let the Kafka consumer replay it in a new transaction.
                    // This synthetic rejection is not a second Kafka send failure. Preserve only the original
                    // failure so a successfully handled non-Kafka DLQ can still take the offset-only skip path.
                    exchange.setProperty(KafkaExactlyOnceMetadata.SUPPRESS_NEXT_SEND_FAILURE_PROPERTY, Boolean.TRUE);
                    exchange.setRollbackOnly(true);
                    throw new IllegalStateException(
                            "Cannot retry a Kafka exactlyOnce send within an active transaction; "
                                                    + "the transaction is marked for rollback and the source record "
                                                    + "must be replayed in a new transaction",
                            previousSendFailure);
                }
            }
            LOG.debug("Using existing kafka transaction {} with exchange {}.",
                    transactionId, exchange.getExchangeId());
        }
    }

    private void validateExactlyOnceDestination(Exchange exchange) {
        if (exchange.getFromEndpoint() instanceof KafkaEndpoint sourceEndpoint
                && sourceEndpoint.getConfiguration().isExactlyOnce()
                && (!configuration.isExactlyOnce() || transactionId == null)
                && !isSecondaryKafkaDeadLetterSend(exchange)) {
            IllegalStateException failure = new IllegalStateException(
                    "A Kafka exactlyOnce source requires every Kafka destination producer to set exactlyOnce=true "
                                                                      + "and configure an explicit transactionalId");
            KafkaTransactionSynchronization.recordSendFailure(exchange, failure);
            throw failure;
        }
    }

    /** A Kafka dead-letter producer is allowed only after the source producer has recorded a send failure. */
    private boolean isSecondaryKafkaDeadLetterSend(Exchange exchange) {
        KafkaExactlyOnceMetadata metadata = exchange.getProperty(
                KafkaExactlyOnceMetadata.EXCHANGE_PROPERTY, KafkaExactlyOnceMetadata.class);
        String sourceProducer = exchange.getProperty(
                KafkaConstants.EXACTLY_ONCE_PRODUCER_TRANSACTION_ID_PROPERTY, String.class);
        return metadata != null && metadata.getSendFailure() != null && sourceProducer != null
                && !Objects.equals(sourceProducer, transactionId);
    }

    private KafkaExactlyOnceMetadata createExactlyOnceMetadata(Exchange exchange) {
        KafkaExactlyOnceMetadata metadata
                = exchange.getProperty(KafkaExactlyOnceMetadata.EXCHANGE_PROPERTY, KafkaExactlyOnceMetadata.class);

        // The original source producer already owns the offsets and its transaction has failed. An exactly-once
        // Kafka DLQ producer may now commit its DLQ record in its own transaction; the source synchronization is
        // registered first and therefore commits offsets only after this later synchronization has completed.
        if (isSecondaryKafkaDeadLetterSend(exchange)) {
            return null;
        }

        if (!(exchange.getFromEndpoint() instanceof KafkaEndpoint sourceEndpoint)) {
            if (metadata != null) {
                metadata.rejectCopyClaim(
                        "exactlyOnce source metadata cannot be claimed from a non-Kafka route or copied exchange");
            }
            throw new KafkaExactlyOnceMetadata.ClaimRejectedException(
                    "exactlyOnce requires a Kafka consumer as the route source");
        }

        KafkaConfiguration source = sourceEndpoint.getConfiguration();
        if (!source.isExactlyOnce() || !source.isAllowManualCommit()
                || source.isAutoCommitEnable() || !source.isBreakOnFirstError()) {
            if (metadata != null) {
                metadata.rejectCopyClaim("exactlyOnce source endpoint configuration is incompatible with source metadata");
            }
            throw new KafkaExactlyOnceMetadata.ClaimRejectedException(
                    "exactlyOnce requires the Kafka source endpoint to set exactlyOnce=true, "
                                                                      + "allowManualCommit=true, autoCommitEnable=false and breakOnFirstError=true");
        }

        if (metadata == null) {
            KafkaManualCommit manualCommit
                    = exchange.getMessage().getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class);
            if (manualCommit == null) {
                throw new IllegalStateException(
                        "exactlyOnce requires source offset metadata; configure allowManualCommit=true "
                                                + "on the Kafka source endpoint");
            }
            String sourceExchangeId = null;
            if (manualCommit instanceof DefaultKafkaManualCommit defaultCommit
                    && defaultCommit.getCamelExchangePayload() != null
                    && defaultCommit.getCamelExchangePayload().exchange != null) {
                sourceExchangeId = defaultCommit.getCamelExchangePayload().exchange.getExchangeId();
            }
            metadata = KafkaExactlyOnceMetadata.fromManualCommits(
                    Collections.singletonList(manualCommit), sourceExchangeId);
            exchange.setProperty(KafkaExactlyOnceMetadata.EXCHANGE_PROPERTY, metadata);
        }

        if (ObjectHelper.isNotEmpty(source.getGroupId())
                && !Objects.equals(source.getGroupId(), metadata.getConsumerGroupMetadata().groupId())) {
            String reason = "exactlyOnce source groupId does not match the consumer group metadata captured for the record";
            metadata.rejectClaim(reason);
            throw new IllegalStateException(reason);
        }
        return metadata;
    }

    /**
     * Rebuilds the shared producer when a previous transaction hit a fatal error and closed it. Lazy (done here on the
     * next transactional send rather than from the Kafka callback thread) and guarded so concurrent callers rebuild it
     * once. Re-initialises transactions so the new producer is ready to use (CAMEL-24782).
     */
    synchronized void recreateProducerIfClosed() {
        if (producerClosedForRecreation) {
            LOG.warn("Recreating kafka producer for transaction {} after a fatal error closed the previous one",
                    transactionId);
            Properties props = getProps();
            // the transactional id may have been generated from the endpoint/route id in doStart and is not in the
            // configuration, so set it explicitly so the rebuilt producer matches the one that was closed
            if (transactionId != null) {
                props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, transactionId);
            }
            createProducer(props);
            kafkaProducer.initTransactions();
            producerClosedForRecreation = false;
        }
    }

    /**
     * Signals that the shared producer was closed because a transaction hit a fatal error, so it must be rebuilt before
     * the next transactional send. Called by {@link KafkaTransactionSynchronization} (CAMEL-24782).
     */
    void markProducerClosedForRecreation() {
        producerClosedForRecreation = true;
    }

    void releaseExactlyOnceTransaction(String exchangeId) {
        String activeExchange = activeExactlyOnceExchange.get();
        if (!Objects.equals(activeExchange, exchangeId)
                || !activeExactlyOnceExchange.compareAndSet(activeExchange, null)) {
            LOG.warn("Could not release exactly-once producer slot for exchange {}; active exchange is {}",
                    exchangeId, activeExactlyOnceExchange.get());
        }
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }
}
