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

public class AwsStsSpanDecorator extends AbstractSpanDecorator {

    static final String STS_OPERATION = "operation";
    static final String STS_ROLE_ARN = "roleArn";
    static final String STS_ROLE_SESSION_NAME = "roleSessionName";
    static final String STS_FEDERATED_NAME = "federatedName";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.sts.STS2Constants}
     */
    static final String OPERATION = "CamelAwsStsOperation";
    static final String ROLE_ARN = "CamelAwsStsRoleArn";
    static final String ROLE_SESSION_NAME = "CamelAwsStsRoleSessionName";
    static final String FEDERATED_NAME = "CamelAwsStsFederatedName";

    @Override
    public String getComponent() {
        return "aws2-sts";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.sts.STS2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(STS_OPERATION, operation);
        }

        String roleArn = exchange.getIn().getHeader(ROLE_ARN, String.class);
        if (roleArn != null) {
            span.setTag(STS_ROLE_ARN, roleArn);
        }

        String roleSessionName = exchange.getIn().getHeader(ROLE_SESSION_NAME, String.class);
        if (roleSessionName != null) {
            span.setTag(STS_ROLE_SESSION_NAME, roleSessionName);
        }

        String federatedName = exchange.getIn().getHeader(FEDERATED_NAME, String.class);
        if (federatedName != null) {
            span.setTag(STS_FEDERATED_NAME, federatedName);
        }
    }

}
