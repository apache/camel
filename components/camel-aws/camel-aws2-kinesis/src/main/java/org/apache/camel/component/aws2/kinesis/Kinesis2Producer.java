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
import java.util.ArrayList;
import java.util.List;

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

public class Kinesis2Producer extends DefaultProducer {

    // Maximum number of records that can be sent in a single PutRecords request
    private static final int MAX_BATCH_SIZE = 500;

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
        if (body instanceof Iterable) {
            sendBatchRecords(exchange);
        } else {
            sendSingleRecord(exchange);
        }
    }

    private void sendBatchRecords(Exchange exchange) {
        Object partitionKey = exchange.getIn().getHeader(Kinesis2Constants.PARTITION_KEY);
        ensurePartitionKeyNotNull(partitionKey);
        List<List<PutRecordsRequestEntry>> requestBatchList = createRequestBatchList(exchange, partitionKey);
        int totalRecordCount = 0;
        int totalFailedCount = 0;
        for (List<PutRecordsRequestEntry> requestBatch : requestBatchList) {
            PutRecordsRequest putRecordsRequest = PutRecordsRequest.builder()
                    .streamName(getEndpoint().getConfiguration().getStreamName())
                    .records(requestBatch)
                    .build();
            PutRecordsResponse putRecordsResponse = connection.getClient(getEndpoint()).putRecords(putRecordsRequest);
            totalRecordCount += putRecordsResponse.records().size();
            totalFailedCount += putRecordsResponse.failedRecordCount();
            if (putRecordsResponse.failedRecordCount() > 0) {
                throw new RuntimeException(
                        "Failed to send records " + putRecordsResponse.failedRecordCount() + " of "
                                           + putRecordsResponse.records().size());
            }
        }
        Message message = exchange.getMessage();
        message.setHeader(Kinesis2Constants.RECORD_COUNT, totalRecordCount);
        message.setHeader(Kinesis2Constants.FAILED_RECORD_COUNT, totalFailedCount);
    }

    private List<List<PutRecordsRequestEntry>> createRequestBatchList(Exchange exchange, Object partitionKey) {
        List<List<PutRecordsRequestEntry>> requestBatchList = new ArrayList<>();
        List<PutRecordsRequestEntry> requestBatch = new ArrayList<>(MAX_BATCH_SIZE);
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

            PutRecordsRequestEntry putRecordsRequestEntry = PutRecordsRequestEntry.builder()
                    .data(sdkBytes)
                    .partitionKey(partitionKey.toString())
                    .build();
            requestBatch.add(putRecordsRequestEntry);
            if (requestBatch.size() == MAX_BATCH_SIZE) {
                requestBatchList.add(requestBatch);
                requestBatch = new ArrayList<>(MAX_BATCH_SIZE);
            }
        }
        if (!requestBatch.isEmpty()) {
            requestBatchList.add(requestBatch);
        }

        return requestBatchList;
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

    private void ensurePartitionKeyNotNull(Object partitionKey) {
        if (partitionKey == null) {
            throw new IllegalArgumentException("Partition key must be specified");
        }
    }
}
