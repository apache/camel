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
package org.apache.camel.component.hl7;

import java.nio.charset.StandardCharsets;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.filter.codec.ProtocolCodecSession;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * End bytes that the HL7 MLLP MINA decoder reads before a start byte: they do not end the message that starts after
 * them.
 */
class HL7MLLPDecoderEndBytesBeforeStartTest {

    private static final String START = "\u000b";
    private static final String END = "\u001c\r";

    private final ProtocolCodecSession session = new ProtocolCodecSession();

    @Test
    void endByte1RightBeforeTheStartByte() throws Exception {
        ProtocolDecoder decoder = decoder(true);

        // the end byte 2 (a segment separator) right after the start byte does not end the message
        decode(decoder, "\u001c" + START + "\rMSH|A" + END);

        assertEquals("\rMSH|A", session.getDecoderOutputQueue().poll());
        assertNull(session.getDecoderOutputQueue().poll());
    }

    @Test
    void endBytesWithoutStartBeforeTheStartByte() throws Exception {
        ProtocolDecoder decoder = decoder(true);

        decode(decoder, END + START + "\rMSH|A" + END);
        decode(decoder, START + "MSH|B" + END);

        assertEquals("\rMSH|A", session.getDecoderOutputQueue().poll());
        assertEquals("MSH|B", session.getDecoderOutputQueue().poll());
        assertNull(session.getDecoderOutputQueue().poll());
    }

    @Test
    void endBytesWithoutStartBeforeTheStartByteAsBytes() throws Exception {
        ProtocolDecoder decoder = decoder(false);

        decode(decoder, END + START + "\rMSH|A" + END);

        assertArrayEquals("\rMSH|A".getBytes(StandardCharsets.ISO_8859_1), (byte[]) session.getDecoderOutputQueue().poll());
        assertNull(session.getDecoderOutputQueue().poll());
    }

    @Test
    void endBytesWithoutStartBeforeAMessage() throws Exception {
        ProtocolDecoder decoder = decoder(true);

        decode(decoder, END + START + "MSH|A" + END);

        assertEquals("MSH|A", session.getDecoderOutputQueue().poll());
        assertNull(session.getDecoderOutputQueue().poll());
    }

    @Test
    void endByte1BeforeTheStartByteInAnotherRead() throws Exception {
        ProtocolDecoder decoder = decoder(true);

        decode(decoder, "\u001c");
        decode(decoder, START + "\rMSH|A" + END);

        assertEquals("\rMSH|A", session.getDecoderOutputQueue().poll());
        assertNull(session.getDecoderOutputQueue().poll());
    }

    @Test
    void endByte1Zero() throws Exception {
        // the decoder starts with no previous byte, also when the end byte 1 is 0x00
        HL7MLLPConfig config = new HL7MLLPConfig();
        config.setCharset(StandardCharsets.ISO_8859_1);
        config.setEndByte1('\u0000');
        ProtocolDecoder decoder = new HL7MLLPDecoder(config);

        decode(decoder, START + "\rMSH|A" + "\u0000\r");

        assertEquals("\rMSH|A", session.getDecoderOutputQueue().poll());
        assertNull(session.getDecoderOutputQueue().poll());
    }

    private static ProtocolDecoder decoder(boolean produceString) {
        HL7MLLPConfig config = new HL7MLLPConfig();
        config.setCharset(StandardCharsets.ISO_8859_1);
        config.setProduceString(produceString);
        return new HL7MLLPDecoder(config);
    }

    private void decode(ProtocolDecoder decoder, String data) throws Exception {
        decoder.decode(session, IoBuffer.wrap(data.getBytes(StandardCharsets.ISO_8859_1)), session.getDecoderOutput());
    }
}
