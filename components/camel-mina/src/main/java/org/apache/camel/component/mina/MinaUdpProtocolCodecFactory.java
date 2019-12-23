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
package org.apache.camel.component.mina;

import java.nio.charset.CharacterCodingException;

import org.apache.camel.CamelContext;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;

public class MinaUdpProtocolCodecFactory implements ProtocolCodecFactory {

    private final CamelContext context;

    public MinaUdpProtocolCodecFactory(CamelContext context) {
        this.context = context;
    }

    @Override
    public ProtocolEncoder getEncoder(IoSession session) throws Exception {
        return new ProtocolEncoder() {

            public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception {
                IoBuffer buf = toIoBuffer(message);
                buf.flip();
                out.write(buf);
            }

            public void dispose(IoSession session) throws Exception {
                // do nothing
            }
        };
    }

    @Override
    public ProtocolDecoder getDecoder(IoSession session) throws Exception {
        return new ProtocolDecoder() {

            public void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
                // convert to bytes to write, we can not pass in the byte buffer as it could be sent to
                // multiple mina sessions so we must convert it to bytes
                byte[] bytes = context.getTypeConverter().mandatoryConvertTo(byte[].class, in);
                out.write(bytes);
            }

            public void finishDecode(IoSession session, ProtocolDecoderOutput out) throws Exception {
                // do nothing
            }

            public void dispose(IoSession session) throws Exception {
                // do nothing
            }
        };
    }

    private IoBuffer toIoBuffer(Object message) throws CharacterCodingException, NoTypeConversionAvailableException {
        //try to convert it to a byte array
        byte[] value = context.getTypeConverter().tryConvertTo(byte[].class, message);
        if (value != null) {
            IoBuffer answer = IoBuffer.allocate(value.length).setAutoExpand(true);
            answer.put(value);
            return answer;
        }

        // fallback to use a byte buffer converter
        return context.getTypeConverter().mandatoryConvertTo(IoBuffer.class, message);
    }
}
