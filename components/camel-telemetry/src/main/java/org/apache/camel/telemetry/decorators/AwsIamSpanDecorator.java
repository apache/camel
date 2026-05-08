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

public class AwsIamSpanDecorator extends AbstractSpanDecorator {

    static final String IAM_OPERATION = "operation";
    static final String IAM_USER_NAME = "userName";
    static final String IAM_GROUP_NAME = "groupName";
    static final String IAM_ROLE_NAME = "roleName";
    static final String IAM_POLICY_NAME = "policyName";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.iam.IAM2Constants}
     */
    static final String OPERATION = "CamelAwsIAMOperation";
    static final String USERNAME = "CamelAwsIAMUsername";
    static final String GROUP_NAME = "CamelAwsIAMGroupName";
    static final String ROLE_NAME = "CamelAwsIAMRoleName";
    static final String POLICY_NAME = "CamelAwsIAMPolicyName";

    @Override
    public String getComponent() {
        return "aws2-iam";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.iam.IAM2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(IAM_OPERATION, operation);
        }

        String userName = exchange.getIn().getHeader(USERNAME, String.class);
        if (userName != null) {
            span.setTag(IAM_USER_NAME, userName);
        }

        String groupName = exchange.getIn().getHeader(GROUP_NAME, String.class);
        if (groupName != null) {
            span.setTag(IAM_GROUP_NAME, groupName);
        }

        String roleName = exchange.getIn().getHeader(ROLE_NAME, String.class);
        if (roleName != null) {
            span.setTag(IAM_ROLE_NAME, roleName);
        }

        String policyName = exchange.getIn().getHeader(POLICY_NAME, String.class);
        if (policyName != null) {
            span.setTag(IAM_POLICY_NAME, policyName);
        }
    }

}
