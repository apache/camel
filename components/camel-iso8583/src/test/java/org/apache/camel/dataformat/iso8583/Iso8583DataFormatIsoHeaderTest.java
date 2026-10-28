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
package org.apache.camel.dataformat.iso8583;

import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.MessageFactory;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The ISO header of a message type is optional in the j8583 configuration, and it can be binary: the data format must
 * unmarshal such messages too.
 */
class Iso8583DataFormatIsoHeaderTest extends CamelTestSupport {

    private MessageFactory<IsoMessage> messageFactory;

    @BeforeEach
    void createMessageFactory() throws Exception {
        messageFactory = new MessageFactory<>();
        messageFactory.setConfigPath("j8583-config.xml");
    }

    @Test
    void unmarshalMessageTypeWithoutIsoHeader() {
        // 0201 has a parse guide but no ISO header in j8583-config.xml
        IsoMessage message = messageFactory.newMessage(0x201);
        message.setValue(3, "1234567890123456789", IsoType.NUMERIC, 19);

        IsoMessage parsed = template.requestBody("direct:0201", message.writeData(), IsoMessage.class);

        assertEquals(0x201, parsed.getType());
        assertEquals("1234567890123456789", parsed.getObjectValue(3).toString());
    }

    @Test
    void unmarshalMessageTypeWithBinaryIsoHeader() {
        // 0280 has the binary ISO header ffffffff in j8583-config.xml
        IsoMessage message = messageFactory.newMessage(0x280);
        message.setValue(3, 42, IsoType.NUMERIC, 2);

        IsoMessage parsed = template.requestBody("direct:0280", message.writeData(), IsoMessage.class);

        assertEquals(0x280, parsed.getType());
        assertEquals("42", parsed.getObjectValue(3).toString());
        assertArrayEquals(new byte[] { (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff }, parsed.getBinaryIsoHeader());
    }

    @Test
    void unmarshalMessageTypeWithIsoHeader() {
        // 0800 has the ISO header ISO015000015 in j8583-config.xml
        IsoMessage message = messageFactory.newMessage(0x800);
        message.setValue(3, "123456", IsoType.ALPHA, 6);

        IsoMessage parsed = template.requestBody("direct:0800", message.writeData(), IsoMessage.class);

        assertEquals(0x800, parsed.getType());
        assertEquals("ISO015000015", parsed.getIsoHeader());
        assertEquals("123456", parsed.getObjectValue(3));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:0201").unmarshal().iso8583("0201");
                from("direct:0280").unmarshal().iso8583("0280");
                from("direct:0800").unmarshal().iso8583("0800");
            }
        };
    }
}
