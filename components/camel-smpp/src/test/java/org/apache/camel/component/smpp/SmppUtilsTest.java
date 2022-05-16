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
package org.apache.camel.component.smpp;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.stream.Stream;

import org.jsmpp.bean.Alphabet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SmppUtilsTest {

    private static TimeZone defaultTimeZone;

    @BeforeAll
    public static void setUpBeforeClass() {
        defaultTimeZone = TimeZone.getDefault();

        TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
    }

    @AfterAll
    public static void tearDownAfterClass() {
        if (defaultTimeZone != null) {
            TimeZone.setDefault(defaultTimeZone);
        }
    }

    @Test
    public void formatTime() {
        assertEquals("-300101000000000+", SmppUtils.formatTime(new Date(0L)));
        assertEquals("-300101024640000+", SmppUtils.formatTime(new Date(10000000L)));
    }

    @Test
    public void string2Date() {
        Date date = SmppUtils.string2Date("-300101010000004+");
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);

        assertEquals(5, calendar.get(Calendar.YEAR));
        assertEquals(11, calendar.get(Calendar.MONTH));
        assertEquals(10, calendar.get(Calendar.DAY_OF_MONTH));
        assertEquals(10, calendar.get(Calendar.HOUR));
        assertEquals(10, calendar.get(Calendar.MINUTE));
        assertEquals(0, calendar.get(Calendar.SECOND));
    }

    @ParameterizedTest
    @MethodSource("decodeBodyProvider")
    void testDecodeBodyWhenBodyIsNot8bitAlphabetTheContentShouldBeDecoded(
            String content, Charset encoding, byte dataCoding, String defaultEncoding)
            throws UnsupportedEncodingException {
        byte[] body = content.getBytes(encoding);
        Assertions.assertEquals(content, SmppUtils.decodeBody(body, dataCoding, defaultEncoding));
    }

    @Test
    void testDecodeBodyWhenBodyIs8bitShouldReturnNull() throws UnsupportedEncodingException {
        byte[] body = new byte[] { 0, 1, 2, 3, 4 };
        Assertions.assertNull(SmppUtils.decodeBody(body, Alphabet.ALPHA_8_BIT.value(), "X-Gsm7Bit"));
    }

    @Test
    void testDecodeBodyWithUnsupportedDefaultEncodingShouldThrow() throws UnsupportedEncodingException {
        Assertions.assertThrows(UnsupportedEncodingException.class, () -> {
            SmppUtils.decodeBody(new byte[] { 0 }, Alphabet.ALPHA_DEFAULT.value(), "X-Gsm7Bit");
        });
    }

    private static Stream<Arguments> decodeBodyProvider() {
        return Stream.of(
                Arguments.of("This is an ascii test !", StandardCharsets.US_ASCII, Alphabet.ALPHA_IA5.value(), "X-Gsm7Bit"),
                Arguments.of("This is a latin1 test ®", StandardCharsets.ISO_8859_1, Alphabet.ALPHA_LATIN1.value(),
                        "X-Gsm7Bit"),
                Arguments.of("This is a utf-16 test \uD83D\uDE00", StandardCharsets.UTF_16BE, Alphabet.ALPHA_UCS2.value(),
                        "X-Gsm7Bit"));
    }
}
