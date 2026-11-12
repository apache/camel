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
package org.apache.camel.component.docling;

import java.util.List;

import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the OCR of a single message, on both paths.
 * <p>
 * The component documentation has a "Using headers to control processing" section that sets
 * {@code CamelDoclingEnableOCR} and {@code CamelDoclingOCRLanguage} per message, next to {@code CamelDoclingOperation},
 * which is honoured. The two OCR headers were declared with {@code @Metadata} and documented but never read, so the
 * example had no effect: every message was OCRed the way the endpoint was configured. Both paths now read them, the
 * docling CLI command and the docling-serve request.
 */
class DoclingOcrHeadersTest extends CamelTestSupport {

    private static final String SOURCE = "https://example.com/report.pdf";
    private static final String DEFAULT = "docling:convert?operation=CONVERT_TO_MARKDOWN";
    private static final String OCR_OFF = "docling:convert?operation=CONVERT_TO_MARKDOWN&enableOCR=false&ocrLanguage=de";
    private static final String OCR_ON_IN_FRENCH
            = "docling:convert?operation=CONVERT_TO_MARKDOWN&doOcr=true&ocrLanguage=fr";

    // --- the docling CLI command

    @Test
    void withoutHeadersTheEndpointConfigurationIsUsed() throws Exception {
        List<String> command = commandFor(DEFAULT, null, null);

        assertThat(command).contains("--ocr-lang", "en").doesNotContain("--no-ocr");
    }

    @Test
    void enableOcrHeaderTurnsOcrOffForTheMessage() throws Exception {
        // the endpoint has OCR on, this one message must not be OCRed, and no language may leak into the command
        List<String> command = commandFor(DEFAULT, false, null);

        assertThat(command).contains("--no-ocr").doesNotContain("--ocr-lang");
    }

    @Test
    void ocrLanguageHeaderSelectsTheLanguageForTheMessage() throws Exception {
        List<String> command = commandFor(DEFAULT, null, "es");

        assertThat(command).contains("--ocr-lang", "es");
    }

    @Test
    void headersWinOverTheEndpointConfiguration() throws Exception {
        // the endpoint has OCR off and German, the message asks for Spanish OCR
        List<String> command = commandFor(OCR_OFF, true, "es");

        assertThat(command).contains("--ocr-lang", "es").doesNotContain("--no-ocr");
    }

    // --- the docling-serve request

    @Test
    void withoutHeadersTheServeRequestKeepsTheServerDefaults() throws Exception {
        ConvertDocumentOptions options = serveOptions(DEFAULT, null, null);

        assertThat(options.getDoOcr()).isNull();
        assertThat(options.getOcrLang()).isNullOrEmpty();
    }

    @Test
    void enableOcrHeaderTurnsOcrOffForTheServeRequest() throws Exception {
        ConvertDocumentOptions options = serveOptions(DEFAULT, false, null);

        assertThat(options.getDoOcr()).isFalse();
        assertThat(options.getOcrLang()).isNullOrEmpty();
    }

    @Test
    void ocrLanguageHeaderSelectsTheLanguageOfTheServeRequest() throws Exception {
        ConvertDocumentOptions options = serveOptions(DEFAULT, null, "es");

        assertThat(options.getDoOcr()).isTrue();
        assertThat(options.getOcrLang()).containsExactly("es");
    }

    @Test
    void theServeRequestFallsBackToTheLanguageOfTheEndpoint() throws Exception {
        // the endpoint has OCR off and German, the message only asks for OCR
        ConvertDocumentOptions options = serveOptions(OCR_OFF, true, null);

        assertThat(options.getDoOcr()).isTrue();
        assertThat(options.getOcrLang()).containsExactly("de");
    }

    @Test
    void theDoOcrOptionOfTheEndpointIsStillSent() throws Exception {
        ConvertDocumentOptions options = serveOptions(OCR_ON_IN_FRENCH, null, null);

        assertThat(options.getDoOcr()).isTrue();
        assertThat(options.getOcrLang()).containsExactly("fr");
    }

    @Test
    void aLanguageHeaderDoesNotTurnOnOcrThatTheEndpointDisabledOnTheCli() throws Exception {
        // the endpoint has OCR off, the message only names a language: OCR must stay off, as the upgrade guide says
        List<String> command = commandFor(OCR_OFF, null, "es");

        assertThat(command).contains("--no-ocr").doesNotContain("--ocr-lang");
    }

    @Test
    void aLanguageHeaderDoesNotTurnOnOcrThatTheEndpointDisabledInTheServeRequest() throws Exception {
        ConvertDocumentOptions options = serveOptions(OCR_OFF, null, "es");

        assertThat(options.getDoOcr()).isFalse();
        assertThat(options.getOcrLang()).isNullOrEmpty();
    }

    // --- the language is docling's own identifier, so it is passed on as it is

    @Test
    void doclingLanguageIdentifiersReachTheCli() throws Exception {
        // docling also takes BCP-47 tags with a delimiter, script names and Tesseract combinations
        assertThat(commandFor(DEFAULT, null, "iso:zh-Hant")).contains("--ocr-lang", "iso:zh-Hant");
        assertThat(commandFor(DEFAULT, null, "script/Latin")).contains("--ocr-lang", "script/Latin");
        assertThat(commandFor(DEFAULT, null, "eng+deu")).contains("--ocr-lang", "eng+deu");
    }

    @Test
    void doclingLanguageIdentifiersReachTheServeRequest() throws Exception {
        assertThat(serveOptions(DEFAULT, null, "iso:zh-Hant").getOcrLang()).containsExactly("iso:zh-Hant");
    }

    @Test
    void aLanguageListWithASpaceReachesTheCli() throws Exception {
        assertThat(commandFor(DEFAULT, null, "en, de")).contains("--ocr-lang", "en, de");
    }

    private List<String> commandFor(String endpointUri, Boolean enableOcr, String ocrLanguage) throws Exception {
        DoclingEndpoint endpoint = context.getEndpoint(endpointUri, DoclingEndpoint.class);
        DoclingProducer producer = (DoclingProducer) endpoint.createProducer();

        return producer.buildDoclingCommand("input.pdf", "markdown", exchangeFor(endpoint, enableOcr, ocrLanguage),
                "/tmp/managed");
    }

    private ConvertDocumentOptions serveOptions(String endpointUri, Boolean enableOcr, String ocrLanguage)
            throws Exception {
        DoclingEndpoint endpoint = context.getEndpoint(endpointUri, DoclingEndpoint.class);
        DoclingProducer producer = (DoclingProducer) endpoint.createProducer();

        Exchange exchange = exchangeFor(endpoint, enableOcr, ocrLanguage);

        return producer.buildConvertRequest(SOURCE, "markdown", producer.resolveOcrSettings(exchange)).getOptions();
    }

    private Exchange exchangeFor(DoclingEndpoint endpoint, Boolean enableOcr, String ocrLanguage) {
        Exchange exchange = endpoint.createExchange();
        if (enableOcr != null) {
            exchange.getIn().setHeader(DoclingHeaders.ENABLE_OCR, enableOcr);
        }
        if (ocrLanguage != null) {
            exchange.getIn().setHeader(DoclingHeaders.OCR_LANGUAGE, ocrLanguage);
        }

        return exchange;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:noop").to("mock:noop");
            }
        };
    }
}
