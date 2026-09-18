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
package org.apache.camel.component.ai.resource;

import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AiResourceExecutorTest extends CamelTestSupport {

    private static final byte[] PDF_BYTES = { 0x25, 0x50, 0x44, 0x46 };

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ai-resource:app_config"
                     + "?resourceUri=camel:///config/app.json"
                     + "&tags=test"
                     + "&mimeType=application/json")
                        .setBody(constant("{\"env\":\"test\"}"));

                from("ai-resource:latest_report"
                     + "?resourceUri=camel:///reports/latest.pdf"
                     + "&tags=test"
                     + "&mimeType=application/pdf")
                        .setBody(constant(PDF_BYTES));

                from("ai-resource:text_from_bytes"
                     + "?resourceUri=camel:///notes.txt"
                     + "&tags=test"
                     + "&mimeType=text/plain")
                        .setBody(constant("notes".getBytes(StandardCharsets.UTF_8)));

                from("ai-resource:empty_resource"
                     + "?resourceUri=camel:///empty"
                     + "&tags=test")
                        .setBody(constant((Object) null));

                from("ai-resource:failing_resource"
                     + "?resourceUri=camel:///failing"
                     + "&tags=test")
                        .throwException(new IllegalStateException("secret internal detail"));

                from("ai-resource:exchange_failure"
                     + "?resourceUri=camel:///exchange-failure"
                     + "&tags=test")
                        .process(exchange -> exchange.setException(new IllegalStateException("exchange failure")));
            }
        };
    }

    @Test
    public void testTextualResourceIsReadAsString() {
        AiResourceResult result = read("camel:///config/app.json");

        assertThat(result).isInstanceOf(AiResourceResult.Text.class);
        assertThat(((AiResourceResult.Text) result).value()).isEqualTo("{\"env\":\"test\"}");
    }

    @Test
    public void testBinaryResourceIsReadAsBytes() {
        AiResourceResult result = read("camel:///reports/latest.pdf");

        assertThat(result).isInstanceOf(AiResourceResult.Binary.class);
        assertThat(((AiResourceResult.Binary) result).value()).isEqualTo(PDF_BYTES);
    }

    @Test
    public void testTextualMimeTypeConvertsByteBodyToString() {
        AiResourceResult result = read("camel:///notes.txt");

        assertThat(result).isInstanceOf(AiResourceResult.Text.class);
        assertThat(((AiResourceResult.Text) result).value()).isEqualTo("notes");
    }

    @Test
    public void testEmptyBodyIsAnError() {
        AiResourceResult result = read("camel:///empty");

        assertThat(result).isInstanceOf(AiResourceResult.ExecutionError.class);
        assertThat(((AiResourceResult.ExecutionError) result).message()).contains("produced no content");
    }

    @Test
    public void testRouteExceptionIsReportedAsExecutionError() {
        AiResourceResult result = read("camel:///failing");

        assertThat(result).isInstanceOf(AiResourceResult.ExecutionError.class);
        AiResourceResult.ExecutionError error = (AiResourceResult.ExecutionError) result;
        assertThat(error.cause()).isInstanceOf(IllegalStateException.class);
        assertThat(error.message()).contains("camel:///failing");
    }

    @Test
    public void testExchangeExceptionIsReportedAsExecutionError() {
        AiResourceResult result = read("camel:///exchange-failure");

        assertThat(result).isInstanceOf(AiResourceResult.ExecutionError.class);
        assertThat(((AiResourceResult.ExecutionError) result).cause())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testMissingConsumerIsReportedAsExecutionError() {
        AiResourceSpec spec = new AiResourceSpec("orphan", "camel:///orphan", "no consumer", "text/plain", null, null);

        AiResourceResult result = AiResourceExecutor.execute(spec, new DefaultExchange(context));

        assertThat(result).isInstanceOf(AiResourceResult.ExecutionError.class);
        assertThat(((AiResourceResult.ExecutionError) result).message()).contains("No consumer available");
    }

    private AiResourceResult read(String resourceUri) {
        AiResourceSpec spec = AiResourceRegistry.getOrCreate(context).getResourcesByTag("test").stream()
                .filter(s -> resourceUri.equals(s.getUri()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Resource not found: " + resourceUri));
        Exchange exchange = new DefaultExchange(context);
        return AiResourceExecutor.execute(spec, exchange);
    }
}
