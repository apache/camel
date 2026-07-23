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

public class AwsTranscribeSpanDecorator extends AbstractSpanDecorator {

    static final String TRANSCRIBE_TRANSCRIPTION_JOB_NAME = "transcriptionJobName";
    static final String TRANSCRIBE_LANGUAGE_CODE = "languageCode";
    static final String TRANSCRIBE_MEDIA_FORMAT = "mediaFormat";
    static final String TRANSCRIBE_MEDIA_URI = "mediaUri";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.transcribe.Transcribe2Constants}
     */
    static final String TRANSCRIPTION_JOB_NAME = "CamelAwsTranscribeTranscriptionJobName";
    static final String LANGUAGE_CODE = "CamelAwsTranscribeLanguageCode";
    static final String MEDIA_FORMAT = "CamelAwsTranscribeMediaFormat";
    static final String MEDIA_URI = "CamelAwsTranscribeMediaUri";

    @Override
    public String getComponent() {
        return "aws2-transcribe";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.transcribe.Transcribe2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String transcriptionJobName = exchange.getIn().getHeader(TRANSCRIPTION_JOB_NAME, String.class);
        if (transcriptionJobName != null) {
            span.setTag(TRANSCRIBE_TRANSCRIPTION_JOB_NAME, transcriptionJobName);
        }

        String languageCode = exchange.getIn().getHeader(LANGUAGE_CODE, String.class);
        if (languageCode != null) {
            span.setTag(TRANSCRIBE_LANGUAGE_CODE, languageCode);
        }

        String mediaFormat = exchange.getIn().getHeader(MEDIA_FORMAT, String.class);
        if (mediaFormat != null) {
            span.setTag(TRANSCRIBE_MEDIA_FORMAT, mediaFormat);
        }

        String mediaUri = exchange.getIn().getHeader(MEDIA_URI, String.class);
        if (mediaUri != null) {
            span.setTag(TRANSCRIBE_MEDIA_URI, mediaUri);
        }
    }

}
