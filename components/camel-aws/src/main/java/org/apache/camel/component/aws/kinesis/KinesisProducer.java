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
package org.apache.camel.component.aws.kinesis;

import com.amazonaws.services.kinesis.model.PutRecordRequest;
import com.amazonaws.services.kinesis.model.PutRecordResult;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.impl.DefaultProducer;

import java.nio.ByteBuffer;

import static org.apache.camel.component.aws.common.AwsExchangeUtil.getMessageForResponse;

public class KinesisProducer extends DefaultProducer {
    public KinesisProducer(KinesisEndpoint endpoint) throws NoFactoryAvailableException {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        PutRecordRequest request = createRequest(exchange);
        PutRecordResult putRecordResult = getEndpoint().getClient().putRecord(request);
        Message message = getMessageForResponse(exchange);
        message.setHeader(KinesisConstants.SEQUENCE_NUMBER,putRecordResult.getSequenceNumber());
        message.setHeader(KinesisConstants.SHARD_ID, putRecordResult.getShardId());
    }

    @Override
    public KinesisEndpoint getEndpoint() {
        return (KinesisEndpoint) super.getEndpoint();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("KinesisProducer{");
        sb.append(super.getEndpoint().getEndpointUri());
        sb.append('}');
        return sb.toString();
    }

    private PutRecordRequest createRequest(Exchange exchange) {
        ByteBuffer body = exchange.getIn().getBody(ByteBuffer.class);
        Object partitionKey = exchange.getIn().getHeader(KinesisConstants.PARTITION_KEY);
        Object sequenceNumber = exchange.getIn().getHeader(KinesisConstants.SEQUENCE_NUMBER);
        PutRecordRequest putRecordRequest = new PutRecordRequest();
        putRecordRequest.setData(body);
        putRecordRequest.setStreamName(getEndpoint().getStreamName());
        if(sequenceNumber != null) {
            putRecordRequest.setSequenceNumberForOrdering(sequenceNumber.toString());
        }
        if(partitionKey != null) {
            putRecordRequest.setPartitionKey(partitionKey.toString());
        }
        return putRecordRequest;
    }
}
