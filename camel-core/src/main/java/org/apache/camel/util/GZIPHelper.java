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
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Message;

/**
 * Helper class to help wrapping content into GZIP input and output streams.
 */
public final class GZIPHelper {

    private GZIPHelper() {
    }

    public static InputStream uncompressGzip(String contentEncoding, InputStream in) throws IOException {
        if (isGzip(contentEncoding)) {
            return new GZIPInputStream(in);
        } else {
            return in;
        }
    }

    public static InputStream compressGzip(String contentEncoding, InputStream in) throws IOException {
        if (isGzip(contentEncoding)) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(os);
            try {
                IOHelper.copy(in, gzip);
                gzip.finish();
                return new ByteArrayInputStream(os.toByteArray());
            } finally {
                IOHelper.close(gzip, "gzip");
                IOHelper.close(os, "byte array output stream");
            }
        } else {
            return in;
        }
    }

    public static InputStream compressGzip(String contentEncoding, byte[] data) throws IOException {
        if (isGzip(contentEncoding)) {
            ByteArrayOutputStream os = null;
            GZIPOutputStream gzip = null;
            try {
                os = new ByteArrayOutputStream();
                gzip = new GZIPOutputStream(os);
                gzip.write(data);
                gzip.finish();
                return new ByteArrayInputStream(os.toByteArray());
            } finally {
                IOHelper.close(gzip, "gzip");
                IOHelper.close(os, "byte array output stream");
            }
        } else {
            return new ByteArrayInputStream(data);
        }
    }

    public static byte[] compressGZIP(byte[] data) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(os);
        try {
            gzip.write(data);
            gzip.finish();
            return os.toByteArray();
        } finally {
            IOHelper.close(gzip, "gzip");
            IOHelper.close(os, "byte array output stream");
        }
    }

    public static boolean isGzip(Message message) {        
        return isGzip(message.getHeader(Exchange.CONTENT_ENCODING, String.class), message.getExchange());
    }
    
    public static boolean isGzip(String header, Exchange exchange) {
        if (exchange == null || !exchange.getProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.FALSE, Boolean.class)) {
            return isGzip(header);
        } else {
            return false;
        }
    }

    public static boolean isGzip(String header) {
        return header != null && header.toLowerCase(Locale.ENGLISH).contains("gzip");
    }
}
