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

public class AwsMqSpanDecorator extends AbstractSpanDecorator {

    static final String MQ_OPERATION = "operation";
    static final String MQ_BROKER_NAME = "brokerName";
    static final String MQ_BROKER_ID = "brokerId";
    static final String MQ_BROKER_ARN = "brokerArn";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.mq.MQ2Constants}
     */
    static final String OPERATION = "CamelAwsMQOperation";
    static final String BROKER_NAME = "CamelAwsMQBrokerName";
    static final String BROKER_ID = "CamelAwsMQBrokerID";
    static final String BROKER_ARN = "CamelAwsMQBrokerArn";

    @Override
    public String getComponent() {
        return "aws2-mq";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.mq.MQ2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(MQ_OPERATION, operation);
        }

        String brokerName = exchange.getIn().getHeader(BROKER_NAME, String.class);
        if (brokerName != null) {
            span.setTag(MQ_BROKER_NAME, brokerName);
        }

        String brokerId = exchange.getIn().getHeader(BROKER_ID, String.class);
        if (brokerId != null) {
            span.setTag(MQ_BROKER_ID, brokerId);
        }

        String brokerArn = exchange.getIn().getHeader(BROKER_ARN, String.class);
        if (brokerArn != null) {
            span.setTag(MQ_BROKER_ARN, brokerArn);
        }
    }

}
