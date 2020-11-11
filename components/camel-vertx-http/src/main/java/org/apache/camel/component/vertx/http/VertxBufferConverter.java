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
package org.apache.camel.component.vertx.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import io.netty.buffer.ByteBuf;
import io.vertx.core.buffer.Buffer;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Converter methods to convert from / to Vert.x Buffer
 */
@Converter(generateLoader = true)
public final class VertxBufferConverter {

    private VertxBufferConverter() {
    }

    @Converter
    public static Buffer toBuffer(String string, Exchange exchange) {
        String charset = VertxHttpHelper.getCharsetFromExchange(exchange);
        if (ObjectHelper.isNotEmpty(charset)) {
            Buffer.buffer(string, charset);
        }
        return Buffer.buffer(string);
    }

    @Converter
    public static Buffer toBuffer(byte[] bytes) {
        return Buffer.buffer(bytes);
    }

    @Converter
    public static Buffer toBuffer(ByteBuf byteBuf) {
        return Buffer.buffer(byteBuf);
    }

    @Converter
    public static Buffer toBuffer(InputStream inputStream) throws IOException {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOHelper.copy(IOHelper.buffered(inputStream), bos);
            return Buffer.buffer(bos.toByteArray());
        } finally {
            IOHelper.close(inputStream);
        }
    }

    @Converter
    public static String toString(Buffer buffer, Exchange exchange) {
        String charset = VertxHttpHelper.getCharsetFromExchange(exchange);
        if (ObjectHelper.isNotEmpty(charset)) {
            buffer.toString(charset);
        }
        return buffer.toString();
    }

    @Converter
    public static byte[] toBytes(Buffer buffer) {
        return buffer.getBytes();
    }
}
