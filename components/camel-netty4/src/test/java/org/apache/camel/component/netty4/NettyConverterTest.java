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
package org.apache.camel.component.netty4;

import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.DynamicChannelBuffer;
import org.junit.Before;
import org.junit.Test;

/**
 * Utility test to verify netty type converter.
 */
public class NettyConverterTest extends CamelTestSupport {

    /**
     * Test payload to send.
     */
    private  static final String PAYLOAD = "Test Message";

    private ChannelBuffer buf;

    @Before
    public void startUp() {
        byte[] bytes = PAYLOAD.getBytes();
        buf = new DynamicChannelBuffer(bytes.length);
        buf.writeBytes(bytes);
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

}
