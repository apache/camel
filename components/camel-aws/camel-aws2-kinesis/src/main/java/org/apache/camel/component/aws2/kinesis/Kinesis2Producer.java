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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequestEntry;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;

import java.util.ArrayList;
import java.util.List;

public class Kinesis2Producer extends DefaultProducer {

    // Maximum number of records that can be sent in a single PutRecords request
    private static final int MAX_BATCH_SIZE = 500;

    private KinesisConnection connection;

    private List<PutRecordsRequestEntry> requestBatchBuffer = new ArrayList<>(MAX_BATCH_SIZE);

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
    public void process(Exchange exchange) throws Exception {
        Boolean batchComplete = exchange.getProperty(Exchange.BATCH_COMPLETE, Boolean.class);
        if (batchComplete == null) {
            // if batchComplete is not set, then we are processing a single record
            flushRequestBatchBuffer();
            sendSingleRecord(exchange);
            return;
        }

        addToRequestBatchBuffer(createRequestEntry(exchange));
        if (batchComplete) {
            flushRequestBatchBuffer();
        }
    }

    private void sendSingleRecord(Exchange exchange) {
        PutRecordRequest request = createRequest(exchange);
        PutRecordResponse putRecordResult = connection.getClient(getEndpoint()).putRecord(request);
        Message message = exchange.getMessage();
        message.setHeader(Kinesis2Constants.SEQUENCE_NUMBER, putRecordResult.sequenceNumber());
        message.setHeader(Kinesis2Constants.SHARD_ID, putRecordResult.shardId());
    }

    private PutRecordRequest createRequest(Exchange exchange) {
        byte[] body = exchange.getIn().getBody(byte[].class);
        Object partitionKey = exchange.getIn().getHeader(Kinesis2Constants.PARTITION_KEY);
        Object sequenceNumber = exchange.getIn().getHeader(Kinesis2Constants.SEQUENCE_NUMBER);

        PutRecordRequest.Builder putRecordRequest = PutRecordRequest.builder();
        putRecordRequest.data(SdkBytes.fromByteArray(body));
        putRecordRequest.streamName(getEndpoint().getConfiguration().getStreamName());
        ensurePartitionKeyNotNull(partitionKey);
        putRecordRequest.partitionKey(partitionKey.toString());

        if (sequenceNumber != null) {
            putRecordRequest.sequenceNumberForOrdering(sequenceNumber.toString());
        }

        return putRecordRequest.build();
    }

    private PutRecordsRequestEntry createRequestEntry(Exchange exchange) {
        byte[] body = exchange.getIn().getBody(byte[].class);
        Object partitionKey = exchange.getIn().getHeader(Kinesis2Constants.PARTITION_KEY);

        PutRecordsRequestEntry.Builder putRecordsRequestEntry = PutRecordsRequestEntry.builder();
        putRecordsRequestEntry.data(SdkBytes.fromByteArray(body));
        ensurePartitionKeyNotNull(partitionKey);
        putRecordsRequestEntry.partitionKey(partitionKey.toString());
        return putRecordsRequestEntry.build();
    }

    private synchronized void addToRequestBatchBuffer(PutRecordsRequestEntry requestEntry) {
        this.requestBatchBuffer.add(requestEntry);
        if (this.requestBatchBuffer.size() >= MAX_BATCH_SIZE) {
            flushRequestBatchBuffer();
        }
    }

    private synchronized void flushRequestBatchBuffer() {
        if (this.requestBatchBuffer.isEmpty()) {
            return;
        }

        List<PutRecordsRequestEntry> requestBatchToSend = new ArrayList<>(this.requestBatchBuffer);
        this.requestBatchBuffer = new ArrayList<>(MAX_BATCH_SIZE);

        PutRecordsRequest putRecordsRequest = PutRecordsRequest.builder()
                .streamName(getEndpoint().getConfiguration().getStreamName())
                .records(requestBatchToSend)
                .build();

        PutRecordsResponse putRecordsResponse = connection.getClient(getEndpoint()).putRecords(putRecordsRequest);
        int failedRecordCount = putRecordsResponse.failedRecordCount();
        if (failedRecordCount > 0) {
            throw new RuntimeException(
                    "Failed to send records " + failedRecordCount + " of " + requestBatchToSend.size());
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        ObjectHelper.notNull(connection, "connection", this);
    }

    private void ensurePartitionKeyNotNull(Object partitionKey) {
        if (partitionKey == null) {
            throw new IllegalArgumentException("Partition key must be specified");
        }
    }
}
