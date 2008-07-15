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
package org.apache.camel.converter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import junit.framework.TestCase;

/**
 * Test case for {@link IOConverter}
 */
public class IOConverterTest extends TestCase {

    private static final byte[] TESTDATA = "My test data".getBytes();

    public void testToBytes() throws FileNotFoundException, IOException {
        File file = new File("src/test/resources/org/apache/camel/converter/dummy.txt");
        byte[] data = IOConverter.toBytes(new FileInputStream(file));
        assertEquals("get the wrong byte size", file.length(), data.length);
        assertEquals('#', (char) data[0]);
        assertEquals('!', (char) data[data.length - 1]);
    }

    public void testCopy() throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(TESTDATA);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOConverter.copy(bis, bos);
        assertEquals(TESTDATA, bos.toByteArray());
    }

    private void assertEquals(byte[] data1, byte[] data2) {
        assertEquals(data1.length, data2.length);
        for (int i = 0; i < data1.length; i++) {
            assertEquals(data1[i], data2[i]);
        }
    }

}
