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

public class AwsBedrockSpanDecorator extends AbstractSpanDecorator {

    static final String BEDROCK_OPERATION = "operation";
    static final String BEDROCK_MODEL_CONTENT_TYPE = "modelContentType";
    static final String BEDROCK_STOP_REASON = "stopReason";
    static final String BEDROCK_TOKEN_COUNT = "tokenCount";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.bedrock.runtime.BedrockConstants}
     */
    static final String OPERATION = "CamelAwsBedrockOperation";
    static final String MODEL_CONTENT_TYPE = "CamelAwsBedrockContentType";
    static final String CONVERSE_STOP_REASON = "CamelAwsBedrockConverseStopReason";
    static final String STREAMING_TOKEN_COUNT = "CamelAwsBedrockTokenCount";

    @Override
    public String getComponent() {
        return "aws-bedrock";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.bedrock.runtime.BedrockComponent";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(BEDROCK_OPERATION, operation);
        }

        String modelContentType = exchange.getIn().getHeader(MODEL_CONTENT_TYPE, String.class);
        if (modelContentType != null) {
            span.setTag(BEDROCK_MODEL_CONTENT_TYPE, modelContentType);
        }

        String stopReason = exchange.getIn().getHeader(CONVERSE_STOP_REASON, String.class);
        if (stopReason != null) {
            span.setTag(BEDROCK_STOP_REASON, stopReason);
        }

        Integer tokenCount = exchange.getIn().getHeader(STREAMING_TOKEN_COUNT, Integer.class);
        if (tokenCount != null) {
            span.setTag(BEDROCK_TOKEN_COUNT, tokenCount.toString());
        }
    }

}
