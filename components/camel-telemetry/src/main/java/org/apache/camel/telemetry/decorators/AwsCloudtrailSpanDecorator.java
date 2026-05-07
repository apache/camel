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

public class AwsCloudtrailSpanDecorator extends AbstractMessagingSpanDecorator {

    static final String CLOUDTRAIL_EVENT_NAME = "eventName";
    static final String CLOUDTRAIL_EVENT_SOURCE = "eventSource";
    static final String CLOUDTRAIL_USERNAME = "username";

    /**
     * Constants copied from {@link org.apache.camel.component.aws.cloudtrail.CloudtrailConstants}
     */
    static final String EVENT_ID = "CamelAwsCloudTrailEventId";
    static final String EVENT_NAME = "CamelAwsCloudTrailEventName";
    static final String EVENT_SOURCE = "CamelAwsCloudTrailEventSource";
    static final String USERNAME = "CamelAwsCloudTrailEventUsername";

    @Override
    public String getComponent() {
        return "aws-cloudtrail";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws.cloudtrail.CloudtrailComponent";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String eventName = exchange.getIn().getHeader(EVENT_NAME, String.class);
        if (eventName != null) {
            span.setTag(CLOUDTRAIL_EVENT_NAME, eventName);
        }

        String eventSource = exchange.getIn().getHeader(EVENT_SOURCE, String.class);
        if (eventSource != null) {
            span.setTag(CLOUDTRAIL_EVENT_SOURCE, eventSource);
        }

        String username = exchange.getIn().getHeader(USERNAME, String.class);
        if (username != null) {
            span.setTag(CLOUDTRAIL_USERNAME, username);
        }
    }

    @Override
    protected String getMessageId(Exchange exchange) {
        return exchange.getIn().getHeader(EVENT_ID, String.class);
    }

}
