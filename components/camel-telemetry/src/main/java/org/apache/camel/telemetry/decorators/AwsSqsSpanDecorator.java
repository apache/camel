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

public class AwsSqsSpanDecorator extends AbstractMessagingSpanDecorator {

    static final String SQS_MD5_OF_BODY = "md5OfBody";
    static final String SQS_RECEIPT_HANDLE = "receiptHandle";
    static final String SQS_DELAY_SECONDS = "delaySeconds";
    static final String SQS_OPERATION = "operation";
    static final String SQS_MESSAGE_GROUP_ID = "messageGroupId";
    static final String SQS_SEQUENCE_NUMBER = "sequenceNumber";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.sqs.Sqs2Constants}
     */
    static final String MESSAGE_ID = "CamelAwsSqsMessageId";
    static final String MD5_OF_BODY = "CamelAwsSqsMD5OfBody";
    static final String RECEIPT_HANDLE = "CamelAwsSqsReceiptHandle";
    static final String DELAY_HEADER = "CamelAwsSqsDelaySeconds";
    static final String OPERATION = "CamelAwsSqsOperation";
    static final String MESSAGE_GROUP_ID = "CamelAwsMessageGroupId";
    static final String SEQUENCE_NUMBER = "CamelAwsSqsSequenceNumber";

    @Override
    public String getComponent() {
        return "aws2-sqs";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.sqs.Sqs2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String md5OfBody = exchange.getIn().getHeader(MD5_OF_BODY, String.class);
        if (md5OfBody != null) {
            span.setTag(SQS_MD5_OF_BODY, md5OfBody);
        }

        String receiptHandle = exchange.getIn().getHeader(RECEIPT_HANDLE, String.class);
        if (receiptHandle != null) {
            span.setTag(SQS_RECEIPT_HANDLE, receiptHandle);
        }

        Integer delaySeconds = exchange.getIn().getHeader(DELAY_HEADER, Integer.class);
        if (delaySeconds != null) {
            span.setTag(SQS_DELAY_SECONDS, delaySeconds.toString());
        }

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(SQS_OPERATION, operation);
        }

        String messageGroupId = exchange.getIn().getHeader(MESSAGE_GROUP_ID, String.class);
        if (messageGroupId != null) {
            span.setTag(SQS_MESSAGE_GROUP_ID, messageGroupId);
        }

        String sequenceNumber = exchange.getIn().getHeader(SEQUENCE_NUMBER, String.class);
        if (sequenceNumber != null) {
            span.setTag(SQS_SEQUENCE_NUMBER, sequenceNumber);
        }
    }

    @Override
    protected String getMessageId(Exchange exchange) {
        return exchange.getIn().getHeader(MESSAGE_ID, String.class);
    }

}
