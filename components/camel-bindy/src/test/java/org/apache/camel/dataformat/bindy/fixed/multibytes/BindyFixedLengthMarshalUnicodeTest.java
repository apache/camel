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
package org.apache.camel.dataformat.bindy.fixed.multibytes;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.FixedLengthRecord;
import org.apache.camel.dataformat.bindy.fixed.BindyFixedLengthDataFormat;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unmarshal counts the field lengths in code points (or graphemes with countGrapheme=true). Marshal must pad and clip
 * with the same count, so that a record with characters outside the BMP (or combining characters) is read back.
 */
public class BindyFixedLengthMarshalUnicodeTest extends CamelTestSupport {

    // U+1F600 (one code point, two UTF-16 chars)
    private static final String EMOJI = "\uD83D\uDE00";
    // U+20000 CJK Extension B (one code point, two UTF-16 chars)
    private static final String CJK_EXT_B = "\uD840\uDC00";
    // e + combining acute accent (one grapheme, two code points)
    private static final String E_ACUTE = "e\u0301";

    @Test
    public void testMarshalPadsByCodePoints() {
        String text = template.requestBody("direct:marshal", record(new CodePointRecord(), "ok" + EMOJI, "x", "y"),
                String.class);
        assertEquals("  ok" + EMOJI + "   x  y\r\n", text);
    }

    @Test
    public void testMarshalUnmarshalCodePoints() {
        for (String value : new String[] { "ok" + EMOJI, CJK_EXT_B + CJK_EXT_B, "caf\u00E9" }) {
            CodePointRecord in = record(new CodePointRecord(), value, "x", "y");
            String text = template.requestBody("direct:marshal", in, String.class);
            CodePointRecord out = template.requestBody("direct:unmarshal", text, CodePointRecord.class);
            assertEquals(value, out.first, text);
            assertEquals("x", out.second, text);
            assertEquals("y", out.third, text);
        }
    }

    @Test
    public void testMarshalClipsByCodePoints() {
        ClipRecord in = new ClipRecord();
        in.first = "abcd" + EMOJI + "f";
        in.second = "x";
        String text = template.requestBody("direct:marshalClip", in, String.class);
        assertEquals("abcd" + EMOJI + "x\r\n", text);
    }

    @Test
    public void testMarshalUnmarshalGraphemes() {
        GraphemeRecord in = record(new GraphemeRecord(), "caf" + E_ACUTE, "x", "y");
        String text = template.requestBody("direct:marshalGrapheme", in, String.class);
        GraphemeRecord out = template.requestBody("direct:unmarshalGrapheme", text, GraphemeRecord.class);
        assertEquals("caf" + E_ACUTE, out.first, text);
        assertEquals("x", out.second, text);
        assertEquals("y", out.third, text);
    }

    private static CodePointRecord record(CodePointRecord record, String first, String second, String third) {
        record.first = first;
        record.second = second;
        record.third = third;
        return record;
    }

    private static GraphemeRecord record(GraphemeRecord record, String first, String second, String third) {
        record.first = first;
        record.second = second;
        record.third = third;
        return record;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                BindyFixedLengthDataFormat codePoints = new BindyFixedLengthDataFormat(CodePointRecord.class);
                from("direct:marshal").marshal(codePoints);
                from("direct:unmarshal").unmarshal(codePoints);

                from("direct:marshalClip").marshal(new BindyFixedLengthDataFormat(ClipRecord.class));

                BindyFixedLengthDataFormat graphemes = new BindyFixedLengthDataFormat(GraphemeRecord.class);
                from("direct:marshalGrapheme").marshal(graphemes);
                from("direct:unmarshalGrapheme").unmarshal(graphemes);
            }
        };
    }

    @FixedLengthRecord(length = 12)
    public static class CodePointRecord {

        @DataField(pos = 1, length = 5, trim = true)
        String first;

        @DataField(pos = 6, length = 4, trim = true)
        String second;

        @DataField(pos = 10, length = 3, trim = true)
        String third;
    }

    @FixedLengthRecord(countGrapheme = true)
    public static class GraphemeRecord {

        @DataField(pos = 1, length = 5, trim = true)
        String first;

        @DataField(pos = 6, length = 4, trim = true)
        String second;

        @DataField(pos = 10, length = 3, trim = true)
        String third;
    }

    @FixedLengthRecord
    public static class ClipRecord {

        @DataField(pos = 1, length = 5, clip = true)
        String first;

        @DataField(pos = 6, length = 1)
        String second;
    }
}
