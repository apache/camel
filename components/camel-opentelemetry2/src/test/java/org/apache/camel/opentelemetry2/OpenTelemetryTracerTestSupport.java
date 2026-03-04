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
package org.apache.camel.opentelemetry2;

import java.util.List;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.telemetry.Op;
import org.apache.camel.telemetry.TagConstants;
import org.apache.camel.test.junit6.ExchangeTestSupport;

public class OpenTelemetryTracerTestSupport extends ExchangeTestSupport {

    protected CamelOpenTelemetryExtension otelExtension = CamelOpenTelemetryExtension.create();

    protected static SpanData getSpan(List<SpanData> trace, String uri, Op op) {
        for (SpanData span : trace) {
            String camelURI = span.getAttributes().get(AttributeKey.stringKey("camel.uri"));
            if (camelURI != null && camelURI.equals(uri)) {
                String operation = span.getAttributes().get(AttributeKey.stringKey(TagConstants.OP));
                if (operation != null && operation.equals(op.toString())) {
                    return span;
                }
            }
        }
        throw new IllegalArgumentException("Trying to get a non existing span!");
    }

}
