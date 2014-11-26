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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.UnmappableCharacterException;

import org.apache.camel.Message;
import org.jsmpp.bean.Alphabet;
import org.jsmpp.session.SMPPSession;

public abstract class SmppSmCommand extends AbstractSmppCommand {

    protected Charset ascii = Charset.forName("US-ASCII");
    protected Charset latin1 = Charset.forName("ISO-8859-1");

    protected Charset charset;

    public SmppSmCommand(SMPPSession session, SmppConfiguration config) {
        super(session, config);
        this.charset = Charset.forName(config.getEncoding());
    }

    protected SmppSplitter createSplitter(Message message) {
        Alphabet alphabet = determineAlphabet(message);

        String body = message.getBody(String.class);

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

    protected final byte[] getShortMessage(Message message) {
        if (has8bitDataCoding(message)) {
            return message.getBody(byte[].class);
        } else {
            byte providedAlphabet = getProvidedAlphabet(message);
            Alphabet determinedAlphabet = determineAlphabet(message);
            Charset charset = determineCharset(providedAlphabet, determinedAlphabet.value());
            String body = message.getBody(String.class);
            return body.getBytes(charset);
        }
    }

    private static boolean has8bitDataCoding(Message message) {
        Byte dcs = message.getHeader(SmppConstants.DATA_CODING, Byte.class);
        if (dcs != null) {
            return SmppUtils.parseAlphabetFromDataCoding(dcs.byteValue()) == Alphabet.ALPHA_8_BIT;
        } else {
            Byte alphabet = message.getHeader(SmppConstants.ALPHABET, Byte.class);
            return alphabet != null && alphabet.equals(Alphabet.ALPHA_8_BIT.value());
        }
    }

    private byte getProvidedAlphabet(Message message) {
        byte alphabet = config.getAlphabet();
        if (message.getHeaders().containsKey(SmppConstants.ALPHABET)) {
            alphabet = message.getHeader(SmppConstants.ALPHABET, Byte.class);
        }

        return alphabet;
    }

    private Charset determineCharset(byte providedAlphabet, byte determinedAlphabet) {
        if (providedAlphabet == Alphabet.ALPHA_UCS2.value() 
            || (providedAlphabet == SmppConstants.UNKNOWN_ALPHABET && determinedAlphabet == Alphabet.ALPHA_UCS2.value())) {
            // change charset to use multilang messages
            return Charset.forName(SmppConstants.UCS2_ENCODING); 
        }
        
        return charset;
    }

    private Alphabet determineAlphabet(Message message) {
        String body = message.getBody(String.class);
        byte alphabet = getProvidedAlphabet(message);

        Alphabet alphabetObj;
        if (alphabet == SmppConstants.UNKNOWN_ALPHABET) {
            alphabetObj = Alphabet.ALPHA_UCS2;
            if (config.getSmscDefaultEncoding().equals("GSM-7BIT")) {
                if (charset.equals(ascii) || charset.equals(latin1)) {
                    byte[] messageBytes = body.getBytes(charset);
                    if (SmppUtils.isGsm0338Encodeable(messageBytes)) {
                        alphabetObj = Alphabet.ALPHA_DEFAULT;
                    }
                }
            } else if (config.getSmscDefaultEncoding().equals("Latin1")) {
                CharBuffer _body = CharBuffer.wrap(body);
                CharsetEncoder enc = latin1.newEncoder();
                enc.onUnmappableCharacter(CodingErrorAction.REPORT);
                try {
                    ByteBuffer result = enc.encode(_body);
                    alphabetObj = Alphabet.ALPHA_LATIN1;  // FIXME - needs patch to jSMPP (pull req #39)
                } catch(UnmappableCharacterException ex) {
                    // This exception occurs if the message contains a
                    // character that can't be represented in Latin-1.
                    // Will fall through to default UCS2.
                } catch(CharacterCodingException ex) {
                    // This indicates some more serious but unexpected failure.
                    // Will fall through to default UCS2.
                }
            }

            // if the alphabet was not explicitly specified and was detected automatically,
            // record it in a header for use elsewhere
            message.setHeader(SmppConstants.ALPHABET_DETECTED, alphabetObj.value());

        } else {
            alphabetObj = Alphabet.valueOf(alphabet);
        }

        return alphabetObj;
    }
}
