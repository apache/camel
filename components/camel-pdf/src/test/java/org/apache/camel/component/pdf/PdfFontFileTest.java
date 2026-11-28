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
package org.apache.camel.component.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PdfFontFileTest extends CamelTestSupport {

    // a TrueType font with Latin, Cyrillic and Greek characters (SIL Open Font License), which PDFBox ships
    private static final String FONT_RESOURCE = "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";
    private static final String FONT_FILE = "classpath:" + FONT_RESOURCE;
    private static final String TEXT = "Hello Привет мир Γειά σου κόσμε";

    @TempDir
    Path tempDir;

    @Test
    public void testStandardFontCannotWriteCyrillic() {
        Exception e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("pdf:create", TEXT));
        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertThat(cause.getMessage(), containsString("is not available in the font Helvetica"));
        assertThat(cause.getMessage(), containsString("fontFile"));
    }

    @Test
    public void testCreateWithFontFile() throws Exception {
        Object body = template.requestBody("pdf:create?fontFile=" + FONT_FILE, TEXT + "\nsecond line");
        try (PDDocument doc = load(body)) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text, containsString(TEXT));
            assertThat(text, containsString("second line"));
            assertEmbeddedFontFile(doc);
        }
    }

    @Test
    public void testCreateWithFontFileFromFile() throws Exception {
        Path fontFile = tempDir.resolve("my-font.ttf");
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(FONT_RESOURCE)) {
            Files.copy(is, fontFile);
        }
        Object body = template.requestBody("pdf:create?fontFile=file:" + fontFile.toAbsolutePath(), TEXT);
        try (PDDocument doc = load(body)) {
            assertThat(new PDFTextStripper().getText(doc), containsString(TEXT));
            assertEmbeddedFontFile(doc);
        }
    }

    @Test
    public void testAutoFormattingWithFontFile() throws Exception {
        // many words, so the text is wrapped over many lines and pages, measured with the font file
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1500; i++) {
            sb.append("Привет").append(i).append(' ');
        }
        Object body = template.requestBody(
                "pdf:create?textProcessingFactory=autoFormatting&fontSize=12&fontFile=" + FONT_FILE, sb.toString());
        try (PDDocument doc = load(body)) {
            assertTrue(doc.getNumberOfPages() > 1, "Expected the text on more than one page");
            String text = new PDFTextStripper().getText(doc);
            assertThat(text, containsString("Привет0 "));
            assertThat(text, containsString("Привет1499"));
            // every page uses the same embedded font
            assertEmbeddedFontFile(doc);
        }
    }

    @Test
    public void testAppendWithFontFile() throws Exception {
        try (PDDocument document = PDFUtil.textToPDF("first page")) {
            Object body = template.requestBodyAndHeader("pdf:append?fontFile=" + FONT_FILE, TEXT,
                    PdfHeaderConstants.PDF_DOCUMENT_HEADER_NAME, document);
            try (PDDocument doc = load(body)) {
                assertEquals(2, doc.getNumberOfPages());
                String text = new PDFTextStripper().getText(doc);
                assertThat(text, containsString("first page"));
                assertThat(text, containsString(TEXT));
            }
        }
    }

    @Test
    public void testFontFileWithoutTheCharacter() {
        // the font has no Chinese characters, which fails rather than leaving them out
        Exception e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("pdf:create?fontFile=" + FONT_FILE, "Hello 你好"));
        assertThat(e.getCause().getMessage(), containsString("4F60"));
        assertThat(e.getCause().getMessage(), not(containsString("use the fontFile option")));
    }

    @Test
    public void testUnknownFontFile() {
        Exception e = assertThrows(Exception.class,
                () -> context.getEndpoint("pdf:create?fontFile=classpath:no-such-font.ttf"));
        assertThat(messages(e), containsString("no-such-font.ttf"));
    }

    @Test
    public void testFontFileNotTrueType() throws Exception {
        Path fontFile = tempDir.resolve("not-a-font.ttf");
        Files.writeString(fontFile, "this is not a font");
        Exception e = assertThrows(Exception.class,
                () -> context.getEndpoint("pdf:create?fontFile=file:" + fontFile.toAbsolutePath()));
        assertThat(messages(e), containsString("Cannot use the fontFile"));
    }

    private static String messages(Throwable e) {
        // the messages of the exception and its causes
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            sb.append(t.getMessage()).append('\n');
        }
        return sb.toString();
    }

    private static PDDocument load(Object body) throws IOException {
        return Loader.loadPDF(((ByteArrayOutputStream) body).toByteArray());
    }

    private static void assertEmbeddedFontFile(PDDocument doc) throws IOException {
        Set<String> fontNames = new HashSet<>();
        for (PDPage page : doc.getPages()) {
            PDResources resources = page.getResources();
            for (COSName name : resources.getFontNames()) {
                PDFont font = resources.getFont(name);
                assertInstanceOf(PDType0Font.class, font);
                assertTrue(font.isEmbedded(), "Expected the font file embedded in the document");
                fontNames.add(font.getName());
            }
        }
        // the font is embedded once, as a subset with a name such as ABCDEF+LiberationSans
        assertEquals(1, fontNames.size(), "Expected one embedded font, but got " + fontNames);
        assertThat(fontNames.iterator().next(), containsString("LiberationSans"));
    }
}
