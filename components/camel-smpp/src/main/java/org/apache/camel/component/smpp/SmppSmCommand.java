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
package org.apache.camel.component.smpp;

import java.nio.charset.Charset;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.jsmpp.bean.Alphabet;
import org.jsmpp.session.SMPPSession;

public abstract class SmppSmCommand extends AbstractSmppCommand {

    protected Charset charset;

    public SmppSmCommand(SMPPSession session, SmppConfiguration config) {
        super(session, config);
        this.charset = Charset.forName(config.getEncoding());
    }

    protected byte getProvidedAlphabet(Exchange exchange) {
        Message in = exchange.getIn();

        byte alphabet = config.getAlphabet();
        if (in.getHeaders().containsKey(SmppConstants.ALPHABET)) {
            alphabet = in.getHeader(SmppConstants.ALPHABET, Byte.class);
        }

        return alphabet;
    }

    protected Charset determineCharset(byte providedAlphabet, byte determinedAlphabet) {
        if (providedAlphabet == SmppConstants.UNKNOWN_ALPHABET && determinedAlphabet == Alphabet.ALPHA_UCS2.value()) {
            return Charset.forName(SmppConstants.UCS2_ENCODING); // change charset to use multilang messages
        }
        
        return charset;
    }

    protected Alphabet determineAlphabet(Exchange exchange) {
        String body = exchange.getIn().getBody(String.class);
        byte alphabet = getProvidedAlphabet(exchange);

        Alphabet alphabetObj;
        if (alphabet == SmppConstants.UNKNOWN_ALPHABET) {
            byte[] message = body.getBytes(charset);
            if (SmppUtils.isGsm0338Encodeable(message)) {
                alphabetObj = Alphabet.ALPHA_DEFAULT;
            } else {
                alphabetObj = Alphabet.ALPHA_UCS2;
            }
        } else {
            alphabetObj = Alphabet.valueOf(alphabet);
        }

        return alphabetObj;
    }

    protected SmppSplitter createSplitter(Exchange exchange) {
        Alphabet alphabet = determineAlphabet(exchange);

        Message in = exchange.getIn();
        String body = in.getBody(String.class);

        SmppSplitter splitter;
        switch (alphabet) {
        case ALPHA_8_BIT:
            splitter = new Smpp8BitSplitter(body.length());
            break;
        case ALPHA_UCS2:
            splitter = new SmppUcs2Splitter(body.length());
            break;
        case ALPHA_DEFAULT:
        default:
            splitter = new SmppDefaultSplitter(body.length());
            break;
        }

        return splitter;
    }
}