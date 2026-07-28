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

public class GoogleVertexAISpanDecorator extends AbstractSpanDecorator {

    static final String VERTEXAI_OPERATION = "operation";
    static final String VERTEXAI_MODEL_ID = "modelId";
    static final String VERTEXAI_LOCATION = "location";

    /**
     * Constants copied from {@link org.apache.camel.component.google.vertexai.GoogleVertexAIConstants}. The prompt and
     * chat messages carry user content and the tuning parameters are not useful as tags, so they are not emitted.
     */
    static final String OPERATION = "CamelGoogleVertexAIOperation";
    static final String MODEL_ID = "CamelGoogleVertexAIModelId";
    static final String LOCATION = "CamelGoogleVertexAILocation";

    @Override
    public String getComponent() {
        return "google-vertexai";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.google.vertexai.GoogleVertexAIComponent";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        Object operation = exchange.getIn().getHeader(OPERATION);
        if (operation != null) {
            span.setTag(VERTEXAI_OPERATION, operation.toString());
        }

        String modelId = exchange.getIn().getHeader(MODEL_ID, String.class);
        if (modelId != null) {
            span.setTag(VERTEXAI_MODEL_ID, modelId);
        }

        String location = exchange.getIn().getHeader(LOCATION, String.class);
        if (location != null) {
            span.setTag(VERTEXAI_LOCATION, location);
        }
    }
}
