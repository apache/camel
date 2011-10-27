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
package org.apache.camel.component.hl7;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HL7MLLPDecoder that is aware that a HL7 message can span several TCP packets.
 * In addition, it avoids rescanning packets by keeping state in the IOSession.
 */
class HL7MLLPDecoder extends CumulativeProtocolDecoder {

    private static final transient Logger LOG = LoggerFactory.getLogger(HL7MLLPDecoder.class);

    private static final String CHARSET_DECODER = HL7MLLPDecoder.class.getName() + ".charsetdecoder";
    private static final String DECODER_STATE = HL7MLLPDecoder.class.getName() + ".STATE";

    private HL7MLLPConfig config;

    HL7MLLPDecoder(HL7MLLPConfig config) {
        super();
        this.config = config;
    }

    @Override
    protected boolean doDecode(IoSession session, ByteBuffer in, ProtocolDecoderOutput out) {

        // Scan the buffer of start and/or end bytes
        boolean foundEnd = scan(session, in);

        // Write HL7 string or wait until message end arrives or buffer ends
        if (foundEnd) {
            writeString(session, in, out);
        } else {
            LOG.debug("No complete message in this packet");
        }

        return foundEnd;
    }

    private void writeString(IoSession session, ByteBuffer in, ProtocolDecoderOutput out) {
        DecoderState state = decoderState(session);
        if (state.posStart == 0) {
            LOG.warn("No start byte found, reading from beginning of data");
        }
        // start reading from the buffer after the start markers
        in.position(state.posStart);
        try {
            String body = in.getString(state.length(), charsetDecoder(session));

            if (LOG.isDebugEnabled()) {
                LOG.debug("Decoded HL7 from byte stream of length {} to String of length {}", state.length(), body.length());
            }
            out.write(body);
            // Avoid redelivery of scanned message
            state.reset();
        } catch (CharacterCodingException e) {
            throw new RuntimeException(e);
        }
    }

    private CharsetDecoder charsetDecoder(IoSession session) {
        // convert to string using the charset decoder
        CharsetDecoder decoder = (CharsetDecoder)session.getAttribute(CHARSET_DECODER);
        if (decoder == null) {
            decoder = config.getCharset().newDecoder();
            session.setAttribute(CHARSET_DECODER, decoder);
        }
        return decoder;
    }

    /**
     * Scans the buffer for start and end bytes and stores its position in the
     * session state object.
     * 
     * @return <code>true</code> if the end bytes were found, <code>false</code>
     *         otherwise
     */
    private boolean scan(IoSession session, ByteBuffer in) {
        DecoderState state = decoderState(session);
        // Start scanning where we left
        in.position(state.current);
        LOG.debug("Start scanning buffer at position " + in.position());
        while (in.hasRemaining()) {
            byte b = in.get();
            // Check start byte
            if (b == config.getStartByte()) {
                if (state.posStart > 0 || state.waitingForEndByte2) {
                    LOG.warn("Ignoring message start at position " + in.position() + " before previous message has ended.");
                } else {
                    state.posStart = in.position();
                    state.waitingForEndByte2 = false;
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Message starts at position {}", state.posStart);
                    }
                }
            }
            // Check end byte1 
            if (b == config.getEndByte1()) {
                if (!state.waitingForEndByte2 && state.posStart > 0) {
                    state.waitingForEndByte2 = true;
                } else {
                    LOG.warn("Ignoring unexpected 1st end byte " + b + ". Expected 2nd endpoint  " + config.getEndByte2());
                }
            }
            // Check end byte2 
            if (b == config.getEndByte2() && state.waitingForEndByte2) {
                state.posEnd = in.position() - 2; // use -2 to skip these
                                                  // last 2 end markers
                state.waitingForEndByte2 = false;
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Message ends at position {}", state.posEnd);
                }
                break;
            }
        }
        // Remember where we are
        state.current = in.position();
        in.rewind();
        return state.posEnd > 0;
    }

    private DecoderState decoderState(IoSession session) {
        DecoderState decoderState = (DecoderState)session.getAttribute(DECODER_STATE);
        if (decoderState == null) {
            decoderState = new DecoderState();
            session.setAttribute(DECODER_STATE, decoderState);
        }
        return decoderState;
    }

    @Override
    public void dispose(IoSession session) throws Exception {
        session.removeAttribute(CHARSET_DECODER);
        session.removeAttribute(DECODER_STATE);
    }

    /**
     * Holds the state of the decoding process
     */
    private static class DecoderState {
        int posStart;
        int posEnd;
        int current;
        boolean waitingForEndByte2;

        int length() {
            return posEnd - posStart;
        }

        void reset() {
            posStart = 0;
            posEnd = 0;
            waitingForEndByte2 = false;
        }
    }

}
