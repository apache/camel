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

public class AwsEventbridgeSpanDecorator extends AbstractMessagingSpanDecorator {

    static final String EVENTBRIDGE_OPERATION = "operation";
    static final String EVENTBRIDGE_RULE_NAME = "ruleName";
    static final String EVENTBRIDGE_EVENT_SOURCE = "eventSource";
    static final String EVENTBRIDGE_EVENT_DETAIL_TYPE = "eventDetailType";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.eventbridge.EventbridgeConstants}
     */
    static final String OPERATION = "CamelAwsEventbridgeOperation";
    static final String RULE_NAME = "CamelAwsEventbridgeRuleName";
    static final String EVENT_SOURCE = "CamelAwsEventbridgeSource";
    static final String EVENT_DETAIL_TYPE = "CamelAwsEventbridgeDetailType";
    static final String MESSAGE_ID = "CamelAwsEventbridgeMessageId";

    @Override
    public String getComponent() {
        return "aws2-eventbridge";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.eventbridge.EventbridgeComponent";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(EVENTBRIDGE_OPERATION, operation);
        }

        String ruleName = exchange.getIn().getHeader(RULE_NAME, String.class);
        if (ruleName != null) {
            span.setTag(EVENTBRIDGE_RULE_NAME, ruleName);
        }

        String eventSource = exchange.getIn().getHeader(EVENT_SOURCE, String.class);
        if (eventSource != null) {
            span.setTag(EVENTBRIDGE_EVENT_SOURCE, eventSource);
        }

        String eventDetailType = exchange.getIn().getHeader(EVENT_DETAIL_TYPE, String.class);
        if (eventDetailType != null) {
            span.setTag(EVENTBRIDGE_EVENT_DETAIL_TYPE, eventDetailType);
        }
    }

    @Override
    protected String getMessageId(Exchange exchange) {
        return exchange.getIn().getHeader(MESSAGE_ID, String.class);
    }

}
