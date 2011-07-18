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
package org.apache.camel.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import junit.framework.TestCase;

/**
 * @version 
 */
public class IOHelperTest extends TestCase {

    public void testIOException() {
        IOException io = new IOException("Damn", new IllegalArgumentException("Damn"));
        assertEquals("Damn", io.getMessage());
        assertTrue(io.getCause() instanceof IllegalArgumentException);
    }

    public void testIOExceptionWithMessage() {
        IOException io = new IOException("Not again", new IllegalArgumentException("Damn"));
        assertEquals("Not again", io.getMessage());
        assertTrue(io.getCause() instanceof IllegalArgumentException);
    }

    public void testNewStringFromBytes() {
        String s = IOHelper.newStringFromBytes("Hello".getBytes());
        assertEquals("Hello", s);
    }

    public void testNewStringFromBytesWithStart() {
        String s = IOHelper.newStringFromBytes("Hello".getBytes(), 2, 3);
        assertEquals("llo", s);
    }

    public void testCopyAndCloseInput() throws Exception {
        InputStream is = new ByteArrayInputStream("Hello".getBytes());
        OutputStream os = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is, os, 256);
    }
}
