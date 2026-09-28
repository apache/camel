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
package org.apache.camel.component.netty;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Utility test to verify netty type converter.
 */
public class NettyConverterTest extends CamelTestSupport {

    /**
     * Test payload to send.
     */
    private static final String PAYLOAD = "Test Message";

    private ByteBuf buf;

    @BeforeEach
    public void startUp() {
        byte[] bytes = PAYLOAD.getBytes();
        buf = PooledByteBufAllocator.DEFAULT.buffer(bytes.length);
        buf.writeBytes(bytes);
    }

    @Override
    public void doPostTearDown() {
        buf.release();
    }

    @Test
    public void testConversionWithExchange() {
        String result = context.getTypeConverter().convertTo(String.class, new DefaultExchange(context), buf);
        assertNotNull(result);
        assertEquals(PAYLOAD, result);
    }

    @Test
    public void testConversionWithoutExchange() {
        String result = context.getTypeConverter().convertTo(String.class, buf);
        assertNotNull(result);
        assertEquals(PAYLOAD, result);
    }

    /**
     * A heap (array-backed) buffer whose backing array is larger than the readable region, and whose reader index has
     * been advanced past a prefix, must convert to exactly the readable bytes - not the whole backing array. Converting
     * via buffer.array() used to return the full backing array (prefix + payload + spare capacity).
     */
    @Test
    public void testConversionHeapBufferReturnsOnlyReadableBytes() {
        byte[] payload = PAYLOAD.getBytes(StandardCharsets.UTF_8);
        byte[] prefix = "SKIP".getBytes(StandardCharsets.UTF_8);
        // Heap buffer with spare capacity so its backing array is larger than the readable region
        ByteBuf heap = Unpooled.buffer(prefix.length + payload.length + 32);
        try {
            heap.writeBytes(prefix);
            heap.writeBytes(payload);
            // Skip the prefix: only "payload" is readable now
            heap.readerIndex(prefix.length);

            assertTrue(heap.hasArray(), "expected an array-backed heap buffer for this test");

            byte[] result = NettyConverter.toByteArray(heap, null);
            assertEquals(payload.length, result.length);
            assertEquals(PAYLOAD, new String(result, StandardCharsets.UTF_8));
        } finally {
            heap.release();
        }
    }

}
