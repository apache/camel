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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Some core java.nio based
 * <a href="http://activemq.apache.org/camel/type-converter.html">Type Converters</a>
 *
 * @version $Revision$
 */
@Converter
public final class NIOConverter {
    private static final transient Log LOG = LogFactory.getLog(NIOConverter.class);

    /**
     * Utility classes should not have a public constructor.
     */
    private NIOConverter() {
    }

    @Converter
    public static byte[] toByteArray(ByteBuffer buffer) {
        return buffer.array();
    }

    @Converter
    public static String toString(ByteBuffer buffer, Exchange exchange) {
        return IOConverter.toString(buffer.array(), exchange);
    }

    @Converter
    public static ByteBuffer toByteBuffer(byte[] data) {
        return ByteBuffer.wrap(data);
    }

    @Converter
    public static ByteBuffer toByteBuffer(File file) throws IOException {
        InputStream in = null;
        try {
            byte[] buf = new byte[(int)file.length()];
            in = new BufferedInputStream(new FileInputStream(file));
            int sizeLeft = (int)file.length();
            int offset = 0;
            while (sizeLeft > 0) {
                int readSize = in.read(buf, offset, sizeLeft);
                sizeLeft -= readSize;
                offset += readSize;
            }
            return ByteBuffer.wrap(buf);
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                LOG.warn("Failed to close file stream: " + file.getPath(), e);
            }
        }
    }

    @Converter
    public static ByteBuffer toByteBuffer(String value, Exchange exchange) {
        ByteBuffer buf = ByteBuffer.allocate(value.length());
        byte[] bytes = null;
        if (exchange != null) {
            String charsetName = exchange.getProperty(Exchange.CHARSET_NAME, String.class);
            if (charsetName != null) {
                try {
                    bytes = value.getBytes(charsetName);
                } catch (UnsupportedEncodingException e) {
                    LOG.warn("Can't convert the byte to String with the charset " + charsetName, e);
                }
            }
        }
        if (bytes == null) {
            bytes = value.getBytes();
        }
        buf.put(bytes);
        return buf;
    }
    @Converter
    public static ByteBuffer toByteBuffer(Short value) {
        ByteBuffer buf = ByteBuffer.allocate(2);
        buf.putShort(value);
        return buf;
    }
    @Converter
    public static ByteBuffer toByteBuffer(Integer value) {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putInt(value);
        return buf;
    }
    @Converter
    public static ByteBuffer toByteBuffer(Long value) {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.putLong(value);
        return buf;
    }
    @Converter
    public static ByteBuffer toByteBuffer(Float value) {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putFloat(value);
        return buf;
    }
    @Converter
    public static ByteBuffer toByteBuffer(Double value) {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.putDouble(value);
        return buf;
    }

    @Converter
    public static InputStream toInputStream(ByteBuffer bufferbuffer) {
        return IOConverter.toInputStream(toByteArray(bufferbuffer));
    }
}
