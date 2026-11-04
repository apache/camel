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

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.attribute.PrintRequestAttributeSet;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The doc flavor check of the lpr producer, on a mocked PrintService (no print service lookup, so nothing reaches a
 * real printer).
 */
public class PrinterProducerDocFlavorTest {

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
        printJob = mock(DocPrintJob.class);
        when(printService.createPrintJob()).thenReturn(printJob);
    }

    @AfterEach
    public void tearDown() {
        if (producer != null) {
            producer.stop();
        }
        context.stop();
    }

    private void startProducer(String uri) {
        PrinterEndpoint endpoint = context.getEndpoint(uri, PrinterEndpoint.class);
        producer = new PrinterProducer(endpoint, endpoint.getConfig());
        producer.setPrintService(printService);
        producer.start();
    }

    private Exchange exchange(String body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);
        return exchange;
    }

    @Test
    public void unsupportedFlavorFails() throws Exception {
        when(printService.isDocFlavorSupported(any(DocFlavor.class))).thenReturn(Boolean.FALSE);
        startProducer("lpr://localhost/MockedPrinter?flavor=DocFlavor.INPUT_STREAM&mimeType=PDF");

        PrintException e = assertThrows(PrintException.class, () -> producer.process(exchange("Hello Printer")));

        assertTrue(e.getMessage().contains("MockedPrinter"), e.getMessage());
        assertTrue(e.getMessage().contains(DocFlavor.INPUT_STREAM.PDF.toString()), e.getMessage());
        verify(printService).isDocFlavorSupported(DocFlavor.INPUT_STREAM.PDF);
        verify(printService, never()).createPrintJob();
    }

    @Test
    public void unsupportedFlavorFailsWithoutSendToPrinter() throws Exception {
        when(printService.isDocFlavorSupported(any(DocFlavor.class))).thenReturn(Boolean.FALSE);
        startProducer("lpr://localhost/MockedPrinter?sendToPrinter=false");

        PrintException e = assertThrows(PrintException.class, () -> producer.process(exchange("Hello Printer")));

        assertTrue(e.getMessage().contains(DocFlavor.BYTE_ARRAY.AUTOSENSE.toString()), e.getMessage());
        verify(printService, never()).createPrintJob();
    }

    @Test
    public void unsupportedFlavorFailsTheRoute() throws Exception {
        when(printService.isDocFlavorSupported(any(DocFlavor.class))).thenReturn(Boolean.FALSE);
        startProducer("lpr://localhost/MockedPrinter");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").process(producer).to("mock:printed");
            }
        });
        MockEndpoint printed = context.getEndpoint("mock:printed", MockEndpoint.class);
        printed.expectedMessageCount(0);

        Exchange exchange = context.createProducerTemplate().send("direct:start", exchange("Hello Printer"));

        assertEquals(PrintException.class, exchange.getException().getClass());
        printed.assertIsSatisfied();
        verify(printService, never()).createPrintJob();
    }

    @Test
    public void supportedFlavorIsPrinted() throws Exception {
        when(printService.isDocFlavorSupported(DocFlavor.INPUT_STREAM.PDF)).thenReturn(Boolean.TRUE);
        startProducer("lpr://localhost/MockedPrinter?flavor=DocFlavor.INPUT_STREAM&mimeType=PDF");

        producer.process(exchange("Hello Printer"));

        verify(printJob).print(any(Doc.class), any(PrintRequestAttributeSet.class));
    }
}
