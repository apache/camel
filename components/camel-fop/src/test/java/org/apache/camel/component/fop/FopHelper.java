/**
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
package org.apache.camel.component.fop;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;

public final class FopHelper {
    private FopHelper() {
    }

    public static String extractTextFrom(PDDocument document) throws IOException {
        Writer output = new StringWriter();
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.writeText(document, output);
        return output.toString().trim();
    }

    public static String getDocumentMetadataValue(PDDocument document, COSName name) {
        PDDocumentInformation info = document.getDocumentInformation();
        return info.getCOSObject().getString(name);
    }

    public static String decorateTextWithXSLFO(String text) {
        return "<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\">\n"
                + "  <fo:layout-master-set>\n"
                + "    <fo:simple-page-master master-name=\"only\">\n"
                + "      <fo:region-body region-name=\"xsl-region-body\" margin=\"0.7in\"  padding=\"0\" />\n"
                + "      <fo:region-before region-name=\"xsl-region-before\" extent=\"0.7in\" />\n"
                + "        <fo:region-after region-name=\"xsl-region-after\" extent=\"0.7in\" />\n"
                + "      </fo:simple-page-master>\n"
                + "    </fo:layout-master-set>\n"
                + "    <fo:page-sequence master-reference=\"only\">\n"
                + "      <fo:flow flow-name=\"xsl-region-body\">\n"
                + "      <fo:block>" + text + "</fo:block>\n"
                + "    </fo:flow>\n"
                + "  </fo:page-sequence>\n"
                + "</fo:root>";
    }
}
