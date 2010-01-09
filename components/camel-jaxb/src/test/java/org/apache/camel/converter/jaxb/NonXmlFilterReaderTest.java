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

import java.io.IOException;
import java.io.Reader;

import org.easymock.classextension.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

import static org.easymock.EasyMock.anyInt;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.notNull;
import static org.easymock.EasyMock.same;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class NonXmlFilterReaderTest extends EasyMockSupport {
    private NonXmlFilterReader nonXmlFilterReader;
    private NonXmlCharFilterer nonXmlCharFiltererMock;
    private Reader readerMock;

    @Before
    public void setUp() {
        readerMock = createStrictMock(Reader.class);
        nonXmlCharFiltererMock = createStrictMock(NonXmlCharFilterer.class);
        nonXmlFilterReader = new NonXmlFilterReader(readerMock);
        nonXmlFilterReader.nonXmlCharFilterer = nonXmlCharFiltererMock;
    }

    @Test
    public void testRead() throws IOException {
        char[] buffer = new char[10];

        expect(readerMock.read(same(buffer), eq(3), eq(5))).andDelegateTo(
                new ConstantReader(new char[] {'a', 'b', 'c'}));
        expect(nonXmlCharFiltererMock.filter(same(buffer), eq(3), eq(3))).andReturn(false);

        replayAll();
        int result = nonXmlFilterReader.read(buffer, 3, 5);
        verifyAll();

        assertEquals("Unexpected number of chars read", 3, result);
        assertArrayEquals("Wrong buffer contents", new char[] {0, 0, 0, 'a', 'b', 'c', 0, 0, 0, 0},
                buffer);
    }

    @Test
    public void testReadEOS() throws IOException {
        char[] buffer = new char[10];

        expect(readerMock.read((char[]) notNull(), anyInt(), anyInt())).andReturn(-1);

        replayAll();
        int result = nonXmlFilterReader.read(buffer, 3, 5);
        verifyAll();

        assertEquals("Unexpected number of chars read", -1, result);
        assertArrayEquals("Buffer should not have been affected",
                          new char[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, buffer);

    }

    static class ConstantReader extends Reader {
        private char[] constant;

        public ConstantReader(char[] constant) {
            this.constant = constant;
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            int length = Math.min(len, constant.length);
            System.arraycopy(constant, 0, cbuf, off, length);
            return length;
        }

    }
}
