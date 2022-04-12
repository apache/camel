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
package org.apache.camel.converter.jaxb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NonXmlCharFiltererTest {
    private NonXmlCharFilterer nonXmlCharFilterer;
    @Mock
    private NonXmlCharFilterer nonXmlCharFiltererMock;

    @BeforeEach
    public void setUp() {
        nonXmlCharFilterer = new NonXmlCharFilterer();
    }

    @Test
    public void testIsFilteredValidChars() {
        // Per http://www.w3.org/TR/2004/REC-xml-20040204/#NT-Char
        // Char ::= #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] |
        // [#x10000-#x10FFFF]
        checkSingleValid(0x9);
        checkSingleValid(0xA);
        checkSingleValid(0xD);
        checkRangeValid(0x20, 0xD7FF);
        checkRangeValid(0xE000, 0xFFFD);
        // not checking [0x10000, 0x10FFFF], as it goes beyond
        // Character.MAX_VALUE
    }

    @Test
    public void testIsFilteredInvalidChars() {
        // Per http://www.w3.org/TR/2004/REC-xml-20040204/#NT-Char
        // Char ::= #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] |
        // [#x10000-#x10FFFF]
        checkRangeInvalid(0x0, 0x8);
        checkRangeInvalid(0xB, 0xC);
        checkRangeInvalid(0xE, 0x1F);
        checkRangeInvalid(0xD800, 0xDFFF);
        checkRangeInvalid(0xFFFE, 0xFFFF);
        // no need to check beyond #x10FFFF as this is greater than
        // Character.MAX_VALUE
    }

    @Test
    public void testFilter1ArgNonFiltered() {
        when(nonXmlCharFiltererMock.filter(anyString())).thenCallRealMethod();
        when(nonXmlCharFiltererMock.filter(any(char[].class), anyInt(), anyInt())).thenReturn(false);

        String string = "abc";
        String result = nonXmlCharFiltererMock.filter(string);

        verify(nonXmlCharFiltererMock).filter(new char[] { 'a', 'b', 'c' }, 0, 3);

        assertSame(string, result, "Should have returned the same string if nothing was filtered");
    }

    @Test
    public void testFilter1ArgFiltered() {
        when(nonXmlCharFiltererMock.filter(anyString())).thenCallRealMethod();
        when(nonXmlCharFiltererMock.filter(eq(new char[] { 'a', 'b', 'c' }), anyInt(), anyInt()))
                .thenAnswer(new Answer<Boolean>() {

                    public Boolean answer(InvocationOnMock invocation) {
                        char[] buffer = (char[]) invocation.getArguments()[0];
                        buffer[0] = 'i';
                        buffer[1] = 'o';
                        return true;
                    }
                });

        String result = nonXmlCharFiltererMock.filter("abc");
        verify(nonXmlCharFiltererMock).filter(any(char[].class), eq(0), eq(3));
        assertEquals("ioc", result, "Should have returned filtered string");
    }

    @Test
    public void testFilter1ArgNullArg() {
        nonXmlCharFiltererMock.filter(null);
        verify(nonXmlCharFiltererMock, never()).filter(any(char[].class), anyInt(), anyInt());
    }

    @Test
    public void testFilter3Args() {
        when(nonXmlCharFiltererMock.filter(any(char[].class), anyInt(), anyInt())).thenCallRealMethod();
        when(nonXmlCharFiltererMock.isFiltered(anyChar())).thenReturn(true, false, true);

        char[] buffer = new char[] { '1', '2', '3', '4', '5', '6' };
        nonXmlCharFiltererMock.filter(buffer, 2, 3);

        verify(nonXmlCharFiltererMock).isFiltered('3');
        verify(nonXmlCharFiltererMock).isFiltered('4');
        verify(nonXmlCharFiltererMock).isFiltered('5');

        assertArrayEquals(new char[] { '1', '2', ' ', '4', ' ', '6' }, buffer, "Unexpected buffer contents");
    }

    @Test
    public void testFilter3ArgsNullArg() {
        assertDoesNotThrow(() -> nonXmlCharFilterer.filter(null, 2, 3));
    }

    private void checkSingleValid(int charCode) {
        checkRangeValid(charCode, charCode);
    }

    private void checkRangeValid(int startCharCodeInclusive, int endCharCodeInclusive) {
        for (int charCode = startCharCodeInclusive; charCode <= endCharCodeInclusive; charCode++) {
            if (nonXmlCharFilterer.isFiltered((char) charCode)) {
                fail("Character " + asHex(charCode) + " from range ["
                     + asHex(startCharCodeInclusive) + "-" + asHex(endCharCodeInclusive)
                     + "] should be valid, but it is not");
            }

        }
    }

    private void checkRangeInvalid(int startCharCodeInclusive, int endCharCodeInclusive) {
        for (int charCode = startCharCodeInclusive; charCode <= endCharCodeInclusive; charCode++) {
            if (!nonXmlCharFilterer.isFiltered((char) charCode)) {
                fail("Character " + asHex(charCode) + " from range ["
                     + asHex(startCharCodeInclusive) + "-" + asHex(endCharCodeInclusive)
                     + "] should not be valid, but it is");
            }
        }
    }

    private String asHex(int charCode) {
        return "#x" + Integer.toHexString(charCode);
    }
}
