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
import java.io.UnsupportedEncodingException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;

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
    public static byte[] toByteArray(ChannelBuffer buffer, Exchange exchange) {
        return buffer.array();
    }

    @Converter
    public static String toString(ChannelBuffer buffer, Exchange exchange) throws UnsupportedEncodingException {
        byte[] bytes = toByteArray(buffer, exchange);
        // use type converter as it can handle encoding set on the Exchange
        if (exchange != null) {
            return exchange.getContext().getTypeConverter().convertTo(String.class, exchange, bytes);
        }
        return new String(bytes, "UTF-8");
    }

    @Converter
    public static InputStream toInputStream(ChannelBuffer buffer, Exchange exchange) {
        return new ChannelBufferInputStream(buffer);
    }

    @Converter
    public static ObjectInput toObjectInput(ChannelBuffer buffer, Exchange exchange) throws IOException {
        InputStream is = toInputStream(buffer, exchange);
        return new ObjectInputStream(is);
    }

    @Converter
    public static ChannelBuffer toByteBuffer(byte[] bytes, Exchange exchange) {
        ChannelBuffer buf = ChannelBuffers.dynamicBuffer(bytes.length);
        buf.writeBytes(bytes);
        return buf;
    }

    @Converter
    public static ChannelBuffer toByteBuffer(String s, Exchange exchange) {
        byte[] bytes;
        if (exchange != null) {
            // use type converter as it can handle encoding set on the Exchange
            bytes = exchange.getContext().getTypeConverter().convertTo(byte[].class, exchange, s);
        } else {
            bytes = s.getBytes();
        }
        return toByteBuffer(bytes, exchange);
    }

    @Converter
    public static Document toDocument(ChannelBuffer buffer, Exchange exchange) {
        InputStream is = toInputStream(buffer, exchange);
        return exchange.getContext().getTypeConverter().convertTo(Document.class, exchange, is);
    }

    @Converter
    public static DOMSource toDOMSource(ChannelBuffer buffer, Exchange exchange) {
        InputStream is = toInputStream(buffer, exchange);
        return exchange.getContext().getTypeConverter().convertTo(DOMSource.class, exchange, is);
    }

    @Converter
    public static SAXSource toSAXSource(ChannelBuffer buffer, Exchange exchange) {
        InputStream is = toInputStream(buffer, exchange);
        return exchange.getContext().getTypeConverter().convertTo(SAXSource.class, exchange, is);
    }

    @Converter
    public static StreamSource toStreamSource(ChannelBuffer buffer, Exchange exchange) {
        InputStream is = toInputStream(buffer, exchange);
        return exchange.getContext().getTypeConverter().convertTo(StreamSource.class, exchange, is);
    }

    @Converter
    public static StAXSource toStAXSource(ChannelBuffer buffer, Exchange exchange) {
        InputStream is = toInputStream(buffer, exchange);
        return exchange.getContext().getTypeConverter().convertTo(StAXSource.class, exchange, is);
    }

}
