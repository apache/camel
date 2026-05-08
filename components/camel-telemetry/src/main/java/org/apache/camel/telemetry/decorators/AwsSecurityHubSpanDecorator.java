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

public class AwsSecurityHubSpanDecorator extends AbstractSpanDecorator {

    static final String SECURITY_HUB_OPERATION = "operation";
    static final String SECURITY_HUB_FINDING_ID = "findingId";
    static final String SECURITY_HUB_PRODUCT_ARN = "productArn";

    /**
     * Constants copied from {@link org.apache.camel.component.aws.securityhub.SecurityHubConstants}
     */
    static final String OPERATION = "CamelAwsSecurityHubOperation";
    static final String FINDING_ID = "CamelAwsSecurityHubFindingId";
    static final String PRODUCT_ARN = "CamelAwsSecurityHubProductArn";

    @Override
    public String getComponent() {
        return "aws-security-hub";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws.securityhub.SecurityHubComponent";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(SECURITY_HUB_OPERATION, operation);
        }

        String findingId = exchange.getIn().getHeader(FINDING_ID, String.class);
        if (findingId != null) {
            span.setTag(SECURITY_HUB_FINDING_ID, findingId);
        }

        String productArn = exchange.getIn().getHeader(PRODUCT_ARN, String.class);
        if (productArn != null) {
            span.setTag(SECURITY_HUB_PRODUCT_ARN, productArn);
        }
    }

}
