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
package org.apache.camel.component.http.helper;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Message;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpMethod;

/**
 * 
 * Helper/Utility class to help wrapping
 * content into GZIP Input/Output Streams.
 * 
 *
 */
public final class GZIPHelper {

    public static final String CONTENT_ENCODING = "Content-Encoding";
    public static final String GZIP = "gzip";
    

    // No need for instatiating, so avoid it.
    private GZIPHelper() { }
    
    public static void setGZIPMessageHeader(Message message) {
        message.setHeader(CONTENT_ENCODING, GZIP);
    }
    
    public static void setGZIPContentEncoding(HttpServletResponse response) {
        response.setHeader(CONTENT_ENCODING, GZIP);
    }

    // --------- Methods To Decompress ----------

    public static InputStream getInputStream(HttpMethod method)
        throws IOException {

        Header header = method.getRequestHeader(CONTENT_ENCODING);
        String contentEncoding =  header != null ? header.getValue() : null;
        return getGZIPWrappedInputStream(contentEncoding, 
            method.getResponseBodyAsStream());
    }

    public static InputStream getInputStream(HttpServletRequest request) throws IOException {
        InputStream dataStream = request.getInputStream();
        String contentEncoding = request.getHeader(CONTENT_ENCODING);
        return getGZIPWrappedInputStream(contentEncoding, dataStream);
    }

    public static InputStream getGZIPWrappedInputStream(String gzipEncoding,
        InputStream inStream) throws IOException {
        if (containsGzip(gzipEncoding)) {
            return new GZIPInputStream(new BufferedInputStream(inStream));
        } else {
            return inStream;
        }
    }

    public static InputStream toGZIPInputStreamIfRequested(String gzipEncoding, byte[] array)
        throws Exception {
        if (containsGzip(gzipEncoding)) {
            // GZip byte array content
            ByteArrayOutputStream outputByteArray = new ByteArrayOutputStream();
            GZIPOutputStream gzipOutputStream = new GZIPOutputStream(
                outputByteArray);
            gzipOutputStream.write(array);
            gzipOutputStream.close();
            return new ByteArrayInputStream(outputByteArray.toByteArray());
        } else {
            return new ByteArrayInputStream(array);
        }
    }

    // -------------- Methods To Compress --------------

    public static byte[] compressArrayIfGZIPRequested(String gzipEncoding,
        byte[] array) throws IOException {
        if (containsGzip(gzipEncoding)) {
            return getGZIPWrappedOutputStream(array).toByteArray();
        } else {
            return array;
        }
    }
    
    public static byte[] compressArrayIfGZIPRequested(String gzipEncoding,
            byte[] array, HttpServletResponse response) throws IOException {
        if (containsGzip(gzipEncoding)) {
            return getGZIPWrappedOutputStream(array).toByteArray();
        } else {
            return array;
        }
    }
    
    public static ByteArrayOutputStream getGZIPWrappedOutputStream(byte[] array) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        GZIPOutputStream gzout = new GZIPOutputStream(compressed);
        gzout.write(array);
        gzout.close();
        return compressed;
    }

    private static boolean containsGzip(String str) {
        return str != null && str.toLowerCase().indexOf(GZIP) >= 0;
    }

}
