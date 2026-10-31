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
package org.apache.camel.component.as2.api.entity;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.component.as2.api.io.AS2SessionInputBuffer;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The text of a body part ends where the boundary delimiter begins: the line break before the delimiter line belongs to
 * the boundary (RFC 2046, 5.1.1), whether it is CRLF or - as openssl writes it without -crlfeol - a bare LF. A capture
 * of the part as received ends at the same byte, so that what a signature is verified over and what is parsed are the
 * same part.
 */
class EntityParserBodyPartTextTest {

    private static final String BOUNDARY = "boundary-7";

    private static AS2SessionInputBuffer buffer() {
        return new AS2SessionInputBuffer(new BasicHttpTransportMetrics(), 1024);
    }

    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.US_ASCII));
    }

    @ParameterizedTest
    @ValueSource(strings = { "\r\n", "\n" })
    void onlyTheLineBreakBeforeTheDelimiterIsRemoved(String lineEnding) throws Exception {
        // the part ends in a quote, which must survive - with a bare LF before the delimiter as well
        String part = "UNA:+.? 'UNB+UNOC:3'" + lineEnding + "UNZ+1+7'";
        String text = EntityParser.parseBodyPartText(buffer(), stream(part + lineEnding + "--" + BOUNDARY + lineEnding),
                BOUNDARY);

        assertEquals(part, text);
    }

    @Test
    void anEmptyPartIsEmpty() throws Exception {
        String text = EntityParser.parseBodyPartText(buffer(), stream("--" + BOUNDARY + "\r\n"), BOUNDARY);

        assertEquals("", text);
    }

    @Test
    void aPartWithoutDelimiterKeepsItsLastLineBreak() throws Exception {
        String text = EntityParser.parseBodyPartText(buffer(), stream("last line\r\n"), BOUNDARY);

        assertEquals("last line\r\n", text);
    }

    @ParameterizedTest
    @ValueSource(strings = { "\r\n", "\n" })
    void theCaptureOfThePartAsReceivedEndsWhereTheTextEnds(String lineEnding) throws Exception {
        String part = "first" + lineEnding + "second";
        AS2SessionInputBuffer inbuffer = buffer();
        InputStream is = stream(part + lineEnding + "--" + BOUNDARY + lineEnding + "next part");

        inbuffer.startRawCapture();
        String text = EntityParser.parseBodyPartText(inbuffer, is, BOUNDARY);
        byte[] captured = inbuffer.stopRawCapture();

        assertEquals(part, text);
        assertArrayEquals(part.getBytes(StandardCharsets.US_ASCII), captured,
                "the bytes a signature is verified over are the part the text was parsed from");
    }
}
