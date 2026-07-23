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

public class AwsPollySpanDecorator extends AbstractSpanDecorator {

    static final String POLLY_OPERATION = "operation";
    static final String POLLY_VOICE_ID = "voiceId";
    static final String POLLY_OUTPUT_FORMAT = "outputFormat";
    static final String POLLY_ENGINE = "engine";
    static final String POLLY_LANGUAGE_CODE = "languageCode";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.polly.Polly2Constants}
     */
    static final String OPERATION = "CamelAwsPollyOperation";
    static final String VOICE_ID = "CamelAwsPollyVoiceId";
    static final String OUTPUT_FORMAT = "CamelAwsPollyOutputFormat";
    static final String ENGINE = "CamelAwsPollyEngine";
    static final String LANGUAGE_CODE = "CamelAwsPollyLanguageCode";

    @Override
    public String getComponent() {
        return "aws2-polly";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.polly.Polly2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(POLLY_OPERATION, operation);
        }

        String voiceId = exchange.getIn().getHeader(VOICE_ID, String.class);
        if (voiceId != null) {
            span.setTag(POLLY_VOICE_ID, voiceId);
        }

        String outputFormat = exchange.getIn().getHeader(OUTPUT_FORMAT, String.class);
        if (outputFormat != null) {
            span.setTag(POLLY_OUTPUT_FORMAT, outputFormat);
        }

        String engine = exchange.getIn().getHeader(ENGINE, String.class);
        if (engine != null) {
            span.setTag(POLLY_ENGINE, engine);
        }

        String languageCode = exchange.getIn().getHeader(LANGUAGE_CODE, String.class);
        if (languageCode != null) {
            span.setTag(POLLY_LANGUAGE_CODE, languageCode);
        }
    }

}
