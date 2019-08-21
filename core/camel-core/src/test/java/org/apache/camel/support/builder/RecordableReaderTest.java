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
package org.apache.camel.support.builder;

import java.io.ByteArrayInputStream;

import org.junit.Assert;
import org.junit.Test;

public class RecordableReaderTest extends Assert {
    private static final byte[] DATA;

    static {
        DATA = new byte[512];
        final int radix = 0x7f - 0x20;
        for (int i = 0; i < 512; i++) {
            DATA[i] = (byte)(i % radix + 0x20);
        }
    }

    @Test
    public void testReadAndGetTextsBufferPurge() throws Exception {
        RecordableInputStream ris = new RecordableInputStream(new ByteArrayInputStream(DATA), "utf-8");
        assertEquals(0, ris.size());
        byte[] buf = new byte[64];

        // 8 * 64 = 512
        for (int i = 0; i < 8; i++) {
            // read in 64 bytes
            int n = ris.read(buf, 0, buf.length);
            assertEquals(64, n);
            assertEquals(64, ris.size());

            int offset = i * 64;
            // consume the first 32 bytes
            String text = ris.getText(32);
            assertEquals(new String(DATA, offset, 32, "utf-8"), text);
            assertEquals(32, ris.size());

            // consume the other 32 bytes
            text = ris.getText(32);
            assertEquals(new String(DATA, offset + 32, 32, "utf-8"), text);
            assertEquals(0, ris.size());

            ris.record();
        }

        ris.close();
    }

    @Test
    public void testReadAndGetTextsAutoStopRecord() throws Exception {
        RecordableInputStream ris = new RecordableInputStream(new ByteArrayInputStream(DATA), "utf-8");
        assertEquals(0, ris.size());
        byte[] buf = new byte[64];

        // read 64 bytes
        int n = ris.read(buf, 0, buf.length);
        assertEquals(64, n);
        assertEquals(64, ris.size());

        // consume the 64 bytes
        String text = ris.getText(64);

        assertEquals(new String(DATA, 0, 64, "utf-8"), text);
        assertEquals(0, ris.size());

        // read the next 64 bytes
        n = ris.read(buf, 0, buf.length);
        assertEquals(64, n);
        assertEquals(0, ris.size());

        // turn back on the recording and read the next 64 bytes
        ris.record();
        n = ris.read(buf, 0, buf.length);
        assertEquals(64, n);
        assertEquals(64, ris.size());

        // consume the 64 bytes
        text = ris.getText(64);

        // 64 * 2 = 128
        assertEquals(new String(DATA, 128, 64, "utf-8"), text);
        assertEquals(0, ris.size());

        ris.close();
    }

}
