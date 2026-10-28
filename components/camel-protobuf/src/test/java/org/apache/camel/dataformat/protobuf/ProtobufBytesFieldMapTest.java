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
package org.apache.camel.dataformat.protobuf;

import java.util.Map;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A {@code bytes} field must keep its value when a message is converted to a {@link Map} and back. The well-known type
 * {@link BytesValue} has a single {@code bytes} field named {@code value}.
 */
public class ProtobufBytesFieldMapTest extends CamelTestSupport {

    // not valid UTF-8, and longer than the text that ByteString.toString() shows
    private static final byte[] DATA = new byte[] {
            0, 1, 2, (byte) 0xff, (byte) 0xfe, 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o',
            'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' };

    @Test
    public void testToMapKeepsTheBytes() {
        Map<String, Object> map = ProtobufConverter.toMap(BytesValue.of(ByteString.copyFrom(DATA)));

        assertArrayEquals(DATA, (byte[]) map.get("value"));
    }

    @Test
    public void testToProtoAcceptsTheBytesOfToMap() {
        BytesValue original = BytesValue.of(ByteString.copyFrom(DATA));

        BytesValue back = (BytesValue) ProtobufConverter.toProto(ProtobufConverter.toMap(original),
                BytesValue.getDefaultInstance());

        assertEquals(original, back);
    }

    @Test
    public void testUnmarshalToMapKeepsTheBytes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:unmarshal", BytesValue.of(ByteString.copyFrom(DATA)).toByteArray());

        mock.assertIsSatisfied();
        Map<?, ?> map = mock.getReceivedExchanges().get(0).getMessage().getBody(Map.class);
        assertArrayEquals(DATA, (byte[]) map.get("value"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:unmarshal").unmarshal(new ProtobufDataFormat(BytesValue.getDefaultInstance()))
                        .to("mock:result");
            }
        };
    }
}
