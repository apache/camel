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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.attribute.PrintRequestAttributeSet;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The body of the lpr producer, printed on a mocked PrintService (no print service lookup, so nothing reaches a real
 * printer).
 */
public class PrinterProducerBodyTest {

    private CamelContext context;
    private PrintService printService;
    private DocPrintJob printJob;
    private PrinterProducer producer;

    @BeforeEach
    public void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.start();

        printService = mock(PrintService.class);
        when(printService.getName()).thenReturn("MockedPrinter");
        when(printService.isDocFlavorSupported(any(DocFlavor.class))).thenReturn(Boolean.TRUE);
        printJob = mock(DocPrintJob.class);
        when(printService.createPrintJob()).thenReturn(printJob);

        PrinterEndpoint endpoint = context.getEndpoint("lpr://localhost/MockedPrinter", PrinterEndpoint.class);
        producer = new PrinterProducer(endpoint, endpoint.getConfig());
        producer.setPrintService(printService);
        producer.start();
    }

    @AfterEach
    public void tearDown() {
        producer.stop();
        context.stop();
    }

    static Stream<Arguments> unsupportedBodies() {
        return Stream.of(
                Arguments.of(Map.of("name", "Camel")),
                Arguments.of(new Object()),
                Arguments.of(42),
                Arguments.of((Object) null));
    }

    @ParameterizedTest
    @MethodSource("unsupportedBodies")
    public void unsupportedBodyFails(Object body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);

        assertThrows(InvalidPayloadException.class, () -> producer.process(exchange));
        verify(printService, never()).createPrintJob();
    }

    @Test
    public void textBodyIsPrinted() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello Printer");

        producer.process(exchange);

        assertArrayEquals("Hello Printer".getBytes(StandardCharsets.UTF_8), printedBytes());
    }

    @Test
    public void bytesBodyIsPrinted() throws Exception {
        byte[] data = { 1, 2, 3, 4 };
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(data);

        producer.process(exchange);

        assertArrayEquals(data, printedBytes());
    }

    @Test
    public void streamBodyIsPrinted() throws Exception {
        byte[] data = { 5, 6, 7 };
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new ByteArrayInputStream(data));

        producer.process(exchange);

        assertArrayEquals(data, printedBytes());
    }

    @Test
    public void textBodyUsesTheCharsetOfTheExchange() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(Exchange.CHARSET_NAME, "ISO-8859-1");
        exchange.getIn().setBody("Grüße");

        producer.process(exchange);

        assertArrayEquals("Grüße".getBytes(StandardCharsets.ISO_8859_1), printedBytes());
    }

    private byte[] printedBytes() throws Exception {
        ArgumentCaptor<Doc> doc = ArgumentCaptor.forClass(Doc.class);
        verify(printJob).print(doc.capture(), any(PrintRequestAttributeSet.class));
        try (InputStream is = doc.getValue().getStreamForBytes()) {
            return is.readAllBytes();
        }
    }
}
