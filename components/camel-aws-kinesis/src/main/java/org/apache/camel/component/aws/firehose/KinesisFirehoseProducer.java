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
package org.apache.camel.component.aws.firehose;

import java.nio.ByteBuffer;

import com.amazonaws.services.kinesisfirehose.model.PutRecordRequest;
import com.amazonaws.services.kinesisfirehose.model.PutRecordResult;
import com.amazonaws.services.kinesisfirehose.model.Record;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KinesisFirehoseProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KinesisFirehoseProducer.class);

    public KinesisFirehoseProducer(KinesisFirehoseEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public KinesisFirehoseEndpoint getEndpoint() {
        return (KinesisFirehoseEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        PutRecordRequest request = createRequest(exchange);
        LOG.trace("Sending request [{}] from exchange [{}]...", request, exchange);
        PutRecordResult putRecordResult = getEndpoint().getClient().putRecord(request);
        LOG.trace("Received result [{}]", putRecordResult);
        Message message = getMessageForResponse(exchange);
        message.setHeader(KinesisFirehoseConstants.RECORD_ID, putRecordResult.getRecordId());
    }

    private PutRecordRequest createRequest(Exchange exchange) {
        ByteBuffer body = exchange.getIn().getBody(ByteBuffer.class);
        Record record = new Record();
        record.setData(body);

        PutRecordRequest putRecordRequest = new PutRecordRequest();
        putRecordRequest.setDeliveryStreamName(getEndpoint().getConfiguration().getStreamName());
        putRecordRequest.setRecord(record);
        return putRecordRequest;
    }
    
    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }
}
