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
package org.apache.camel.component.vertx;

import java.io.InputStream;

import io.netty.buffer.Unpooled;
import io.vertx.core.buffer.Buffer;
import org.apache.camel.Exchange;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VertxBufferConverterTest extends CamelTestSupport {

    private static final String BODY = "Hello World";

    @Test
    public void testStringToBuffer() {
        Buffer buffer = context.getTypeConverter().convertTo(Buffer.class, BODY);
        Assertions.assertEquals(BODY, buffer.toString());
    }

    @Test
    public void testStringToBufferWithEncoding() {
        Exchange exchange = ExchangeBuilder.anExchange(context)
                .withHeader(Exchange.CONTENT_TYPE, "text/html; charset=iso-8859-4").build();
        context.getTypeConverter().convertTo(Buffer.class, exchange, BODY);
        Buffer buffer = context.getTypeConverter().convertTo(Buffer.class, BODY);
        Assertions.assertEquals(BODY, buffer.toString());
    }

    @Test
    public void testByteArrayToBuffer() {
        Buffer buffer = context.getTypeConverter().convertTo(Buffer.class, BODY.getBytes());
        Assertions.assertEquals(BODY, buffer.toString());
    }

    @Test
    public void testByteBufToBuffer() {
        Buffer buffer = context.getTypeConverter().convertTo(Buffer.class, Unpooled.wrappedBuffer(BODY.getBytes()));
        Assertions.assertEquals(BODY, buffer.toString());
    }

    @Test
    public void testInputStreamToBuffer() {
        InputStream inputStream = context.getTypeConverter().convertTo(InputStream.class, BODY);
        Buffer buffer = context.getTypeConverter().convertTo(Buffer.class, inputStream);
        Assertions.assertEquals(BODY, buffer.toString());
    }

    @Test
    public void testBufferToString() {
        String result = context.getTypeConverter().convertTo(String.class, Buffer.buffer(BODY));
        Assertions.assertEquals(BODY, result);
    }

    @Test
    public void testBufferToStringWithEncoding() {
        Exchange exchange = ExchangeBuilder.anExchange(context)
                .withHeader(Exchange.CONTENT_TYPE, "text/html; charset=iso-8859-4").build();
        String result = context.getTypeConverter().convertTo(String.class, exchange, Buffer.buffer(BODY));
        Assertions.assertEquals(BODY, result);
    }

    @Test
    public void testBufferToByteArray() {
        byte[] result = context.getTypeConverter().convertTo(byte[].class, Buffer.buffer(BODY.getBytes()));
        Assertions.assertEquals(BODY, new String(result));
    }
}
