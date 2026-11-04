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

import java.io.IOException;
import java.io.InputStream;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * sendToPrinter=false writes the document to a file under ./target; a failing copy must close the streams and keep the
 * cause.
 */
public class PrinterOperationsWriteToFileTest {

    private static final IOException READ_FAILURE = new IOException("Simulated read failure");

    private static final class FailingInputStream extends InputStream {
        private boolean closed;

        @Override
        public int read() throws IOException {
            throw READ_FAILURE;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private final FailingInputStream in = new FailingInputStream();
    private PrintService printService;
    private PrinterOperations operations;
    private Doc doc;

    @BeforeEach
    public void setUp() throws Exception {
        printService = mock(PrintService.class);
        when(printService.getName()).thenReturn("MockedPrinter");
        operations = new PrinterOperations(
                printService, DocFlavor.INPUT_STREAM.AUTOSENSE, new HashPrintRequestAttributeSet());
        doc = mock(Doc.class);
        when(doc.getStreamForBytes()).thenReturn(in);
    }

    @Test
    public void failingCopyClosesTheDocumentStream() {
        assertThrows(PrintException.class, () -> operations.print(doc, false, "AUTOSENSE", "job"));

        assertTrue(in.closed, "The document stream should be closed when the copy fails");
        verify(printService, never()).createPrintJob();
    }

    @Test
    public void failingCopyKeepsTheCause() {
        PrintException e = assertThrows(PrintException.class, () -> operations.print(doc, false, "AUTOSENSE", "job"));

        assertTrue(e.getMessage().startsWith("Error writing Document to the target file"), e.getMessage());
        assertSame(READ_FAILURE, e.getCause());
    }
}
