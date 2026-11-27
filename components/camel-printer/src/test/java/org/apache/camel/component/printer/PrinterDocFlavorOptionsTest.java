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
package org.apache.camel.component.printer;

import java.awt.print.Printable;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.attribute.PrintRequestAttributeSet;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The defaults of the flavor and mimeType options, the doc flavor they select, and the print data of the
 * SERVICE_FORMATTED doc flavors, on a mocked PrintService (no print service lookup, so nothing reaches a real printer).
 */
class PrinterDocFlavorOptionsTest {

    private CamelContext context;
    private PrintService printService;
    private DocPrintJob printJob;
    private PrinterProducer producer;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        context.start();

        printService = mock(PrintService.class);
        when(printService.getName()).thenReturn("MockedPrinter");
        when(printService.isDocFlavorSupported(any(DocFlavor.class))).thenReturn(Boolean.TRUE);
        printJob = mock(DocPrintJob.class);
        when(printService.createPrintJob()).thenReturn(printJob);
    }

    @AfterEach
    void tearDown() {
        if (producer != null) {
            producer.stop();
        }
        context.stop();
    }

    private PrinterEndpoint startProducer(String uri) {
        PrinterEndpoint endpoint = context.getEndpoint(uri, PrinterEndpoint.class);
        producer = new PrinterProducer(endpoint, endpoint.getConfig());
        producer.setPrintService(printService);
        producer.start();
        return endpoint;
    }

    private Exchange exchange(Object body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);
        return exchange;
    }

    @Test
    void sendToPrinterFalseWithoutMimeTypeWritesTheDocument() throws Exception {
        PrinterEndpoint endpoint = startProducer("lpr://localhost/MockedPrinter?sendToPrinter=false");

        // failed with a NullPointerException, as the mime type of the file name was null
        producer.process(exchange("Hello Printer".getBytes(StandardCharsets.UTF_8)));

        assertEquals("AUTOSENSE", endpoint.getConfig().getMimeType());
        assertEquals("DocFlavor.BYTE_ARRAY", endpoint.getConfig().getFlavor());
        assertEquals(DocFlavor.BYTE_ARRAY.AUTOSENSE, endpoint.getConfig().getDocFlavor());
    }

    static Stream<Arguments> unknownOptions() {
        return Stream.of(
                Arguments.of("mimeType=PDFF", "Unknown mimeType: PDFF"),
                Arguments.of("flavor=DocFlavor.BYTES&mimeType=PDF", "Unknown flavor: DocFlavor.BYTES"),
                Arguments.of("mimeType=TEXT_PLAIN",
                        "The flavor DocFlavor.BYTE_ARRAY has no doc flavor for the mimeType TEXT_PLAIN"),
                Arguments.of("flavor=DocFlavor.STRING&mimeType=PDF",
                        "The flavor DocFlavor.STRING has no doc flavor for the mimeType PDF"));
    }

    @ParameterizedTest
    @MethodSource("unknownOptions")
    void flavorAndMimeTypeWithoutDocFlavorFailTheEndpoint(String options, String message) {
        // printed as DocFlavor.BYTE_ARRAY.AUTOSENSE without an error
        ResolveEndpointFailedException e = assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("lpr://localhost/MockedPrinter?" + options));

        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(cause.getMessage().startsWith(message), cause.getMessage());
    }

    static Stream<Arguments> htmlMimeTypes() {
        return Stream.of(
                Arguments.of("mimeType=TEXT_HTML_UTF_8", DocFlavor.BYTE_ARRAY.TEXT_HTML_UTF_8),
                Arguments.of("flavor=DocFlavor.STRING&mimeType=TEXT_HTML", DocFlavor.STRING.TEXT_HTML),
                Arguments.of("mimeType=TEXT_PLAIN_UTF_8", DocFlavor.BYTE_ARRAY.TEXT_PLAIN_UTF_8),
                Arguments.of("flavor=DocFlavor.STRING&mimeType=TEXT_PLAIN", DocFlavor.STRING.TEXT_PLAIN));
    }

    @ParameterizedTest
    @MethodSource("htmlMimeTypes")
    void htmlMimeTypeSelectsTheHtmlDocFlavor(String options, DocFlavor expected) {
        // TEXT_HTML_UTF_8 and TEXT_HTML selected the text/plain doc flavor
        PrinterEndpoint endpoint = context.getEndpoint("lpr://localhost/MockedPrinter?" + options, PrinterEndpoint.class);

        assertEquals(expected, endpoint.getConfig().getDocFlavor());
    }

    @Test
    void printableBodyIsThePrintDataOfThePrintableDocFlavor() throws Exception {
        startProducer("lpr://localhost/MockedPrinter?mimeType=PRINTABLE");
        Printable printable = (graphics, pageFormat, pageIndex) -> Printable.NO_SUCH_PAGE;

        // failed with an InvalidPayloadException, as the body was read as an InputStream
        producer.process(exchange(printable));

        ArgumentCaptor<Doc> doc = ArgumentCaptor.forClass(Doc.class);
        verify(printJob).print(doc.capture(), any(PrintRequestAttributeSet.class));
        assertEquals(DocFlavor.SERVICE_FORMATTED.PRINTABLE, doc.getValue().getDocFlavor());
        assertSame(printable, doc.getValue().getPrintData());
    }
}
