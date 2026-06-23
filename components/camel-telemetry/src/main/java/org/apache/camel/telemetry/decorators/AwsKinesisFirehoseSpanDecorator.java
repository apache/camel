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
package org.apache.camel.telemetry.decorators;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.telemetry.Span;

public class AwsKinesisFirehoseSpanDecorator extends AbstractMessagingSpanDecorator {

    static final String FIREHOSE_OPERATION = "operation";
    static final String FIREHOSE_STREAM_NAME = "deliveryStreamName";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.firehose.KinesisFirehose2Constants}
     */
    static final String RECORD_ID = "CamelAwsKinesisFirehoseRecordId";
    static final String OPERATION = "CamelAwsKinesisFirehoseOperation";
    static final String STREAM_NAME = "CamelAwsKinesisFirehoseDeliveryStreamName";

    @Override
    public String getComponent() {
        return "aws2-kinesis-firehose";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.firehose.KinesisFirehose2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(FIREHOSE_OPERATION, operation);
        }

        String streamName = exchange.getIn().getHeader(STREAM_NAME, String.class);
        if (streamName != null) {
            span.setTag(FIREHOSE_STREAM_NAME, streamName);
        }
    }

    @Override
    protected String getMessageId(Exchange exchange) {
        return exchange.getIn().getHeader(RECORD_ID, String.class);
    }

}
