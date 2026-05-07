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

public class AwsSnsSpanDecorator extends AbstractMessagingSpanDecorator {

    static final String SNS_SUBJECT = "subject";
    static final String SNS_MESSAGE_STRUCTURE = "messageStructure";
    static final String SNS_MESSAGE_GROUP_ID = "messageGroupId";
    static final String SNS_SEQUENCE_NUMBER = "sequenceNumber";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.sns.Sns2Constants}
     */
    static final String MESSAGE_ID = "CamelAwsSnsMessageId";
    static final String SUBJECT = "CamelAwsSnsSubject";
    static final String MESSAGE_STRUCTURE = "CamelAwsSnsMessageStructure";
    static final String MESSAGE_GROUP_ID = "CamelAwsSnsMessageGroupId";
    static final String SEQUENCE_NUMBER = "CamelAwsSnsSequenceNumber";

    @Override
    public String getComponent() {
        return "aws2-sns";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.sns.Sns2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String subject = exchange.getIn().getHeader(SUBJECT, String.class);
        if (subject != null) {
            span.setTag(SNS_SUBJECT, subject);
        }

        String messageStructure = exchange.getIn().getHeader(MESSAGE_STRUCTURE, String.class);
        if (messageStructure != null) {
            span.setTag(SNS_MESSAGE_STRUCTURE, messageStructure);
        }

        String messageGroupId = exchange.getIn().getHeader(MESSAGE_GROUP_ID, String.class);
        if (messageGroupId != null) {
            span.setTag(SNS_MESSAGE_GROUP_ID, messageGroupId);
        }

        String sequenceNumber = exchange.getIn().getHeader(SEQUENCE_NUMBER, String.class);
        if (sequenceNumber != null) {
            span.setTag(SNS_SEQUENCE_NUMBER, sequenceNumber);
        }
    }

    @Override
    protected String getMessageId(Exchange exchange) {
        return exchange.getIn().getHeader(MESSAGE_ID, String.class);
    }

}
