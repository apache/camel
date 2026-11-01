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

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderException;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HL7MLLPDecoder that is aware that a HL7 message can span several buffers. In addition, it avoids rescanning packets
 * by keeping state in the IOSession.
 */
class HL7MLLPDecoder extends CumulativeProtocolDecoder {

    private static final Logger LOG = LoggerFactory.getLogger(HL7MLLPDecoder.class);
    private static final String DECODER_STATE = HL7MLLPDecoder.class.getName() + ".STATE";
    private static final String CHARSET_DECODER = HL7MLLPDecoder.class.getName() + ".charsetdecoder";

    private HL7MLLPConfig config;

    HL7MLLPDecoder(HL7MLLPConfig config) {
        this.config = config;
        // keep the data of an incomplete message between two reads also on a transport that MINA does not regard as
        // fragmenting (mina:udp, mina:vm): without it MINA hands each read to doDecode on its own, so the positions
        // that this decoder keeps would point into a read that MINA did not keep
        setTransportMetadataFragmentation(false);
    }

    @Override
    protected boolean doDecode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {

        // Get the state of the current message and
        // Skip what we have already scanned before
        DecoderState state = decoderState(session);
        in.position(state.current());

        if (state.isDiscarding() && !skipDiscarded(state, in)) {
            // the rest of a message that was too long: drop what was received, and wait for its end bytes
            LOG.debug("Discarded {} bytes of a message longer than maxFrameLength", in.limit() - state.current());
            in.position(in.limit());
            state.markCurrent(0);
            return false;
        }

        LOG.debug("Received data, checking from position {} to {}", in.position(), in.limit());
        boolean messageDecoded = false;

        while (in.hasRemaining()) {

            int previousPosition = in.position();
            byte current = in.get();

            // Check if we are at the end of an HL7 message
            if (current == config.getEndByte2() && state.previous() == config.getEndByte1()) {
                if (state.isStarted()) {
                    // Save the current buffer pointers and reset them to surround the identifier message
                    int currentPosition = in.position();
                    int currentLimit = in.limit();
                    LOG.debug("Message ends at position {} with length {}", previousPosition,
                            previousPosition - state.start() + 1);
                    in.position(state.start());
                    in.limit(currentPosition);
                    LOG.debug("Set start to position {} and limit to {}", in.position(), in.limit());

                    // Now create string or byte[] from this part of the buffer and restore the buffer pointers
                    try {
                        out.write(config.isProduceString()
                                ? parseMessageToString(in.slice(), charsetDecoder(session))
                                : parseMessageToByteArray(in.slice()));
                        messageDecoded = true;
                    } finally {
                        LOG.debug("Resetting to position {} and limit to {}", currentPosition, currentLimit);
                        in.position(currentPosition);
                        in.limit(currentLimit);
                        state.reset();
                        // the data of the next message starts after these end bytes
                        state.markDataStart(currentPosition);
                    }
                } else {
                    LOG.warn("Ignoring message end at position {} until start byte has been seen.", previousPosition);
                    // the data of the next message starts after these end bytes
                    state.markDataStart(in.position());
                }
            } else {
                // the bytes of the message so far, from the start of its data; an end byte 1 is not counted yet, as the
                // end byte 2 may follow it
                int length = in.position() - state.dataStart() - (current == config.getEndByte1() ? 1 : 0);
                if (length > config.getMaxFrameLength()) {
                    state.markPrevious(current);
                    tooLong(session, in, state);
                }
                // Check if we are at the start of an HL7 message
                if (current == config.getStartByte()) {
                    state.markStart(previousPosition);
                } else {
                    // Remember previous byte in state object because the buffer could
                    // be theoretically exhausted right between the two end bytes
                    state.markPrevious(current);
                }
                messageDecoded = false;
            }
        }

        if (!messageDecoded) {
            // Could not find a complete message in the buffer: return false so that this method is called again
            // with more data. Keep only the data of the current message: MINA keeps the bytes from this position for
            // the next call (so the bytes of the messages decoded before, and of end bytes without a start, do not
            // pile up), and the positions of the state are moved down by as much
            LOG.debug("No complete message yet at position {}", in.position());
            int dataStart = state.dataStart();
            state.markCurrent(in.position() - dataStart);
            state.rebase(dataStart);
            in.position(dataStart);
        } else {
            // the buffer is used up to the end of this message, so the next data starts at the start of the buffer
            state.markDataStart(0);
        }
        return messageDecoded;
    }

    /**
     * Skips the bytes of a message that was too long up to its end bytes.
     *
     * @return true when its end bytes were found, and the data of the next message starts after them
     */
    private boolean skipDiscarded(DecoderState state, IoBuffer in) {
        while (in.hasRemaining()) {
            byte current = in.get();
            if (current == config.getEndByte2() && state.previous() == config.getEndByte1()) {
                LOG.debug("End of the discarded message at position {}", in.position() - 1);
                state.stopDiscarding();
                state.markPrevious((byte) 0);
                state.markDataStart(in.position());
                return true;
            }
            state.markPrevious(current);
        }
        return false;
    }

    /**
     * Discards a message longer than maxFrameLength, with the rest of the data received with it, and fails.
     */
    private void tooLong(IoSession session, IoBuffer in, DecoderState state) throws Exception {
        // the end bytes of this message may already be in the rest of the data: then the next message starts after them
        boolean discarding = !skipDiscarded(state, in);
        byte previous = state.previous();
        in.position(in.limit());
        state.reset();
        state.markDataStart(0);
        state.markPrevious(previous);
        if (discarding) {
            state.startDiscarding();
        }
        // the data is used up: drop the buffer that MINA keeps for this session, so that it is not read again (this
        // decoder throws, so MINA does not do it)
        super.dispose(session);
        throw new FrameTooLongException(
                "HL7 MLLP message is longer than maxFrameLength of " + config.getMaxFrameLength() + " bytes: discarded");
    }

    /**
     * The failure of a message longer than maxFrameLength. It has an empty hex dump, so that MINA does not add the data
     * that was received (the message) to it, and so to the logs (ProtocolCodecFilter dumps the data only when the
     * exception has none).
     */
    static final class FrameTooLongException extends ProtocolDecoderException {
        private static final long serialVersionUID = 1L;

        FrameTooLongException(String message) {
            super(message);
        }

        @Override
        public String getHexdump() {
            return "";
        }
    }

    // Make a defensive byte copy (the buffer will be reused)
    // and omit the start and the two end bytes of the MLLP message
    // returning a byte array
    private Object parseMessageToByteArray(IoBuffer buf) {
        int len = buf.limit() - 3;
        LOG.debug("Making byte array of length {}", len);
        byte[] dst = new byte[len];
        buf.skip(1); // skip start byte
        buf.get(dst, 0, len);
        buf.skip(2); // skip end bytes

        // Only do this if conversion is enabled
        if (config.isConvertLFtoCR()) {
            LOG.debug("Replacing LF by CR");
            for (int i = 0; i < dst.length; i++) {
                if (dst[i] == (byte) '\n') {
                    dst[i] = (byte) '\r';
                }
            }
        }
        return dst;
    }

    // Make a defensive byte copy (the buffer will be reused)
    // and omit the start and the two end bytes of the MLLP message
    // returning a String
    private Object parseMessageToString(IoBuffer buf, CharsetDecoder decoder) throws CharacterCodingException {
        int len = buf.limit() - 3;
        LOG.debug("Making string of length {} using charset {}", len, decoder.charset());
        buf.skip(1); // skip start byte
        String message = buf.getString(len, decoder);
        buf.skip(2); // skip end bytes

        // Only do this if conversion is enabled
        if (config.isConvertLFtoCR()) {
            LOG.debug("Replacing LF by CR");
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
                decoder = config.getCharset().newDecoder()
                        .onMalformedInput(config.getMalformedInputErrorAction())
                        .onUnmappableCharacter(config.getUnmappableCharacterErrorAction());
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
        private int startPos = -1;
        private int currentPos;
        private byte previousByte;
        // where the data of the current message starts in the buffer: after the end bytes of the previous message
        private int dataStartPos;
        // whether the rest of a message that was too long is being skipped
        private boolean discarding;

        void reset() {
            startPos = -1;
            currentPos = 0;
            previousByte = 0;
        }

        void markStart(int position) {
            if (isStarted()) {
                LOG.warn("Ignoring message start at position {} before previous message has ended.", position);
            } else {
                startPos = position;
                LOG.debug("Message starts at position {}", startPos);
            }
        }

        void markCurrent(int position) {
            currentPos = position;
        }

        void markDataStart(int position) {
            dataStartPos = position;
        }

        /** Moves the positions down by {@code offset}, as the first {@code offset} bytes of the buffer are dropped. */
        void rebase(int offset) {
            if (startPos >= 0) {
                startPos -= offset;
            }
            dataStartPos -= offset;
        }

        int dataStart() {
            return dataStartPos;
        }

        void startDiscarding() {
            discarding = true;
        }

        void stopDiscarding() {
            discarding = false;
        }

        boolean isDiscarding() {
            return discarding;
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

        public boolean isStarted() {
            return startPos >= 0;
        }
    }
}
