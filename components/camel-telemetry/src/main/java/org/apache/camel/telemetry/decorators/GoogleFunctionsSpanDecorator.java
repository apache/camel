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

public class GoogleFunctionsSpanDecorator extends AbstractSpanDecorator {

    static final String FUNCTIONS_OPERATION = "operation";
    static final String FUNCTIONS_ENTRY_POINT = "entryPoint";
    static final String FUNCTIONS_RUNTIME = "runtime";

    /**
     * Constants copied from {@link org.apache.camel.component.google.functions.GoogleCloudFunctionsConstants}
     */
    static final String OPERATION = "CamelGoogleCloudFunctionsOperation";
    static final String ENTRY_POINT = "CamelGoogleCloudFunctionsEntryPoint";
    static final String RUNTIME = "CamelGoogleCloudFunctionsRuntime";

    @Override
    public String getComponent() {
        return "google-functions";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.google.functions.GoogleCloudFunctionsComponent";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        Object operation = exchange.getIn().getHeader(OPERATION);
        if (operation != null) {
            span.setTag(FUNCTIONS_OPERATION, operation.toString());
        }

        String entryPoint = exchange.getIn().getHeader(ENTRY_POINT, String.class);
        if (entryPoint != null) {
            span.setTag(FUNCTIONS_ENTRY_POINT, entryPoint);
        }

        String runtime = exchange.getIn().getHeader(RUNTIME, String.class);
        if (runtime != null) {
            span.setTag(FUNCTIONS_RUNTIME, runtime);
        }
    }
}
