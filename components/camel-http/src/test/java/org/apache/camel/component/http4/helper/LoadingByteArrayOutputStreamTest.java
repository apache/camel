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
package org.apache.camel.component.http4.helper;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class LoadingByteArrayOutputStreamTest {

    private LoadingByteArrayOutputStream out;
    private byte[] bytes = new byte[]{1, 2, 3, 4, 5};

    @Before
    public void setUp() throws Exception {
        out = new LoadingByteArrayOutputStream(4);
        out.write(bytes);
    }

    @Test
    public void defaultConstructor() {
        out = new LoadingByteArrayOutputStream() {
            public byte[] toByteArray() {
                return buf;
            }
        };

        assertEquals(1024, out.toByteArray().length);
    }

    @Test
    public void toByteArrayShouldReturnTheSameArray() {
        byte[] byteArray1 = out.toByteArray();
        byte[] byteArray2 = out.toByteArray();

        assertEquals(5, byteArray1.length);
        assertSame(byteArray1, byteArray2);
    }

    @Test
    public void toByteArrayShouldReturnANewArray() throws IOException {
        byte[] byteArray1 = out.toByteArray();
        out.write(bytes);
        byteArray1 = out.toByteArray();

        assertEquals(10, byteArray1.length);
    }

    @Test
    public void createInputStream() {
        ByteArrayInputStream in = out.createInputStream();

        assertEquals(1, in.read());
        assertEquals(2, in.read());
        assertEquals(3, in.read());
        assertEquals(4, in.read());
        assertEquals(5, in.read());
        assertEquals(-1, in.read());
    }
}