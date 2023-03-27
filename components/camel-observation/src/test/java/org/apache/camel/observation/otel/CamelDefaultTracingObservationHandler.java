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
package org.apache.camel.observation.otel;

import io.micrometer.common.util.StringUtils;
import io.micrometer.observation.Observation;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;

public class CamelDefaultTracingObservationHandler extends DefaultTracingObservationHandler {

    /**
     * Creates a new instance of {@link DefaultTracingObservationHandler}.
     *
     * @param tracer the tracer to use to record events
     */
    public CamelDefaultTracingObservationHandler(Tracer tracer) {
        super(tracer);
    }

    // Current implementation of OpenTelemetry is not doing hyphens
    // e.g. ServiceB does not become service-b
    @Override
    public String getSpanName(Observation.Context context) {
        String name = context.getName();
        if (StringUtils.isNotBlank(context.getContextualName())) {
            name = context.getContextualName();
        }
        return name;
    }

    @Override
    public void tagSpan(Observation.Context context, Span span) {
        super.tagSpan(context, span);
        if (context.getError() != null) {
            span.tag("error", "true");
        }
    }
}
