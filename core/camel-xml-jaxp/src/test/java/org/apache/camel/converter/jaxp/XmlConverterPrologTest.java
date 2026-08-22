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

import java.nio.charset.StandardCharsets;

import org.apache.camel.TypeConversionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the prolog-guard in {@link XmlConverter}.
 *
 * <p>
 * {@link XmlConverter#looksLikeXml(byte[])} unit tests confirm the helper correctly identifies XML vs non-XML content.
 * Integration tests via {@link XmlConverter#toDOMDocument(byte[], Exchange)} confirm that non-XML payloads throw
 * {@link TypeConversionException} (explicit, diagnosable failure) rather than propagating a
 * {@code SAXParseException: Content is not allowed in prolog} from deep inside the JDK parser.
 *
 * <p>
 * Real-world trigger: {@code ByteArrayInputStreamCache} carrying a non-XML HTTP response body (JSON error page, empty
 * body, BOM-only) routed through {@code CxfPayloadConverter} ->
 * {@code XmlConverter.toDOMDocument(StreamCache, Exchange)}.
 */
class XmlConverterPrologTest {

    // ---- looksLikeXml unit tests ----

    @Test
    void looksLikeXml_nullReturnsFalse() {
        assertFalse(XmlConverter.looksLikeXml(null));
    }

    @Test
    void looksLikeXml_emptyReturnsFalse() {
        assertFalse(XmlConverter.looksLikeXml(new byte[0]));
    }

    @Test
    void looksLikeXml_jsonBodyReturnsFalse() {
        assertFalse(XmlConverter.looksLikeXml("{\"error\":\"bad request\"}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void looksLikeXml_plainTextReturnsFalse() {
        assertFalse(XmlConverter.looksLikeXml("some plain text".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void looksLikeXml_httpStatusLineReturnsFalse() {
        // typical non-XML upstream response: HTTP status line or JSON error body
        assertFalse(XmlConverter.looksLikeXml(
                "HTTP/1.1 500 Internal Server Error".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void looksLikeXml_utf8BomOnlyReturnsFalse() {
        byte[] bomOnly = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        assertFalse(XmlConverter.looksLikeXml(bomOnly));
    }

    @Test
    void looksLikeXml_utf8BomFollowedByJsonReturnsFalse() {
        byte[] bom = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] body = "{\"k\":\"v\"}".getBytes(StandardCharsets.UTF_8);
        byte[] data = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, data, 0, bom.length);
        System.arraycopy(body, 0, data, bom.length, body.length);
        assertFalse(XmlConverter.looksLikeXml(data));
    }

    @Test
    void looksLikeXml_validXmlDeclarationReturnsTrue() {
        assertTrue(XmlConverter.looksLikeXml(
                "<?xml version=\"1.0\"?><root/>".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void looksLikeXml_validXmlNoDeclarationReturnsTrue() {
        assertTrue(XmlConverter.looksLikeXml("<root><child/></root>".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void looksLikeXml_leadingWhitespaceBeforeTagReturnsTrue() {
        assertTrue(XmlConverter.looksLikeXml("  \t\r\n<root/>".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void looksLikeXml_utf8BomFollowedByXmlReturnsTrue() {
        byte[] bom = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] body = "<root/>".getBytes(StandardCharsets.UTF_8);
        byte[] data = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, data, 0, bom.length);
        System.arraycopy(body, 0, data, bom.length, body.length);
        assertTrue(XmlConverter.looksLikeXml(data));
    }

    @Test
    void looksLikeXml_utf16BeBomReturnsTrue() {
        // UTF-16 BE BOM: FE FF — XML parsers handle this natively
        byte[] data = { (byte) 0xFE, (byte) 0xFF, 0x00, '<' };
        assertTrue(XmlConverter.looksLikeXml(data));
    }

    @Test
    void looksLikeXml_utf16LeBomReturnsTrue() {
        // UTF-16 LE BOM: FF FE — XML parsers handle this natively
        byte[] data = { (byte) 0xFF, (byte) 0xFE, '<', 0x00 };
        assertTrue(XmlConverter.looksLikeXml(data));
    }

    // ---- toDOMDocument prolog-guard integration tests ----

    @Test
    void toDOMDocument_emptyByteArrayThrowsTypeConversionException() {
        XmlConverter converter = new XmlConverter();
        assertThrows(TypeConversionException.class,
                () -> converter.toDOMDocument(new byte[0], null));
    }

    @Test
    void toDOMDocument_jsonBodyThrowsTypeConversionException() {
        XmlConverter converter = new XmlConverter();
        byte[] json = "{\"status\":\"error\"}".getBytes(StandardCharsets.UTF_8);
        assertThrows(TypeConversionException.class,
                () -> converter.toDOMDocument(json, null));
    }

    @Test
    void toDOMDocument_plainTextThrowsTypeConversionException() {
        XmlConverter converter = new XmlConverter();
        byte[] text = "HTTP/1.1 503 Service Unavailable".getBytes(StandardCharsets.UTF_8);
        assertThrows(TypeConversionException.class,
                () -> converter.toDOMDocument(text, null));
    }
}
