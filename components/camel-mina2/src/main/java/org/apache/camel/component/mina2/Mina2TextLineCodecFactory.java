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
package org.apache.camel.component.mina2;

import java.nio.charset.Charset;

import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.textline.LineDelimiter;
import org.apache.mina.filter.codec.textline.TextLineDecoder;
import org.apache.mina.filter.codec.textline.TextLineEncoder;

/**
 * Text line codec that supports setting charset and delimiter.
 * <p/>
 * Uses Mina's default TextLineEncoder and TextLineDncoder. 
 */
public class Mina2TextLineCodecFactory implements ProtocolCodecFactory {

    private TextLineEncoder encoder;
    private TextLineDecoder decoder;

    public Mina2TextLineCodecFactory(Charset charset, LineDelimiter delimiter) {
        if (delimiter.equals(LineDelimiter.AUTO)) {
            // AUTO not supported by encoder
            encoder = new TextLineEncoder(charset);
        } else {
            encoder = new TextLineEncoder(charset, delimiter);
        }
        decoder = new TextLineDecoder(charset, delimiter);
    }

    public ProtocolEncoder getEncoder(IoSession session) throws Exception {
        return encoder;
    }

    public ProtocolDecoder getDecoder(IoSession session) throws Exception {
        return decoder;
    }

    public void setEncoderMaxLineLength(int encoderMaxLineLength) {
        encoder.setMaxLineLength(encoderMaxLineLength);
    }

    public int getEncoderMaxLineLength() {
        return encoder.getMaxLineLength();
    }

    public void setDecoderMaxLineLength(int decoderMaxLineLength) {
        decoder.setMaxLineLength(decoderMaxLineLength);
    }

    public int getDecoderMaxLineLength() {
        return decoder.getMaxLineLength();
    }
}
