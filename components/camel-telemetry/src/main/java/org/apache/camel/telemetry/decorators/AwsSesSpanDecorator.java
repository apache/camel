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

public class AwsSesSpanDecorator extends AbstractSpanDecorator {

    static final String SES_FROM = "from";
    static final String SES_SUBJECT = "subject";
    static final String SES_TO = "to";
    static final String SES_MESSAGE_ID = "messageId";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.ses.Ses2Constants}
     */
    static final String FROM = "CamelAwsSesFrom";
    static final String SUBJECT = "CamelAwsSesSubject";
    static final String TO = "CamelAwsSesTo";
    static final String MESSAGE_ID = "CamelAwsSesMessageId";

    @Override
    public String getComponent() {
        return "aws2-ses";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.ses.Ses2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String from = exchange.getIn().getHeader(FROM, String.class);
        if (from != null) {
            span.setTag(SES_FROM, from);
        }

        String subject = exchange.getIn().getHeader(SUBJECT, String.class);
        if (subject != null) {
            span.setTag(SES_SUBJECT, subject);
        }

        String to = exchange.getIn().getHeader(TO, String.class);
        if (to != null) {
            span.setTag(SES_TO, to);
        }

        String messageId = exchange.getIn().getHeader(MESSAGE_ID, String.class);
        if (messageId != null) {
            span.setTag(SES_MESSAGE_ID, messageId);
        }
    }

}
