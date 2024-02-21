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

import java.nio.ByteBuffer;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse;

public class Kinesis2Producer extends DefaultProducer {

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
    public void process(Exchange exchange) throws Exception {
        PutRecordRequest request = createRequest(exchange);
        PutRecordResponse putRecordResult = connection.getClient(getEndpoint()).putRecord(request);
        Message message = getMessageForResponse(exchange);
        message.setHeader(Kinesis2Constants.SEQUENCE_NUMBER, putRecordResult.sequenceNumber());
        message.setHeader(Kinesis2Constants.SHARD_ID, putRecordResult.shardId());
    }

    private PutRecordRequest createRequest(Exchange exchange) {
        ByteBuffer body = exchange.getIn().getBody(ByteBuffer.class);
        Object partitionKey = exchange.getIn().getHeader(Kinesis2Constants.PARTITION_KEY);
        Object sequenceNumber = exchange.getIn().getHeader(Kinesis2Constants.SEQUENCE_NUMBER);

        PutRecordRequest.Builder putRecordRequest = PutRecordRequest.builder();
        putRecordRequest.data(SdkBytes.fromByteBuffer(body));
        putRecordRequest.streamName(getEndpoint().getConfiguration().getStreamName());
        putRecordRequest.partitionKey(partitionKey.toString());
        if (sequenceNumber != null) {
            putRecordRequest.sequenceNumberForOrdering(sequenceNumber.toString());
        }
        return putRecordRequest.build();
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        ObjectHelper.notNull(connection, "connection", this);
    }
}
