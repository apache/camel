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
package org.apache.camel.component.aws2.kinesis;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequestEntry;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResultEntry;

public class Kinesis2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(Kinesis2Producer.class);

    // Limits of a single PutRecords request: 500 records and 10 MiB, partition keys included
    private static final int MAX_BATCH_SIZE = 500;
    private static final long MAX_BATCH_BYTES = 10L * 1024 * 1024;
    // Largest record (data and partition key) that PutRecords accepts. A stream can have a lower limit (1 to 10 MiB).
    private static final long MAX_RECORD_BYTES = 10L * 1024 * 1024;
    // How many times the records that failed in a PutRecords request are sent again, and the delay before the first
    // time. The delay doubles at each retry.
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MILLIS = 100;

    private KinesisConnection connection;

    public Kinesis2Producer(Kinesis2Endpoint endpoint) {
        super(endpoint);
    }

    public KinesisConnection getConnection() {
        return connection;
    }

    public void setConnection(KinesisConnection connection) {
        this.connection = connection;
    }

    @Override
    public Kinesis2Endpoint getEndpoint() {
        return (Kinesis2Endpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        ObjectHelper.notNull(connection, "connection", this);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        // a Path is an Iterable of its name elements, but it is sent as one record with its content, as a File is
        if (body instanceof Iterable && !(body instanceof Path)) {
            sendBatchRecords(exchange);
        } else {
            sendSingleRecord(exchange);
        }
    }

    @SuppressWarnings("unchecked")
    private void sendBatchRecords(Exchange exchange) {
        List<String> partitionKeys = exchange.getIn().getHeader(Kinesis2Constants.PARTITION_KEYS, List.class);
        Object partitionKey = exchange.getIn().getHeader(Kinesis2Constants.PARTITION_KEY);
        if (partitionKeys == null) {
            ensurePartitionKeyNotNull(partitionKey);
        } else if (partitionKeys.isEmpty()) {
            throw new IllegalArgumentException("CamelAwsKinesisPartitionKeys header must not be empty");
        }
        // every record is converted and checked before the first request is sent
        List<List<PutRecordsRequestEntry>> requestBatchList
                = createRequestBatchList(exchange, partitionKey, partitionKeys);
        int totalRecordCount = 0;
        int totalFailedCount = 0;
        PutRecordsResultEntry firstFailure = null;
        // the next requests are sent even when records of an earlier one could not be written
        for (List<PutRecordsRequestEntry> requestBatch : requestBatchList) {
            List<PutRecordsResultEntry> failures = sendRequestBatch(requestBatch);
            totalRecordCount += requestBatch.size();
            totalFailedCount += failures.size();
            if (firstFailure == null && !failures.isEmpty()) {
                firstFailure = failures.get(0);
            }
        }
        Message message = exchange.getMessage();
        message.setHeader(Kinesis2Constants.RECORD_COUNT, totalRecordCount);
        message.setHeader(Kinesis2Constants.FAILED_RECORD_COUNT, totalFailedCount);
        if (firstFailure != null) {
            throw new RuntimeCamelException(
                    "Failed to send " + totalFailedCount + " of " + totalRecordCount + " records to the Kinesis stream "
                                            + getEndpoint().getConfiguration().getStreamName() + " after " + MAX_RETRIES
                                            + " retries, " + (totalRecordCount - totalFailedCount)
                                            + " records were written. First error: " + firstFailure.errorCode() + ": "
                                            + firstFailure.errorMessage());
        }
    }

    /**
     * Sends the records of one PutRecords request, then sends again the records that failed, at most
     * {@link #MAX_RETRIES} times. Returns the results of the records that are still not written.
     */
    private List<PutRecordsResultEntry> sendRequestBatch(List<PutRecordsRequestEntry> requestBatch) {
        String streamName = getEndpoint().getConfiguration().getStreamName();
        List<PutRecordsRequestEntry> pending = requestBatch;
        long delay = RETRY_DELAY_MILLIS;
        for (int retry = 0;; retry++) {
            PutRecordsRequest putRecordsRequest = PutRecordsRequest.builder()
                    .streamName(streamName)
                    .records(pending)
                    .build();
            PutRecordsResponse putRecordsResponse = connection.getClient(getEndpoint()).putRecords(putRecordsRequest);
            // the result of a record is at the same position as the record in the request,
            // and the result of a record that was not written has an error code
            List<PutRecordsResultEntry> results = putRecordsResponse.records();
            List<PutRecordsRequestEntry> failedEntries = new ArrayList<>();
            List<PutRecordsResultEntry> failures = new ArrayList<>();
            for (int i = 0; i < pending.size() && i < results.size(); i++) {
                if (results.get(i).errorCode() != null) {
                    failedEntries.add(pending.get(i));
                    failures.add(results.get(i));
                }
            }
            if (failures.isEmpty() || retry == MAX_RETRIES) {
                return failures;
            }
            LOG.debug("{} of {} records were not written to the Kinesis stream {} ({}), sending them again in {} ms",
                    failures.size(), pending.size(), streamName, failures.get(0).errorCode(), delay);
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeCamelException(
                        "Interrupted before sending again " + failures.size() + " records to the Kinesis stream " + streamName,
                        e);
            }
            delay *= 2;
            pending = failedEntries;
        }
    }

    private List<List<PutRecordsRequestEntry>> createRequestBatchList(
            Exchange exchange, Object partitionKey, List<String> partitionKeys) {
        List<List<PutRecordsRequestEntry>> requestBatchList = new ArrayList<>();
        List<PutRecordsRequestEntry> requestBatch = new ArrayList<>(MAX_BATCH_SIZE);
        long requestBatchBytes = 0;
        int index = 0;
        for (Object record : exchange.getIn().getBody(Iterable.class)) {
            SdkBytes sdkBytes;
            if (record instanceof byte[] bytes) {
                sdkBytes = SdkBytes.fromByteArray(bytes);
            } else if (record instanceof ByteBuffer bf) {
                sdkBytes = SdkBytes.fromByteBuffer(bf);
            } else if (record instanceof InputStream is) {
                sdkBytes = SdkBytes.fromInputStream(is);
            } else if (record instanceof String str) {
                sdkBytes = SdkBytes.fromUtf8String(str);
            } else {
                throw new IllegalArgumentException(
                        "Record type not supported. Must be byte[], ByteBuffer, InputStream or UTF-8 String");
            }

            String key;
            if (partitionKeys != null && index < partitionKeys.size()) {
                key = partitionKeys.get(index);
            } else {
                ensurePartitionKeyNotNull(partitionKey);
                key = partitionKey.toString();
            }

            long recordBytes = sdkBytes.asByteArrayUnsafe().length
                               + (key != null ? (long) key.getBytes(StandardCharsets.UTF_8).length : 0);
            if (recordBytes > MAX_RECORD_BYTES) {
                throw new IllegalArgumentException(
                        "The record at index " + index + " of the batch is " + recordBytes
                                                   + " bytes with its partition key, more than "
                                                   + MAX_RECORD_BYTES + " bytes, the largest record that Kinesis accepts");
            }
            if (!requestBatch.isEmpty()
                    && (requestBatch.size() == MAX_BATCH_SIZE || requestBatchBytes + recordBytes > MAX_BATCH_BYTES)) {
                requestBatchList.add(requestBatch);
                requestBatch = new ArrayList<>(MAX_BATCH_SIZE);
                requestBatchBytes = 0;
            }
            PutRecordsRequestEntry putRecordsRequestEntry = PutRecordsRequestEntry.builder()
                    .data(sdkBytes)
                    .partitionKey(key)
                    .build();
            requestBatch.add(putRecordsRequestEntry);
            requestBatchBytes += recordBytes;
            index++;
        }
        if (!requestBatch.isEmpty()) {
            requestBatchList.add(requestBatch);
        }

        return requestBatchList;
    }

    private void sendSingleRecord(Exchange exchange) throws InvalidPayloadException {
        PutRecordRequest request = createRequest(exchange);
        PutRecordResponse putRecordResult = connection.getClient(getEndpoint()).putRecord(request);
        Message message = exchange.getMessage();
        message.setHeader(Kinesis2Constants.SEQUENCE_NUMBER, putRecordResult.sequenceNumber());
        message.setHeader(Kinesis2Constants.SHARD_ID, putRecordResult.shardId());
    }

    private PutRecordRequest createRequest(Exchange exchange) throws InvalidPayloadException {
        byte[] body = exchange.getIn().getMandatoryBody(byte[].class);
        Object partitionKey = exchange.getIn().getHeader(Kinesis2Constants.PARTITION_KEY);
        Object sequenceNumber = exchange.getIn().getHeader(Kinesis2Constants.SEQUENCE_NUMBER);

        PutRecordRequest.Builder putRecordRequest = PutRecordRequest.builder();
        putRecordRequest.data(SdkBytes.fromByteArray(body));
        putRecordRequest.streamName(getEndpoint().getConfiguration().getStreamName());
        ensurePartitionKeyNotNull(partitionKey);
        putRecordRequest.partitionKey(partitionKey.toString());
        if (ObjectHelper.isNotEmpty(sequenceNumber)) {
            putRecordRequest.sequenceNumberForOrdering(sequenceNumber.toString());
        }
        return putRecordRequest.build();
    }

    private void ensurePartitionKeyNotNull(Object partitionKey) {
        if (ObjectHelper.isEmpty(partitionKey)) {
            throw new IllegalArgumentException("Partition key must be specified");
        }
    }
}
