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

package org.apache.camel.opentelemetry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SpanTreePrinter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpanTreePrinter.class);
    private static final AttributeKey<String> CAMEL_URI_ATTRIBUTE = AttributeKey.stringKey("camel.uri");

    private SpanTreePrinter() {}

    // Method to print the spans in a tree view
    public static void printSpanTree(List<SpanData> spans) {
        if (!LOGGER.isInfoEnabled()) {
            return;
        }

        // Map to hold child spans against their parent span id
        Map<String, List<SpanData>> spanMap = new LinkedHashMap<>();
        SpanData rootSpan = null;

        // Populate the spanMap with the spans
        for (SpanData span : spans) {
            SpanContext context = span.getParentSpanContext();
            String parentSpanId = context.getSpanId();

            if (parentSpanId.isEmpty() || parentSpanId.equals(SpanId.getInvalid())) {
                rootSpan = span; // Identify the root span (no parent span or invalid parent)
            } else {
                spanMap.computeIfAbsent(parentSpanId, k -> new ArrayList<>()).add(span);
            }
        }

        // Print the tree starting from the root span
        if (rootSpan != null) {
            LOGGER.info("Span tree:");
            printSpan(rootSpan, spanMap, "", true);
        } else {
            LOGGER.warn("No root span found!");
        }
    }

    // Recursive method to print each span and its children
    private static void printSpan(SpanData span, Map<String, List<SpanData>> spanMap, String indent, boolean last) {
        SpanContext context = span.getSpanContext();
        Attributes attributes = span.getAttributes();
        String routeDescription = " span: %s (%s) - %s (%s) %s"
                .formatted(
                        context.getSpanId(),
                        span.getKind(),
                        span.getName(),
                        attributes.get(CAMEL_URI_ATTRIBUTE),
                        humanReadableFormat(Duration.ofNanos(span.getEndEpochNanos() - span.getStartEpochNanos())));

        LOGGER.info("{}{}{}", indent, last ? "└─ " : "├─ ", routeDescription);

        List<SpanData> children = spanMap.get(context.getSpanId());
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                printSpan(children.get(i), spanMap, indent + (last ? "    " : "│   "), i == children.size() - 1);
            }
        }
    }

    private static String humanReadableFormat(Duration duration) {
        return duration.toString()
                .substring(2)
                .replaceAll("(\\d[HMS])(?!$)", "$1 ")
                .toLowerCase();
    }
}
