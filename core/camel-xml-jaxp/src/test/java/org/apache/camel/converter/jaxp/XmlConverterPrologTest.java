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

import org.apache.camel.TypeConversionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that {@link XmlConverter#toDOMDocument(byte[], org.apache.camel.Exchange)} and
 * {@link XmlConverter#toDOMDocument(java.io.InputStream, org.apache.camel.Exchange)} throw
 * {@link TypeConversionException} with a descriptive message for non-XML payloads rather than propagating a raw
 * {@code SAXParseException: Content is not allowed in prolog}.
 *
 * The StreamCache overload delegates to toDOMDocument(InputStream, Exchange), so the InputStream tests cover that path
 * as well.
 */
class XmlConverterPrologTest {

    // ---- byte[] overload ----

    @Test
    void toDOMDocument_emptyByteArrayThrowsTypeConversionException() {
        XmlConverter converter = new XmlConverter();
        assertThatThrownBy(() -> converter.toDOMDocument(new byte[0], null))
                .isInstanceOf(TypeConversionException.class)
                .hasMessageContaining("not valid XML");
    }

    @Test
    void toDOMDocument_jsonBodyThrowsTypeConversionException() {
        XmlConverter converter = new XmlConverter();
        byte[] json = "{\"status\":\"error\"}".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> converter.toDOMDocument(json, null))
                .isInstanceOf(TypeConversionException.class)
                .hasMessageContaining("not valid XML")
                .hasMessageContaining("possible causes");
    }

    @Test
    void toDOMDocument_plainTextThrowsTypeConversionException() {
        XmlConverter converter = new XmlConverter();
        byte[] text = "HTTP/1.1 503 Service Unavailable".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> converter.toDOMDocument(text, null))
                .isInstanceOf(TypeConversionException.class)
                .hasMessageContaining("not valid XML");
    }

    // ---- InputStream overload (same code path as StreamCache) ----

    @Test
    void toDOMDocument_inputStreamJsonBodyThrowsTypeConversionException() {
        XmlConverter converter = new XmlConverter();
        byte[] json = "{\"status\":\"error\"}".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> converter.toDOMDocument(new ByteArrayInputStream(json), null))
                .isInstanceOf(TypeConversionException.class)
                .hasMessageContaining("not valid XML")
                .hasMessageContaining("possible causes");
    }

    @Test
    void toDOMDocument_inputStreamPlainTextThrowsTypeConversionException() {
        XmlConverter converter = new XmlConverter();
        byte[] text = "HTTP/1.1 503 Service Unavailable".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> converter.toDOMDocument(new ByteArrayInputStream(text), null))
                .isInstanceOf(TypeConversionException.class)
                .hasMessageContaining("not valid XML");
    }
}
