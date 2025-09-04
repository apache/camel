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

package org.apache.camel.component.pdf.converter;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.apache.camel.Converter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

@Converter(generateLoader = true)
public class PdfConverter {

    @Converter
    public PDDocument convertToPDF(byte[] bytes) throws Exception {
        return Loader.loadPDF(bytes);
    }

    @Converter
    public PDDocument toPDDocument(InputStream stream) throws Exception {
        final byte[] bytes = stream.readAllBytes();
        return convertToPDF(bytes);
    }

    @Converter
    public PDDocument convertToPDF(File file) throws Exception {
        try (InputStream is = new FileInputStream(file)) {
            return toPDDocument(is);
        }
    }
}
