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

import java.nio.charset.CharsetEncoder;
import ca.uhn.hl7v2.model.Message;
import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HL7 MLLP encoder
 */
class HL7MLLPEncoder implements ProtocolEncoder {

    private static final transient Logger LOG = LoggerFactory.getLogger(HL7MLLPEncoder.class);

    private static final String CHARSET_ENCODER = HL7MLLPCodec.class.getName() + ".charsetencoder";

    private HL7MLLPConfig config;

    HL7MLLPEncoder(HL7MLLPConfig config) {
        super();
        this.config = config;
    }

    public void dispose(IoSession session) throws Exception {
        session.removeAttribute(CHARSET_ENCODER);
    }

    public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception {
        if (message == null) {
            throw new IllegalArgumentException("Message to encode is null");
        } else if (message instanceof Exception) {
            // we cannot handle exceptions
            throw (Exception)message;
        }

        CharsetEncoder encoder = (CharsetEncoder)session.getAttribute(CHARSET_ENCODER);
        if (encoder == null) {
            encoder = config.getCharset().newEncoder();
            session.setAttribute(CHARSET_ENCODER, encoder);
        }

        // convert to string
        String body;
        if (message instanceof Message) {
            body = HL7Converter.encode((Message)message, config.isValidate());
        } else if (message instanceof String) {
            body = (String)message;
        } else if (message instanceof byte[]) {
            body = new String((byte[])message);
        } else {
            throw new IllegalArgumentException("The message to encode is not a supported type: "
                                               + message.getClass().getCanonicalName());
        }

        // replace \n with \r as HL7 uses 0x0d = \r as segment termninators
        if (config.isConvertLFtoCR()) {
            body = body.replace('\n', '\r');
        }

        // put the data into the byte buffer
        ByteBuffer buf = ByteBuffer.allocate(body.length() + 3).setAutoExpand(true);
        buf.put((byte)config.getStartByte());
        buf.putString(body, encoder);
        buf.put((byte)config.getEndByte1());
        buf.put((byte)config.getEndByte2());

        // flip the buffer so we can use it to write to the out stream
        buf.flip();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Encoding HL7 from {} to byte stream", message.getClass().getCanonicalName());
        }
        out.write(buf);
    }

}
