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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.firehose.model.PutRecordRequest;
import software.amazon.awssdk.services.firehose.model.PutRecordResponse;
import software.amazon.awssdk.services.firehose.model.Record;

public class KinesisFirehose2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KinesisFirehose2Producer.class);

    public KinesisFirehose2Producer(KinesisFirehose2Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public KinesisFirehose2Endpoint getEndpoint() {
        return (KinesisFirehose2Endpoint)super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        PutRecordRequest request = createRequest(exchange);
        LOG.trace("Sending request [{}] from exchange [{}]...", request, exchange);
        PutRecordResponse putRecordResult = getEndpoint().getClient().putRecord(request);
        LOG.trace("Received result [{}]", putRecordResult);
        Message message = getMessageForResponse(exchange);
        message.setHeader(KinesisFirehose2Constants.RECORD_ID, putRecordResult.recordId());
    }

    private PutRecordRequest createRequest(Exchange exchange) {
        ByteBuffer body = exchange.getIn().getBody(ByteBuffer.class);
        Record.Builder record = Record.builder();
        record.data(SdkBytes.fromByteBuffer(body));

        PutRecordRequest.Builder putRecordRequest = PutRecordRequest.builder();
        putRecordRequest.deliveryStreamName(getEndpoint().getConfiguration().getStreamName());
        putRecordRequest.record(record.build());
        return putRecordRequest.build();
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }
}
