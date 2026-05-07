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

public class AwsKmsSpanDecorator extends AbstractSpanDecorator {

    static final String KMS_OPERATION = "operation";
    static final String KMS_KEY_ID = "keyId";
    static final String KMS_KEY_ARN = "keyArn";
    static final String KMS_KEY_STATE = "keyState";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.kms.KMS2Constants}
     */
    static final String OPERATION = "CamelAwsKMSOperation";
    static final String KEY_ID = "CamelAwsKMSKeyId";
    static final String KEY_ARN = "CamelAwsKMSKeyArn";
    static final String KEY_STATE = "CamelAwsKMSKeyState";

    @Override
    public String getComponent() {
        return "aws2-kms";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.kms.KMS2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(KMS_OPERATION, operation);
        }

        String keyId = exchange.getIn().getHeader(KEY_ID, String.class);
        if (keyId != null) {
            span.setTag(KMS_KEY_ID, keyId);
        }

        String keyArn = exchange.getIn().getHeader(KEY_ARN, String.class);
        if (keyArn != null) {
            span.setTag(KMS_KEY_ARN, keyArn);
        }

        String keyState = exchange.getIn().getHeader(KEY_STATE, String.class);
        if (keyState != null) {
            span.setTag(KMS_KEY_STATE, keyState);
        }
    }

}
