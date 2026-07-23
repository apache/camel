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

public class AwsComprehendSpanDecorator extends AbstractSpanDecorator {

    static final String COMPREHEND_OPERATION = "operation";
    static final String COMPREHEND_LANGUAGE_CODE = "languageCode";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.comprehend.Comprehend2Constants}
     */
    static final String OPERATION = "CamelAwsComprehendOperation";
    static final String LANGUAGE_CODE = "CamelAwsComprehendLanguageCode";

    @Override
    public String getComponent() {
        return "aws2-comprehend";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.comprehend.Comprehend2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(COMPREHEND_OPERATION, operation);
        }

        String languageCode = exchange.getIn().getHeader(LANGUAGE_CODE, String.class);
        if (languageCode != null) {
            span.setTag(COMPREHEND_LANGUAGE_CODE, languageCode);
        }
    }

}
