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
package org.apache.camel.component.salesforce.internal.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.ByteString;
import com.salesforce.eventbus.protobuf.ConsumerEvent;
import com.salesforce.eventbus.protobuf.FetchRequest;
import com.salesforce.eventbus.protobuf.FetchResponse;
import com.salesforce.eventbus.protobuf.ProducerEvent;
import com.salesforce.eventbus.protobuf.PubSubGrpc;
import com.salesforce.eventbus.protobuf.PublishRequest;
import com.salesforce.eventbus.protobuf.PublishResponse;
import com.salesforce.eventbus.protobuf.PublishResult;
import com.salesforce.eventbus.protobuf.ReplayPreset;
import com.salesforce.eventbus.protobuf.SchemaRequest;
import com.salesforce.eventbus.protobuf.TopicInfo;
import com.salesforce.eventbus.protobuf.TopicRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.IndexedRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.reflect.ReflectDatumReader;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.apache.camel.component.salesforce.PubSubApiConsumer;
import org.apache.camel.component.salesforce.SalesforceLoginConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.allegro.schema.json2avro.converter.JsonAvroConverter;

public class PubSubApiClient extends ServiceSupport {

    public static final String PUBSUB_ERROR_AUTH_ERROR = "sfdc.platform.eventbus.grpc.service.auth.error";
    private static final String PUBSUB_ERROR_AUTH_REFRESH_INVALID = "sfdc.platform.eventbus.grpc.service.auth.refresh.invalid";

    protected PubSubGrpc.PubSubStub asyncStub;
    protected PubSubGrpc.PubSubBlockingStub blockingStub;
    protected String accessToken;

    private final long backoffIncrement;
    private final long maxBackoff;
    private final String pubSubHost;
    private final int pubSubPort;

    private final Logger LOG = LoggerFactory.getLogger(getClass());
    private final SalesforceLoginConfig loginConfig;
    private final SalesforceSession session;

    private final Map<String, Schema> schemaCache = new ConcurrentHashMap<>();
    private final Map<String, String> schemaJsonCache = new ConcurrentHashMap<>();
    private final Map<String, TopicInfo> topicInfoCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PubSubApiConsumer, StreamObserver<FetchRequest>> observerMap = new ConcurrentHashMap<>();

    private ManagedChannel channel;
    private boolean usePlainTextConnection = false;

    public PubSubApiClient(SalesforceSession session, SalesforceLoginConfig loginConfig, String pubSubHost,
                           int pubSubPort, long backoffIncrement, long maxBackoff) {
        this.session = session;
        this.loginConfig = loginConfig;
        this.pubSubHost = pubSubHost;
        this.pubSubPort = pubSubPort;
        this.maxBackoff = maxBackoff;
        this.backoffIncrement = backoffIncrement;
    }

    public List<org.apache.camel.component.salesforce.api.dto.pubsub.PublishResult> publishMessage(
            String topic, List<?> bodies)
            throws IOException {
        LOG.debug("Preparing to publish on topic {}", topic);
        TopicInfo topicInfo = getTopicInfo(topic);
        String busTopicName = topicInfo.getTopicName();
        Schema schema = getSchema(topicInfo.getSchemaId());
        List<ProducerEvent> events = new ArrayList<>(bodies.size());
        for (Object body : bodies) {
            final ProducerEvent event = createProducerEvent(topicInfo.getSchemaId(), schema, body);
            events.add(event);
        }
        PublishRequest publishRequest = PublishRequest.newBuilder()
                .setTopicName(busTopicName)
                .addAllEvents(events)
                .build();
        PublishResponse response = blockingStub.publish(publishRequest);
        LOG.debug("Published on topic {}", topic);
        final List<PublishResult> results = response.getResultsList();
        List<org.apache.camel.component.salesforce.api.dto.pubsub.PublishResult> publishResults
                = new ArrayList<>(results.size());
        for (PublishResult rawResult : results) {
            if (rawResult.hasError()) {
                LOG.error("{} {} ", rawResult.getError().getCode(), rawResult.getError().getMsg());
            }
            publishResults.add(
                    new org.apache.camel.component.salesforce.api.dto.pubsub.PublishResult(rawResult));
        }
        return publishResults;
    }

    public void subscribe(PubSubApiConsumer consumer, ReplayPreset replayPreset, String initialReplayId) {
        LOG.error("Starting subscribe {}", consumer.getTopic());
        if (replayPreset == ReplayPreset.CUSTOM && initialReplayId == null) {
            throw new RuntimeException("initialReplayId is required for ReplayPreset.CUSTOM");
        }

        ByteString replayId = null;
        if (initialReplayId != null) {
            replayId = base64DecodeToByteString(initialReplayId);
        }
        String topic = consumer.getTopic();
        LOG.info("Subscribing to topic: {}.", topic);
        final FetchResponseObserver responseObserver = new FetchResponseObserver(consumer);
        StreamObserver<FetchRequest> serverStream = asyncStub.subscribe(responseObserver);
        LOG.info("Subscribe successful.");
        responseObserver.setServerStream(serverStream);
        observerMap.put(consumer, serverStream);
        FetchRequest.Builder fetchRequestBuilder = FetchRequest.newBuilder()
                .setReplayPreset(replayPreset)
                .setTopicName(topic)
                .setNumRequested(consumer.getBatchSize());
        if (replayPreset == ReplayPreset.CUSTOM) {
            fetchRequestBuilder.setReplayId(replayId);
        }
        serverStream.onNext(fetchRequestBuilder.build());
    }

    public TopicInfo getTopicInfo(String name) {
        return topicInfoCache.computeIfAbsent(name,
                topic -> blockingStub.getTopic(TopicRequest.newBuilder().setTopicName(topic).build()));
    }

    public String getSchemaJson(String schemaId) {
        return schemaJsonCache.computeIfAbsent(schemaId,
                s -> blockingStub.getSchema(SchemaRequest.newBuilder().setSchemaId(s).build()).getSchemaJson());
    }

    public Schema getSchema(String schemaId) {
        return schemaCache.computeIfAbsent(schemaId, id -> (new Schema.Parser()).parse(getSchemaJson(id)));
    }

    public static String base64EncodeByteString(ByteString bs) {
        var bb = bs.asReadOnlyByteBuffer();
        bb.position(0);
        byte[] bytes = new byte[bb.limit()];
        bb.get(bytes, 0, bytes.length);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static ByteString base64DecodeToByteString(String b64) {
        final byte[] decode = Base64.getDecoder().decode(b64);
        return ByteString.copyFrom(decode);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        final ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder
                .forAddress(pubSubHost, pubSubPort);
        if (usePlainTextConnection) {
            channelBuilder.usePlaintext();
        }
        channel = channelBuilder.build();
        TokenCredentials callCredentials = new TokenCredentials(session);
        this.asyncStub = PubSubGrpc.newStub(channel).withCallCredentials(callCredentials);
        this.blockingStub = PubSubGrpc.newBlockingStub(channel).withCallCredentials(callCredentials);

        // accessToken could be null
        accessToken = session.getAccessToken();
        if (accessToken == null && !loginConfig.isLazyLogin()) {
            try {
                accessToken = session.login(null);
            } catch (SalesforceException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        LOG.warn("Stopping PubSubApiClient");
        // stop each open stream
        observerMap.values().forEach(observer -> {
            LOG.debug("Stopping subscription");
            observer.onCompleted();
        });

        channel.shutdown();
        channel.awaitTermination(10, TimeUnit.SECONDS);
        super.doStop();
    }

    private ProducerEvent createProducerEvent(String schemaId, Schema schema, Object body) throws IOException {
        if (body instanceof ProducerEvent e) {
            return e;
        }
        byte[] bytes;
        if (body instanceof IndexedRecord indexedRecord) {
            if (body instanceof GenericRecord record) {
                bytes = getBytes(body, new GenericDatumWriter<>(record.getSchema()));
            } else if (body instanceof SpecificRecord) {
                bytes = getBytes(body, new SpecificDatumWriter<>());
            } else {
                throw new IllegalArgumentException(
                        "Body is of unexpected type: " + indexedRecord.getClass().getName());
            }
        } else if (body instanceof byte[] bodyBytes) {
            bytes = bodyBytes;
        } else if (body instanceof String json) {
            JsonAvroConverter converter = new JsonAvroConverter();
            bytes = converter.convertToAvro(json.getBytes(), schema);
        } else {
            // try serializing as POJO
            bytes = getBytes(body, new ReflectDatumWriter<>(schema));
        }
        return ProducerEvent.newBuilder()
                .setSchemaId(schemaId)
                .setPayload(ByteString.copyFrom(bytes))
                .build();
    }

    private byte[] getBytes(Object body, DatumWriter<Object> writer) throws IOException {
        byte[] bytes;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().directBinaryEncoder(buffer, null);
        writer.write(body, encoder);
        bytes = buffer.toByteArray();
        return bytes;
    }

    private class FetchResponseObserver implements StreamObserver<FetchResponse> {

        private final Logger LOG = LoggerFactory.getLogger(getClass());
        private final PubSubApiConsumer consumer;
        private final Map<String, Class<?>> eventClassMap;
        private final Class<?> pojoClass;
        private String replayId;
        private StreamObserver<FetchRequest> serverStream;

        public FetchResponseObserver(PubSubApiConsumer consumer) {
            this.consumer = consumer;
            this.eventClassMap = consumer.getEventClassMap();
            this.pojoClass = consumer.getPojoClass();
        }

        @Override
        public void onNext(FetchResponse fetchResponse) {
            String topic = consumer.getTopic();

            LOG.debug("Received {} events on topic: {}", fetchResponse.getEventsList().size(), topic);
            LOG.debug("rpcId: {}", fetchResponse.getRpcId());
            LOG.debug("pending_num_requested: {}", fetchResponse.getPendingNumRequested());
            for (ConsumerEvent ce : fetchResponse.getEventsList()) {
                try {
                    processEvent(ce);
                } catch (Exception e) {
                    LOG.error(e.toString(), e);
                }
            }
            replayId = base64EncodeByteString(fetchResponse.getLatestReplayId());
            int nextRequestSize = consumer.getBatchSize() - fetchResponse.getPendingNumRequested();
            // batchSize could be zero if this FetchResponse contained an empty batch, which is to be expected
            // for keep-alive reasons. In this case there is no need to send a FetchRequest
            if (nextRequestSize > 0) {
                FetchRequest fetchRequest = FetchRequest.newBuilder().setTopicName(topic)
                        .setNumRequested(nextRequestSize).build();
                LOG.debug("Sending FetchRequest, num_requested: {}", nextRequestSize);
                serverStream.onNext(fetchRequest);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            observerMap.remove(consumer);
            if (throwable instanceof StatusRuntimeException e) {
                LOG.error("GRPC Exception", e);
                Metadata trailers = e.getTrailers();
                String errorCode = "";
                LOG.error("Trailers:");
                if (trailers != null) {
                    trailers.keys().forEach(trailer -> LOG.error("Trailer: {}, Value: {}", trailer,
                            trailers.get(Metadata.Key.of(trailer, Metadata.ASCII_STRING_MARSHALLER))));
                    errorCode = trailers.get(Metadata.Key.of("error-code", Metadata.ASCII_STRING_MARSHALLER));
                }
                if (errorCode != null) {
                    switch (errorCode) {
                        case PUBSUB_ERROR_AUTH_ERROR, PUBSUB_ERROR_AUTH_REFRESH_INVALID -> {
                            LOG.error("attempting login");
                            session.attemptLoginUntilSuccessful(backoffIncrement, maxBackoff);
                            LOG.debug("logged in {}", consumer.getTopic());
                        }
                        default -> LOG.error("unexpected errorCode: {}", errorCode);
                    }
                }
            } else {
                LOG.error("An unexpected error occurred.", throwable);
            }
            LOG.debug("Attempting subscribe after error");
            if (replayId == null) {
                LOG.warn("Not re-subscribing after error because replayId is null. Topic: {}",
                        consumer.getTopic());
                return;
            }
            subscribe(consumer, ReplayPreset.CUSTOM, replayId);
        }

        @Override
        public void onCompleted() {
            LOG.debug("onCompleted() called by server");
            observerMap.remove(consumer);
        }

        public void setServerStream(StreamObserver<FetchRequest> serverStream) {
            this.serverStream = serverStream;
        }

        private void processEvent(ConsumerEvent ce) throws IOException {
            final Schema schema = getSchema(ce.getEvent().getSchemaId());
            Object record = switch (consumer.getDeserializeType()) {
                case AVRO -> deserializeAvro(ce, schema);
                case GENERIC_RECORD -> deserializeGenericRecord(ce, schema);
                case SPECIFIC_RECORD -> deserializeSpecificRecord(ce, schema);
                case POJO -> deserializePojo(ce, schema);
                case JSON -> deserializeJson(ce, schema);
            };
            String replayId = PubSubApiClient.base64EncodeByteString(ce.getReplayId());
            consumer.processEvent(record, replayId);
        }

        private Object deserializeAvro(ConsumerEvent ce, Schema schema) throws IOException {
            if (eventClassMap.containsKey(schema.getFullName())) {
                return deserializeSpecificRecord(ce, schema);
            } else {
                LOG.debug("No DTO found for schema: {}. Using GenericRecord.", schema.getFullName());
                return deserializeGenericRecord(ce, schema);
            }
        }

        private Object deserializeJson(ConsumerEvent ce, Schema schema) throws IOException {
            final GenericRecord record = deserializeGenericRecord(ce, schema);
            JsonAvroConverter converter = new JsonAvroConverter();
            final byte[] bytes = converter.convertToJson(record);
            return new String(bytes);
        }

        private Object deserializePojo(ConsumerEvent ce, Schema schema) throws IOException {
            ReflectDatumReader<?> reader = new ReflectDatumReader(pojoClass);
            reader.setSchema(schema);
            ByteArrayInputStream in = new ByteArrayInputStream(ce.getEvent().getPayload().toByteArray());
            BinaryDecoder decoder = DecoderFactory.get().directBinaryDecoder(in, null);
            return reader.read(null, decoder);
        }

        private GenericRecord deserializeGenericRecord(ConsumerEvent ce, Schema schema) throws IOException {
            DatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
            ByteArrayInputStream in = new ByteArrayInputStream(ce.getEvent().getPayload().toByteArray());
            BinaryDecoder decoder = DecoderFactory.get().directBinaryDecoder(in, null);
            return reader.read(null, decoder);
        }

        private Object deserializeSpecificRecord(ConsumerEvent ce, Schema schema) throws IOException {
            final Class<?> clas = eventClassMap.get(schema.getFullName());
            DatumReader<?> reader = new SpecificDatumReader<>(clas);
            ByteArrayInputStream in = new ByteArrayInputStream(ce.getEvent().getPayload().toByteArray());
            BinaryDecoder decoder = DecoderFactory.get().directBinaryDecoder(in, null);
            return reader.read(null, decoder);
        }
    }

    // ability to use Plain Text (http) for test contexts
    public void setUsePlainTextConnection(boolean usePlainTextConnection) {
        this.usePlainTextConnection = usePlainTextConnection;
    }
}
