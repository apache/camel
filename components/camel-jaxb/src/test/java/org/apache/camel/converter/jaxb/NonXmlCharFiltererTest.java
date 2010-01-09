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

package org.apache.camel.converter.jaxb;

import org.easymock.Capture;
import org.easymock.IAnswer;
import org.easymock.classextension.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

import static org.easymock.EasyMock.and;
import static org.easymock.EasyMock.aryEq;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class NonXmlCharFiltererTest extends EasyMockSupport {
    private NonXmlCharFilterer nonXmlCharFilterer;

    @Before
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
        NonXmlCharFilterer nonXmlCharFiltererMock = createMockBuilder(NonXmlCharFilterer.class)
                .addMockedMethod("filter", char[].class, int.class, int.class).createStrictMock();
        String string = "abc";

        expect(nonXmlCharFiltererMock.filter(aryEq(new char[] {'a', 'b', 'c'}), eq(0), eq(3)))
                .andReturn(false);
        replayAll();

        String result = nonXmlCharFiltererMock.filter(string);
        verifyAll();

        assertSame("Should have returned the same string if nothing was filtered", string, result);
    }

    //@Test
    //"This test can't be build with JDK 1.5"
    /*
    public void testFilter1ArgFiltered() {
        NonXmlCharFilterer nonXmlCharFiltererMock = createMockBuilder(NonXmlCharFilterer.class)
            .addMockedMethod("filter", char[].class, int.class, int.class).createStrictMock();
        final Capture<char[]> bufferCapture = new Capture<char[]>();

        expect(
               nonXmlCharFiltererMock.filter(and(capture(bufferCapture), aryEq(new char[] {'a', 'b', 'c'})),
                                             eq(0), eq(3))).andAnswer(new IAnswer<Boolean>() {
            public Boolean answer() throws Throwable {
                char[] buffer = bufferCapture.getValue();
                buffer[0] = 'i';
                buffer[1] = 'o';
                return true;
            }
        });
        replayAll();

        String result = nonXmlCharFiltererMock.filter("abc");
        verifyAll();

        assertEquals("Should have returned filtered string", "ioc", result);
    }*/

    @Test
    public void testFilter1ArgNullArg() {
        NonXmlCharFilterer nonXmlCharFiltererMock = createStrictMock(NonXmlCharFilterer.class);
        nonXmlCharFiltererMock.filter(null);
    }

    @Test
    public void testFilter3Args() {
        NonXmlCharFilterer nonXmlCharFiltererMock = createMockBuilder(NonXmlCharFilterer.class)
                .addMockedMethod("isFiltered").createStrictMock();
        char[] buffer = new char[] {'1', '2', '3', '4', '5', '6'};

        expect(nonXmlCharFiltererMock.isFiltered('3')).andReturn(true);
        expect(nonXmlCharFiltererMock.isFiltered('4')).andReturn(false);
        expect(nonXmlCharFiltererMock.isFiltered('5')).andReturn(true);
        replayAll();

        nonXmlCharFiltererMock.filter(buffer, 2, 3);
        verifyAll();

        assertArrayEquals("Unexpected buffer contents",
                new char[] {'1', '2', ' ', '4', ' ', '6'}, buffer);
    }

    @Test
    public void testFilter3ArgsNullArg() {
        NonXmlCharFilterer nonXmlCharFiltererMock = createStrictMock(NonXmlCharFilterer.class);
        nonXmlCharFiltererMock.filter(null, 2, 3);
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
