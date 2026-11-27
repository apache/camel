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
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.print.DocFlavor;
import javax.print.PrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * sendToPrinter=false writes the document to a file, also when the directory does not exist yet.
 */
class PrinterOperationsOutputDirectoryTest {

    @TempDir
    Path tempDir;

    @Test
    void missingOutputDirectoryIsCreated() throws Exception {
        PrintService printService = mock(PrintService.class);
        when(printService.getName()).thenReturn("MockedPrinter");
        PrinterOperations operations = new PrinterOperations(
                printService, DocFlavor.BYTE_ARRAY.AUTOSENSE, new HashPrintRequestAttributeSet());
        File missing = tempDir.resolve("work").resolve("target").toFile();
        operations.setOutputDirectory(missing);
        byte[] data = "Hello Printer".getBytes(StandardCharsets.UTF_8);

        // failed with a PrintException from the FileOutputStream when ./target did not exist
        operations.print(new PrintDocument(new ByteArrayInputStream(data), DocFlavor.BYTE_ARRAY.AUTOSENSE), false,
                "AUTOSENSE", "job");

        File[] written = missing.listFiles();
        assertEquals(1, written.length);
        assertArrayEquals(data, Files.readAllBytes(written[0].toPath()));
    }
}
