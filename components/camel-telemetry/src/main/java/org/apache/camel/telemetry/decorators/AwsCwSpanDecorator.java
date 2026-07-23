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

public class AwsCwSpanDecorator extends AbstractSpanDecorator {

    static final String CW_OPERATION = "operation";
    static final String CW_METRIC_NAMESPACE = "metricNamespace";
    static final String CW_METRIC_NAME = "metricName";
    static final String CW_ALARM_NAME = "alarmName";
    static final String CW_ALARM_STATE = "alarmState";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.cw.Cw2Constants}
     */
    static final String OPERATION = "CamelAwsCwOperation";
    static final String METRIC_NAMESPACE = "CamelAwsCwMetricNamespace";
    static final String METRIC_NAME = "CamelAwsCwMetricName";
    static final String ALARM_NAME = "CamelAwsCwAlarmName";
    static final String ALARM_STATE = "CamelAwsCwAlarmState";

    @Override
    public String getComponent() {
        return "aws2-cw";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.cw.Cw2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(CW_OPERATION, operation);
        }

        String metricNamespace = exchange.getIn().getHeader(METRIC_NAMESPACE, String.class);
        if (metricNamespace != null) {
            span.setTag(CW_METRIC_NAMESPACE, metricNamespace);
        }

        String metricName = exchange.getIn().getHeader(METRIC_NAME, String.class);
        if (metricName != null) {
            span.setTag(CW_METRIC_NAME, metricName);
        }

        String alarmName = exchange.getIn().getHeader(ALARM_NAME, String.class);
        if (alarmName != null) {
            span.setTag(CW_ALARM_NAME, alarmName);
        }

        String alarmState = exchange.getIn().getHeader(ALARM_STATE, String.class);
        if (alarmState != null) {
            span.setTag(CW_ALARM_STATE, alarmState);
        }
    }

}
