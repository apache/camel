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

public class AwsEc2SpanDecorator extends AbstractSpanDecorator {

    static final String EC2_OPERATION = "operation";
    static final String EC2_IMAGE_ID = "imageId";
    static final String EC2_INSTANCE_TYPE = "instanceType";
    static final String EC2_SUBNET_ID = "subnetId";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.ec2.AWS2EC2Constants}
     */
    static final String OPERATION = "CamelAwsEC2Operation";
    static final String IMAGE_ID = "CamelAwsEC2ImageId";
    static final String INSTANCE_TYPE = "CamelAwsEC2InstanceType";
    static final String SUBNET_ID = "CamelAwsEC2SubnetId";

    @Override
    public String getComponent() {
        return "aws2-ec2";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.ec2.AWS2EC2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(EC2_OPERATION, operation);
        }

        String imageId = exchange.getIn().getHeader(IMAGE_ID, String.class);
        if (imageId != null) {
            span.setTag(EC2_IMAGE_ID, imageId);
        }

        String instanceType = exchange.getIn().getHeader(INSTANCE_TYPE, String.class);
        if (instanceType != null) {
            span.setTag(EC2_INSTANCE_TYPE, instanceType);
        }

        String subnetId = exchange.getIn().getHeader(SUBNET_ID, String.class);
        if (subnetId != null) {
            span.setTag(EC2_SUBNET_ID, subnetId);
        }
    }

}
