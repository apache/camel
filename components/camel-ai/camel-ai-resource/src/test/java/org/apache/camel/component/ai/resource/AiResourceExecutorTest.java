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
import java.util.List;
import java.util.Map;

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

                from("ai-resource:list_as_blob"
                     + "?resourceUri=camel:///list.bin"
                     + "&tags=test"
                     + "&mimeType=application/octet-stream")
                        .setBody(constant(List.of("a", "b")));

                from("ai-resource:map_as_blob"
                     + "?resourceUri=camel:///map.bin"
                     + "&tags=test"
                     + "&mimeType=application/octet-stream")
                        .setBody(constant(Map.of("key", "value")));

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

                from("ai-resource:suspendable"
                     + "?resourceUri=camel:///suspendable"
                     + "&tags=test")
                        .routeId("suspendable-route")
                        .setBody(constant("suspendable content"));

                from("ai-resource:suspendable_template"
                     + "?uriTemplate=camel:///suspendable-items/{id}"
                     + "&tags=test")
                        .routeId("suspendable-template-route")
                        .setBody(constant("suspendable item"));
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
    public void testListBodyWithBinaryMimeTypeIsAConversionError() {
        AiResourceResult result = read("camel:///list.bin");

        assertThat(result).isInstanceOf(AiResourceResult.ExecutionError.class);
        AiResourceResult.ExecutionError error = (AiResourceResult.ExecutionError) result;
        String expected = "Resource 'list_as_blob' produced a body that cannot be converted to byte[]"
                          + " (declared mimeType is application/octet-stream)";
        assertThat(error.message()).isEqualTo(expected);
        assertThat(error.cause()).isInstanceOf(IllegalStateException.class)
                .hasNoCause();
    }

    @Test
    public void testMapBodyWithBinaryMimeTypeIsAConversionError() {
        AiResourceResult result = read("camel:///map.bin");

        assertThat(result).isInstanceOf(AiResourceResult.ExecutionError.class);
        String expected = "Resource 'map_as_blob' produced a body that cannot be converted to byte[]"
                          + " (declared mimeType is application/octet-stream)";
        assertThat(((AiResourceResult.ExecutionError) result).message()).isEqualTo(expected);
    }

    @Test
    public void testEmptyBodyIsAnError() {
        AiResourceResult result = read("camel:///empty");

        assertThat(result).isInstanceOf(AiResourceResult.ExecutionError.class);
        assertThat(((AiResourceResult.ExecutionError) result).message()).contains("empty_resource")
                .doesNotContain("camel:///empty");
    }

    @Test
    public void testRouteExceptionIsReportedAsExecutionError() {
        AiResourceResult result = read("camel:///failing");

        assertThat(result).isInstanceOf(AiResourceResult.ExecutionError.class);
        AiResourceResult.ExecutionError error = (AiResourceResult.ExecutionError) result;
        assertThat(error.cause()).isInstanceOf(IllegalStateException.class);
        assertThat(error.message()).contains("failing_resource").doesNotContain("camel:///failing");
    }

    @Test
    public void testExchangeExceptionIsReportedAsExecutionError() {
        AiResourceResult result = read("camel:///exchange-failure");

        assertThat(result).isInstanceOf(AiResourceResult.ExecutionError.class);
        assertThat(((AiResourceResult.ExecutionError) result).cause())
                .isInstanceOf(IllegalStateException.class);
        assertThat(((AiResourceResult.ExecutionError) result).message())
                .contains("exchange_failure").doesNotContain("camel:///exchange-failure");
    }

    @Test
    public void testMissingConsumerIsReportedAsExecutionError() {
        AiResourceSpec spec = new AiResourceSpec("orphan", "camel:///orphan", "no consumer", "text/plain", null, null);

        AiResourceResult result = AiResourceExecutor.execute(spec, new DefaultExchange(context));

        assertThat(result).isInstanceOf(AiResourceResult.ExecutionError.class);
        assertThat(((AiResourceResult.ExecutionError) result).message()).contains("No consumer available", "orphan")
                .doesNotContain("camel:///orphan");
    }

    @Test
    public void testSuspendedResourceRefusesReadsUntilItsRouteResumes() throws Exception {
        // a caller can hold a spec it obtained before the route was suspended
        AiResourceSpec spec = findSpec("camel:///suspendable");
        context.getRouteController().suspendRoute("suspendable-route");

        AiResourceResult refused = AiResourceExecutor.execute(spec, new DefaultExchange(context));

        assertThat(refused).isInstanceOf(AiResourceResult.ExecutionError.class);
        AiResourceResult.ExecutionError error = (AiResourceResult.ExecutionError) refused;
        assertThat(error.message()).isEqualTo("Resource 'suspendable' is suspended");
        assertThat(error.cause()).isInstanceOf(IllegalStateException.class);

        context.getRouteController().resumeRoute("suspendable-route");

        assertThat(findSpec("camel:///suspendable")).isSameAs(spec);
        AiResourceResult result = AiResourceExecutor.execute(spec, new DefaultExchange(context));
        assertThat(result).isInstanceOf(AiResourceResult.Text.class);
        assertThat(((AiResourceResult.Text) result).value()).isEqualTo("suspendable content");
    }

    @Test
    public void testSuspendedResourceTemplateRefusesReadsUntilItsRouteResumes() throws Exception {
        AiResourceTemplateSpec template = findTemplate("camel:///suspendable-items/{id}");
        context.getRouteController().suspendRoute("suspendable-template-route");

        assertThat(AiResourceRegistry.getOrCreate(context).getResourceTemplatesByTag("test")).doesNotContain(template);
        AiResourceResult refused = AiResourceExecutor.execute(
                template.asResourceSpec("camel:///suspendable-items/42"), new DefaultExchange(context));

        assertThat(refused).isInstanceOf(AiResourceResult.ExecutionError.class);
        assertThat(((AiResourceResult.ExecutionError) refused).message())
                .isEqualTo("Resource 'suspendable_template' is suspended");

        context.getRouteController().resumeRoute("suspendable-template-route");

        assertThat(findTemplate("camel:///suspendable-items/{id}")).isSameAs(template);
        AiResourceResult result = AiResourceExecutor.execute(
                template.asResourceSpec("camel:///suspendable-items/42"), new DefaultExchange(context));
        assertThat(result).isInstanceOf(AiResourceResult.Text.class);
        assertThat(((AiResourceResult.Text) result).value()).isEqualTo("suspendable item");
    }

    private AiResourceResult read(String resourceUri) {
        Exchange exchange = new DefaultExchange(context);
        return AiResourceExecutor.execute(findSpec(resourceUri), exchange);
    }

    private AiResourceSpec findSpec(String resourceUri) {
        return AiResourceRegistry.getOrCreate(context).getResourcesByTag("test").stream()
                .filter(s -> resourceUri.equals(s.getUri()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Resource not found: " + resourceUri));
    }

    private AiResourceTemplateSpec findTemplate(String uriTemplate) {
        return AiResourceRegistry.getOrCreate(context).getResourceTemplatesByTag("test").stream()
                .filter(s -> uriTemplate.equals(s.getUriTemplate()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Resource template not found: " + uriTemplate));
    }
}
