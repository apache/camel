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
package org.apache.camel.component.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import junit.framework.TestCase;

import org.apache.camel.component.http.helper.GZIPHelper;

public class GZIPHelperTest extends TestCase {

    public void testGetGZIPWrappedInputStreamTrue() throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        GZIPOutputStream gzout = new GZIPOutputStream(compressed);
        gzout.write(new byte[]{0, 1});
        gzout.close();
        ByteArrayInputStream bai = new ByteArrayInputStream(compressed.toByteArray());
        InputStream inStream = GZIPHelper.getGZIPWrappedInputStream(GZIPHelper.GZIP, bai);
        assertNotNull(inStream);
        assertTrue("Returned InputStream is not of type GZIPInputStream", inStream instanceof GZIPInputStream);
    }

    public void testGetGZIPWrappedInputStreamFalse() throws Exception {
        ByteArrayInputStream bai = new ByteArrayInputStream(new byte[]{0, 1});
        InputStream inStream = GZIPHelper.getGZIPWrappedInputStream("other-encoding", bai);
        assertNotNull(inStream);
        assertFalse("Unexpected Return InputStream type", inStream instanceof GZIPInputStream);
    }

    public void testGetInputStreamStringByteArrayTrue() throws Exception {
        InputStream inStream = GZIPHelper.toGZIPInputStreamIfRequested(GZIPHelper.GZIP, new byte[]{0, 1});
        assertNotNull(inStream);
        try {
            new GZIPInputStream(inStream);
        } catch (Exception e) {
            fail("Returned InpuStream is not GZipped correctly");
        }
    }

    public void testGetInputStreamStringByteArrayFalse() throws Exception {
        InputStream inStream = GZIPHelper.toGZIPInputStreamIfRequested("other-encoding", new byte[]{0, 1});
        assertNotNull(inStream);
        try {
            new GZIPInputStream(inStream);
            fail("Returned InputStream should not be GZipped!");
        } catch (IOException e) {
            // Expected error.
        }
    }

    public void testCompressArrayIfGZIPRequestedStringByteArrayTrue() throws Exception {
        byte[] initialArray = new byte[]{0, 1};
        
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        GZIPOutputStream gzout = new GZIPOutputStream(compressed);
        gzout.write(initialArray);
        gzout.close();
        byte[] expectedArray = compressed.toByteArray();
        assertNotNull("Returned expectedArray is null", expectedArray);

        byte[] retArray = GZIPHelper.compressArrayIfGZIPRequested(GZIPHelper.GZIP, initialArray);
        assertNotNull("Returned array is null", retArray);


        assertTrue("Length of returned array is different than expected array.", expectedArray.length == retArray.length);

        for (int i = 0; i < retArray.length; i++) {
            assertEquals("Contents of returned array is different thatn expected array", expectedArray[i], retArray[i]);
        }

    }

    public void testGetGZIPWrappedOutputStream() throws Exception {
        ByteArrayOutputStream arrayOutputStream = GZIPHelper.getGZIPWrappedOutputStream(new byte[]{0, 1});
        assertNotNull(arrayOutputStream);
    }

}
