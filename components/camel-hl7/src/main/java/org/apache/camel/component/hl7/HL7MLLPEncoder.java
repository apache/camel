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

import ca.uhn.hl7v2.model.Message;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HL7 MLLP encoder
 */
class HL7MLLPEncoder implements ProtocolEncoder {

    private static final Logger LOG = LoggerFactory.getLogger(HL7MLLPEncoder.class);

    private HL7MLLPConfig config;

    HL7MLLPEncoder(HL7MLLPConfig config) {
        this.config = config;
    }

    @Override
    public void dispose(IoSession session) throws Exception {
    }

    public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception {
        if (message == null) {
            throw new IllegalArgumentException("Message to be encoded is null");
        } else if (message instanceof Exception) {
            // we cannot handle exceptions
            throw (Exception)message;
        }

        byte[] body;
        if (message instanceof Message) {
            body = ((Message) message).encode().getBytes(config.getCharset());
        } else if (message instanceof String) {
            body = ((String) message).getBytes(config.getCharset());
        } else if (message instanceof byte[]) {
            body = (byte[])message;
        } else {
            throw new IllegalArgumentException("The message to encode is not a supported type: "
                                               + message.getClass().getCanonicalName());
        }

        // put the data into the byte buffer
        IoBuffer buf = IoBuffer.allocate(body.length + 3).setAutoExpand(true);
        buf.put((byte)config.getStartByte());
        buf.put(body);
        buf.put((byte)config.getEndByte1());
        buf.put((byte)config.getEndByte2());

        // flip the buffer so we can use it to write to the out stream
        buf.flip();
        LOG.debug("Encoded HL7 from {} to byte stream", message.getClass().getCanonicalName());
        out.write(buf);
    }

}
