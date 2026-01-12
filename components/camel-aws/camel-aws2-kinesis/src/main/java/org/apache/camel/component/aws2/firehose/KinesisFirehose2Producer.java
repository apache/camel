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
package org.apache.camel.component.aws2.firehose;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.model.CreateDeliveryStreamRequest;
import software.amazon.awssdk.services.firehose.model.CreateDeliveryStreamResponse;
import software.amazon.awssdk.services.firehose.model.DeleteDeliveryStreamRequest;
import software.amazon.awssdk.services.firehose.model.DescribeDeliveryStreamRequest;
import software.amazon.awssdk.services.firehose.model.DescribeDeliveryStreamResponse;
import software.amazon.awssdk.services.firehose.model.PutRecordBatchRequest;
import software.amazon.awssdk.services.firehose.model.PutRecordBatchResponse;
import software.amazon.awssdk.services.firehose.model.PutRecordRequest;
import software.amazon.awssdk.services.firehose.model.PutRecordResponse;
import software.amazon.awssdk.services.firehose.model.Record;
import software.amazon.awssdk.services.firehose.model.UpdateDestinationRequest;

public class KinesisFirehose2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KinesisFirehose2Producer.class);

    public KinesisFirehose2Producer(KinesisFirehose2Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public KinesisFirehose2Endpoint getEndpoint() {
        return (KinesisFirehose2Endpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        KinesisFirehose2Operations operation = determineOperation(exchange);
        if (ObjectHelper.isEmpty(operation)) {
            processSingleRecord(exchange);
        } else {
            switch (operation) {
                case sendBatchRecord:
                    sendBatchRecord(getClient(), exchange);
                    break;
                case createDeliveryStream:
                    createDeliveryStream(getClient(), exchange);
                    break;
                case deleteDeliveryStream:
                    deleteDeliveryStream(getClient(), exchange);
                    break;
                case updateDestination:
                    updateDestination(getClient(), exchange);
                    break;
                case describeDeliveryStream:
                    describeDeliveryStream(getClient(), exchange);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported operation");
            }
        }
    }

    private void createDeliveryStream(FirehoseClient client, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                CreateDeliveryStreamRequest.class,
                client::createDeliveryStream,
                null,
                "createDeliveryStream",
                (CreateDeliveryStreamResponse response, Message message) -> {
                    message.setHeader(KinesisFirehose2Constants.DELIVERY_STREAM_ARN, response.deliveryStreamARN());
                });
    }

    private void deleteDeliveryStream(FirehoseClient client, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                DeleteDeliveryStreamRequest.class,
                client::deleteDeliveryStream,
                () -> {
                    String streamName = getOptionalHeader(exchange, KinesisFirehose2Constants.KINESIS_FIREHOSE_STREAM_NAME,
                            String.class);
                    if (streamName == null) {
                        throw new IllegalArgumentException(
                                "The deleteDeliveryStream operation expects at least a delivery stream name header or a DeleteDeliveryStreamRequest instance");
                    }
                    return client.deleteDeliveryStream(
                            DeleteDeliveryStreamRequest.builder().deliveryStreamName(streamName).build());
                },
                "deleteDeliveryStream");
    }

    private void updateDestination(FirehoseClient client, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                UpdateDestinationRequest.class,
                client::updateDestination,
                null,
                "updateDestination");
    }

    private void describeDeliveryStream(FirehoseClient client, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                DescribeDeliveryStreamRequest.class,
                client::describeDeliveryStream,
                () -> {
                    String streamName = getOptionalHeader(exchange, KinesisFirehose2Constants.KINESIS_FIREHOSE_STREAM_NAME,
                            String.class);
                    if (streamName == null) {
                        throw new IllegalArgumentException(
                                "The describeDeliveryStream operation expects at least a delivery stream name header or a DescribeDeliveryStreamRequest instance");
                    }
                    return client.describeDeliveryStream(
                            DescribeDeliveryStreamRequest.builder().deliveryStreamName(streamName).build());
                },
                "describeDeliveryStream",
                (DescribeDeliveryStreamResponse response, Message message) -> {
                    if (response.deliveryStreamDescription() != null) {
                        message.setHeader(KinesisFirehose2Constants.DELIVERY_STREAM_ARN,
                                response.deliveryStreamDescription().deliveryStreamARN());
                        message.setHeader(KinesisFirehose2Constants.DELIVERY_STREAM_STATUS,
                                response.deliveryStreamDescription().deliveryStreamStatusAsString());
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private void sendBatchRecord(FirehoseClient client, Exchange exchange) {
        PutRecordBatchResponse result;
        if (exchange.getIn().getBody() instanceof Iterable) {
            Iterable<Record> c = exchange.getIn().getBody(Iterable.class);
            PutRecordBatchRequest.Builder batchRequest = PutRecordBatchRequest.builder();
            batchRequest.deliveryStreamName(getEndpoint().getConfiguration().getStreamName());
            batchRequest.records((Collection<Record>) c);
            result = client.putRecordBatch(batchRequest.build());
        } else {
            PutRecordBatchRequest req = exchange.getIn().getBody(PutRecordBatchRequest.class);
            result = client.putRecordBatch(req);
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
        message.setHeader(KinesisFirehose2Constants.FAILED_RECORD_COUNT, result.failedPutCount());
        message.setHeader(KinesisFirehose2Constants.ENCRYPTED, result.encrypted());
    }

    public void processSingleRecord(final Exchange exchange) {
        PutRecordRequest request = createRequest(exchange);
        LOG.trace("Sending request [{}] from exchange [{}]...", request, exchange);
        PutRecordResponse putRecordResult = getEndpoint().getClient().putRecord(request);
        LOG.trace("Received result [{}]", putRecordResult);
        Message message = getMessageForResponse(exchange);
        message.setHeader(KinesisFirehose2Constants.RECORD_ID, putRecordResult.recordId());
    }

    private PutRecordRequest createRequest(Exchange exchange) {
        ByteBuffer body = exchange.getIn().getBody(ByteBuffer.class);
        Record.Builder builder = Record.builder();
        builder.data(SdkBytes.fromByteBuffer(body));

        PutRecordRequest.Builder putRecordRequest = PutRecordRequest.builder();
        putRecordRequest.deliveryStreamName(getEndpoint().getConfiguration().getStreamName());
        putRecordRequest.record(builder.build());
        return putRecordRequest.build();
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    protected FirehoseClient getClient() {
        return getEndpoint().getClient();
    }

    protected KinesisFirehose2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    private KinesisFirehose2Operations determineOperation(Exchange exchange) {
        KinesisFirehose2Operations operation = exchange.getIn().getHeader(KinesisFirehose2Constants.KINESIS_FIREHOSE_OPERATION,
                KinesisFirehose2Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    /**
     * Executes a Firehose operation with POJO request support.
     */
    private <REQ, RES> void executeOperation(
            Exchange exchange,
            Class<REQ> requestClass,
            Function<REQ, RES> pojoExecutor,
            Supplier<RES> headerExecutor,
            String operationName)
            throws InvalidPayloadException {
        executeOperation(exchange, requestClass, pojoExecutor, headerExecutor, operationName, null);
    }

    /**
     * Executes a Firehose operation with POJO request support and optional response post-processing.
     */
    private <REQ, RES> void executeOperation(
            Exchange exchange,
            Class<REQ> requestClass,
            Function<REQ, RES> pojoExecutor,
            Supplier<RES> headerExecutor,
            String operationName,
            BiConsumer<RES, Message> responseProcessor)
            throws InvalidPayloadException {

        RES result;
        Object payload = exchange.getIn().getBody();
        if (requestClass.isInstance(payload)) {
            try {
                result = pojoExecutor.apply(requestClass.cast(payload));
            } catch (AwsServiceException ase) {
                LOG.trace("{} command returned the error code {}", operationName, ase.awsErrorDetails().errorCode());
                throw ase;
            }
        } else if (headerExecutor != null) {
            try {
                result = headerExecutor.get();
            } catch (AwsServiceException ase) {
                LOG.trace("{} command returned the error code {}", operationName, ase.awsErrorDetails().errorCode());
                throw ase;
            }
        } else {
            throw new IllegalArgumentException(
                    String.format("The %s operation expects a %s instance as body",
                            operationName, requestClass.getSimpleName()));
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
        if (responseProcessor != null) {
            responseProcessor.accept(result, message);
        }
    }

    /**
     * Gets an optional header value.
     */
    private <T> T getOptionalHeader(Exchange exchange, String headerName, Class<T> headerType) {
        return exchange.getIn().getHeader(headerName, headerType);
    }
}
