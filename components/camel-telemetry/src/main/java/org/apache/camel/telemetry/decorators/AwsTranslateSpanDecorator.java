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

public class AwsTranslateSpanDecorator extends AbstractSpanDecorator {

    static final String TRANSLATE_OPERATION = "operation";
    static final String TRANSLATE_SOURCE_LANGUAGE = "sourceLanguage";
    static final String TRANSLATE_TARGET_LANGUAGE = "targetLanguage";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.translate.Translate2Constants}
     */
    static final String OPERATION = "CamelAwsTranslateOperation";
    static final String SOURCE_LANGUAGE = "CamelAwsTranslateSourceLanguage";
    static final String TARGET_LANGUAGE = "CamelAwsTranslateTargetLanguage";

    @Override
    public String getComponent() {
        return "aws2-translate";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.translate.Translate2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(TRANSLATE_OPERATION, operation);
        }

        String sourceLanguage = exchange.getIn().getHeader(SOURCE_LANGUAGE, String.class);
        if (sourceLanguage != null) {
            span.setTag(TRANSLATE_SOURCE_LANGUAGE, sourceLanguage);
        }

        String targetLanguage = exchange.getIn().getHeader(TARGET_LANGUAGE, String.class);
        if (targetLanguage != null) {
            span.setTag(TRANSLATE_TARGET_LANGUAGE, targetLanguage);
        }
    }

}
