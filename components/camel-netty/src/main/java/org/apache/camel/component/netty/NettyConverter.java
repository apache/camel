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

package org.apache.camel.component.netty;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.DynamicChannelBuffer;

/**
 * A set of converter methods for working with Netty types
 *
 * @version 
 */
@Converter
public final class NettyConverter {

    private NettyConverter() {
        //Utility Class
    }

    @Converter
    public static byte[] toByteArray(ChannelBuffer buffer) {
        return buffer.array();
    }

    @Converter
    public static String toString(ChannelBuffer buffer, Exchange exchange) {
        byte[] bytes = toByteArray(buffer);
        // use type converter as it can handle encoding set on the Exchange
        return exchange.getContext().getTypeConverter().convertTo(String.class, exchange, bytes);
    }

    @Converter
    public static InputStream toInputStream(ChannelBuffer buffer) {
        return new ChannelBufferInputStream(buffer);
    }

    @Converter
    public static ObjectInput toObjectInput(ChannelBuffer buffer) throws IOException {
        InputStream is = toInputStream(buffer);
        return new ObjectInputStream(is);
    }

    @Converter
    public static ChannelBuffer toByteBuffer(byte[] bytes) {
        ChannelBuffer buf = new DynamicChannelBuffer(bytes.length);

        buf.writeBytes(bytes);
        return buf;
    }
}
