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
package org.apache.camel.converter.jaxp;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the prolog guard in {@link XmlConverter}.
 *
 * <p>
 * {@link XmlConverter#looksLikeXml(byte[])} unit tests confirm the helper correctly identifies XML vs non-XML content.
 * Integration tests via {@link XmlConverter#toDOMDocument(byte[], org.apache.camel.Exchange)} confirm that non-XML
 * payloads return {@code null} (allowing the type-converter framework to fall through gracefully) rather than
 * triggering expensive DOM construction and a {@code SAXParseException: Content is not allowed in prolog}.
 */
class XmlConverterPrologTest {

    // ---- looksLikeXml unit tests ----

    @Test
    void testLooksLikeXmlNullReturnsFalse() {
        assertThat(XmlConverter.looksLikeXml(null)).isFalse();
    }

    @Test
    void testLooksLikeXmlEmptyReturnsFalse() {
        assertThat(XmlConverter.looksLikeXml(new byte[0])).isFalse();
    }

    @Test
    void testLooksLikeXmlJsonBodyReturnsFalse() {
        assertThat(XmlConverter.looksLikeXml("{\"error\":\"bad request\"}".getBytes(StandardCharsets.UTF_8))).isFalse();
    }

    @Test
    void testLooksLikeXmlPlainTextReturnsFalse() {
        assertThat(XmlConverter.looksLikeXml("some plain text".getBytes(StandardCharsets.UTF_8))).isFalse();
    }

    @Test
    void testLooksLikeXmlHttpStatusLineReturnsFalse() {
        assertThat(XmlConverter.looksLikeXml(
                "HTTP/1.1 500 Internal Server Error".getBytes(StandardCharsets.UTF_8))).isFalse();
    }

    @Test
    void testLooksLikeXmlUtf8BomOnlyReturnsFalse() {
        byte[] bomOnly = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        assertThat(XmlConverter.looksLikeXml(bomOnly)).isFalse();
    }

    @Test
    void testLooksLikeXmlUtf8BomFollowedByJsonReturnsFalse() {
        byte[] bom = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] body = "{\"k\":\"v\"}".getBytes(StandardCharsets.UTF_8);
        byte[] data = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, data, 0, bom.length);
        System.arraycopy(body, 0, data, bom.length, body.length);
        assertThat(XmlConverter.looksLikeXml(data)).isFalse();
    }

    @Test
    void testLooksLikeXmlValidXmlDeclarationReturnsTrue() {
        assertThat(XmlConverter.looksLikeXml(
                "<?xml version=\"1.0\"?><root/>".getBytes(StandardCharsets.UTF_8))).isTrue();
    }

    @Test
    void testLooksLikeXmlValidXmlNoDeclarationReturnsTrue() {
        assertThat(XmlConverter.looksLikeXml("<root><child/></root>".getBytes(StandardCharsets.UTF_8))).isTrue();
    }

    @Test
    void testLooksLikeXmlLeadingWhitespaceBeforeTagReturnsTrue() {
        assertThat(XmlConverter.looksLikeXml("  \t\r\n<root/>".getBytes(StandardCharsets.UTF_8))).isTrue();
    }

    @Test
    void testLooksLikeXmlUtf8BomFollowedByXmlReturnsTrue() {
        byte[] bom = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] body = "<root/>".getBytes(StandardCharsets.UTF_8);
        byte[] data = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, data, 0, bom.length);
        System.arraycopy(body, 0, data, bom.length, body.length);
        assertThat(XmlConverter.looksLikeXml(data)).isTrue();
    }

    @Test
    void testLooksLikeXmlUtf16BeBomReturnsTrue() {
        byte[] data = { (byte) 0xFE, (byte) 0xFF, 0x00, '<' };
        assertThat(XmlConverter.looksLikeXml(data)).isTrue();
    }

    @Test
    void testLooksLikeXmlUtf16LeBomReturnsTrue() {
        byte[] data = { (byte) 0xFF, (byte) 0xFE, '<', 0x00 };
        assertThat(XmlConverter.looksLikeXml(data)).isTrue();
    }

    // ---- toDOMDocument prolog-guard integration tests ----

    @Test
    void testToDOMDocumentEmptyByteArrayReturnsNull() throws Exception {
        XmlConverter converter = new XmlConverter();
        assertThat(converter.toDOMDocument(new byte[0], null)).isNull();
    }

    @Test
    void testToDOMDocumentJsonBodyReturnsNull() throws Exception {
        XmlConverter converter = new XmlConverter();
        byte[] json = "{\"status\":\"error\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(converter.toDOMDocument(json, null)).isNull();
    }

    @Test
    void testToDOMDocumentPlainTextReturnsNull() throws Exception {
        XmlConverter converter = new XmlConverter();
        byte[] text = "HTTP/1.1 503 Service Unavailable".getBytes(StandardCharsets.UTF_8);
        assertThat(converter.toDOMDocument(text, null)).isNull();
    }

    @Test
    void testToDOMDocumentInputStreamJsonBodyThrows() {
        XmlConverter converter = new XmlConverter();
        byte[] json = "{\"status\":\"error\"}".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> converter.toDOMDocument(new ByteArrayInputStream(json), null))
                .isInstanceOf(Exception.class);
    }
}
