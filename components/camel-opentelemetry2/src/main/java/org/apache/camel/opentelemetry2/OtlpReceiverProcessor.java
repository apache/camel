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

import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processes incoming OTLP protobuf trace export requests and feeds parsed spans into the {@link DevSpanExporter}.
 */
final class OtlpReceiverProcessor implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(OtlpReceiverProcessor.class);

    private final DevSpanExporter exporter;

    OtlpReceiverProcessor(DevSpanExporter exporter) {
        this.exporter = exporter;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        byte[] body = exchange.getMessage().getBody(byte[].class);
        if (body == null || body.length == 0) {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
            exchange.getMessage().setBody(new byte[0]);
            return;
        }

        try {
            List<SpanData> spans = OtlpProtobufSpanData.fromProtobuf(body);
            // filter out spans for the OTLP receiver itself to avoid self-tracing noise
            spans = spans.stream()
                    .filter(s -> !s.getName().contains("/v1/traces"))
                    .toList();
            if (!spans.isEmpty()) {
                exporter.export(spans);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Received {} spans from OTLP exporter", spans.size());
                }
            }
        } catch (Exception e) {
            LOG.debug("Failed to parse OTLP protobuf: {}", e.getMessage());
        }

        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
        exchange.getMessage().setBody(new byte[0]);
    }
}
