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
package org.apache.camel.component.ibm.watsonx.ai;

import java.io.IOException;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The text extraction and classification options reach the parameters of the requests.
 */
class WatsonxAiDocumentParametersTest extends CamelTestSupport {

    private static final String EXTRACTIONS = "/ml/v1/text/extractions";
    private static final String CLASSIFICATIONS = "/ml/v1/text/classifications";

    private static FakeWatsonxServer server;

    @BeforeAll
    static void startServer() throws IOException {
        server = FakeWatsonxServer.start();
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @BeforeEach
    void resetServer() {
        server.reset();
        server.respond(EXTRACTIONS, """
                {"metadata":{"id":"extraction-1","created_at":"2026-01-01T00:00:00.000Z","project_id":"my-project"},
                 "entity":{"results":{"status":"submitted"}}}""");
        server.respond(CLASSIFICATIONS, """
                {"metadata":{"id":"classification-1","created_at":"2026-01-01T00:00:00.000Z","project_id":"my-project"},
                 "entity":{"results":{"status":"submitted"}}}""");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getRegistry().bind("auth", FakeWatsonxServer.TEST_AUTHENTICATOR);
            }
        };
    }

    private Exchange send(String operation, String options, Map<String, Object> headers) {
        String uri = "ibm-watsonx-ai:docs?authenticator=#auth&baseUrl=" + server.baseUrl()
                     + "&projectId=my-project&cosUrl=" + server.baseUrl()
                     + "&documentConnectionId=doc-connection&documentBucket=documents"
                     + "&resultConnectionId=result-connection&resultBucket=results&operation=" + operation
                     + (options.isEmpty() ? "" : "&" + options);
        Exchange result = template.send(uri, e -> {
            e.getIn().setBody("folder/invoice.pdf");
            e.getIn().getHeaders().putAll(headers);
        });
        assertNull(result.getException(), () -> "Failed: " + result.getException());
        return result;
    }

    @Test
    void extractionParameters() {
        send("textExtraction", "extractionOutputs=plain_text,json&extractionMode=high_quality&ocrMode=forced"
                               + "&documentLanguages=en,fr&autoRotationCorrection=true&embeddedImages=enabled_text"
                               + "&outputDpi=150&outputTokens=true&kvpMode=invoice",
                Map.of(WatsonxAiConstants.OUTPUT_FILE_NAME, "invoice-out/"));

        JsonNode request = server.lastRequest(EXTRACTIONS).json();
        JsonNode parameters = request.get("parameters");
        assertEquals("plain_text", parameters.get("requested_outputs").get(0).asString());
        assertEquals("assembly", parameters.get("requested_outputs").get(1).asString());
        assertEquals("high_quality", parameters.get("mode").asString());
        assertEquals("forced", parameters.get("ocr_mode").asString());
        assertEquals("en", parameters.get("languages").get(0).asString());
        assertEquals("fr", parameters.get("languages").get(1).asString());
        assertTrue(parameters.get("auto_rotation_correction").asBoolean());
        assertEquals("enabled_text", parameters.get("create_embedded_images").asString());
        assertEquals(150, parameters.get("output_dpi").asInt());
        assertTrue(parameters.get("output_tokens").asBoolean());
        assertEquals("invoice", parameters.get("kvp_mode").asString());
        assertEquals("invoice-out/", request.get("results_reference").get("location").get("file_name").asString());
        assertEquals("folder/invoice.pdf", request.get("document_reference").get("location").get("file_name").asString());
    }

    @Test
    void extractionWithoutOptionsKeepsTheMarkdownDefault() {
        send("textExtraction", "", Map.of());

        JsonNode parameters = server.lastRequest(EXTRACTIONS).json().get("parameters");
        assertEquals(1, parameters.get("requested_outputs").size());
        assertEquals("md", parameters.get("requested_outputs").get(0).asString());
        assertFalse(parameters.has("mode"));
    }

    @Test
    void cleanupFlagsDoNotApplyToAsynchronousStarts() {
        // the SDK refuses them when it does not wait for the result: an asynchronous start ignores them
        send("textExtraction", "removeUploadedFile=true&removeOutputFile=true&ocrMode=disabled", Map.of());

        assertEquals("disabled", server.lastRequest(EXTRACTIONS).json().get("parameters").get("ocr_mode").asString());
    }

    @Test
    void classificationParameters() {
        send("textClassification", "classificationMode=binary&ocrMode=enabled&documentLanguages=de"
                                   + "&autoRotationCorrection=false",
                Map.of());

        JsonNode parameters = server.lastRequest(CLASSIFICATIONS).json().get("parameters");
        assertEquals("binary", parameters.get("classification_mode").asString());
        assertEquals("enabled", parameters.get("ocr_mode").asString());
        assertEquals("de", parameters.get("languages").get(0).asString());
        assertFalse(parameters.get("auto_rotation_correction").asBoolean());
    }
}
