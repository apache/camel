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

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HL7MLLPDecoder that is aware that a HL7 message can span several buffers.
 * In addition, it avoids rescanning packets by keeping state in the IOSession.
 */
class HL7MLLPDecoder extends CumulativeProtocolDecoder {

    private static final Logger LOG = LoggerFactory.getLogger(HL7MLLPDecoder.class);
    private static final String DECODER_STATE = HL7MLLPDecoder.class.getName() + ".STATE";
    private static final String CHARSET_DECODER = HL7MLLPDecoder.class.getName() + ".charsetdecoder";

    private HL7MLLPConfig config;

    HL7MLLPDecoder(HL7MLLPConfig config) {
        this.config = config;
    }


    @Override
    protected boolean doDecode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) {

        // Get the state of the current message and
        // Skip what we have already scanned
        DecoderState state = decoderState(session);
        in.position(state.current());

        while (in.hasRemaining()) {
            byte current = in.get();

            // If it is the start byte and mark the position
            if (current == config.getStartByte()) {
                state.markStart(in.position() - 1);
            }
            // If it is the end bytes, extract the payload and return
            if (state.previous() == config.getEndByte1() && current == config.getEndByte2()) {

                // Remember the current position and limit.
                int position = in.position();
                int limit = in.limit();
                LOG.debug("Message ends at position {} with length {}",
                        position, position - state.start());
                try {
                    in.position(state.start());
                    in.limit(position);
                    // The bytes between in.position() and in.limit()
                    // now contain a full MLLP message including the
                    // start and end bytes.
                    out.write(config.isProduceString()
                            ? parseMessageToString(in.slice(), charsetDecoder(session))
                            : parseMessageToByteArray(in.slice()));
                } catch (CharacterCodingException cce) {
                    throw new IllegalArgumentException("Exception while finalizing the message", cce);
                } finally {
                    // Reset position, limit, and state
                    in.limit(limit);
                    in.position(position);
                    state.reset();
                }
                return true;
            }
            // Remember previous byte in state object because the buffer could
            // be theoretically exhausted right between the two end bytes
            state.markPrevious(current);
        }

        // Could not find a complete message in the buffer.
        // Reset to the initial position and return false so that this method
        // is called again with more data.
        LOG.debug("No complete message yet at position {} ", in.position());
        state.markCurrent(in.position());
        in.position(0);
        return false;
    }

    // Make a defensive byte copy (the buffer will be reused)
    // and omit the start and the two end bytes of the MLLP message
    // returning a byte array
    private Object parseMessageToByteArray(IoBuffer slice) throws CharacterCodingException {
        byte[] dst = new byte[slice.limit() - 3];
        slice.skip(1); // skip start byte
        slice.get(dst, 0, dst.length);

        // Only do this if conversion is enabled
        if (config.isConvertLFtoCR()) {
            for (int i = 0; i < dst.length; i++) {
                if (dst[i] == (byte)'\n') {
                    dst[i] = (byte)'\r';
                }
            }
        }
        return dst;
    }

    // Make a defensive byte copy (the buffer will be reused)
    // and omit the start and the two end bytes of the MLLP message
    // returning a String
    private Object parseMessageToString(IoBuffer slice, CharsetDecoder decoder) throws CharacterCodingException {
        slice.skip(1); // skip start byte
        String message = slice.getString(slice.limit() - 3, decoder);

        // Only do this if conversion is enabled
        if (config.isConvertLFtoCR()) {
            message = message.replace('\n', '\r');
        }
        return message;
    }

    @Override
    public void dispose(IoSession session) throws Exception {
        session.removeAttribute(DECODER_STATE);
        session.removeAttribute(CHARSET_DECODER);
    }

    private CharsetDecoder charsetDecoder(IoSession session) {
        synchronized (session) {
            CharsetDecoder decoder = (CharsetDecoder) session.getAttribute(CHARSET_DECODER);
            if (decoder == null) {
                decoder = config.getCharset().newDecoder();
                session.setAttribute(CHARSET_DECODER, decoder);
            }
            return decoder;
        }
    }

    private DecoderState decoderState(IoSession session) {
        synchronized (session) {
            DecoderState decoderState = (DecoderState) session.getAttribute(DECODER_STATE);
            if (decoderState == null) {
                decoderState = new DecoderState();
                session.setAttribute(DECODER_STATE, decoderState);
            }
            return decoderState;
        }
    }

    /**
     * Holds the state of the decoding process
     */
    private static class DecoderState {
        private int startPos;
        private int currentPos;
        private byte previousByte;
        private boolean started;

        void reset() {
            startPos = 0;
            currentPos = 0;
            started = false;
            previousByte = 0;
        }

        void markStart(int position) {
            if (started) {
                LOG.warn("Ignoring message start at position {} before previous message has ended.", position);
            } else {
                startPos = position;
                LOG.debug("Message starts at position {}", startPos);
                started = true;
            }
        }

        void markCurrent(int position) {
            currentPos = position;
        }

        void markPrevious(byte previous) {
            previousByte = previous;
        }

        public int start() {
            return startPos;
        }

        public int current() {
            return currentPos;
        }

        public byte previous() {
            return previousByte;
        }
    }

}
