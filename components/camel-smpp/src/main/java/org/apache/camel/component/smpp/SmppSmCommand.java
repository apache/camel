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

import org.apache.camel.Message;
import org.jsmpp.bean.Alphabet;
import org.jsmpp.extra.NegativeResponseException;
import org.jsmpp.session.SMPPSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SmppSmCommand extends AbstractSmppCommand {

    // FIXME: these constants should be defined somewhere in jSMPP:
    public static final int SMPP_NEG_RESPONSE_MSG_TOO_LONG = 1;

    protected Charset ascii = Charset.forName("US-ASCII");
    protected Charset latin1 = Charset.forName("ISO-8859-1");
    protected Charset defaultCharset;

    private final Logger logger = LoggerFactory.getLogger(SmppSmCommand.class);

    public SmppSmCommand(SMPPSession session, SmppConfiguration config) {
        super(session, config);
        defaultCharset = Charset.forName(config.getEncoding());
    }

    protected byte[][] splitBody(Message message) throws SmppException {
        byte[] shortMessage = getShortMessage(message);
        SmppSplitter splitter = createSplitter(message);
        byte[][] segments = splitter.split(shortMessage);
        if (segments.length > 1) {
            // Message body is split into multiple parts,
            // check if this is permitted
            SmppSplittingPolicy policy = getSplittingPolicy(message);
            switch(policy) {
            case ALLOW:
                return segments;
            case TRUNCATE:
                return new byte[][] {java.util.Arrays.copyOfRange(shortMessage, 0, segments[0].length)};
            case REJECT:
                // FIXME - JSMPP needs to have an enum of the negative response
                // codes instead of just using them like this
                NegativeResponseException nre = new NegativeResponseException(SMPP_NEG_RESPONSE_MSG_TOO_LONG);
                throw new SmppException(nre);
            default:
                throw new SmppException("Unknown splitting policy: " + policy);
            }
        } else {
            return segments;
        }
    }

    private SmppSplittingPolicy getSplittingPolicy(Message message) throws SmppException {
        if (message.getHeaders().containsKey(SmppConstants.SPLITTING_POLICY)) {
            String policyName = message.getHeader(SmppConstants.SPLITTING_POLICY, String.class);
            return SmppSplittingPolicy.fromString(policyName);
        }
        return config.getSplittingPolicy();
    }

    protected SmppSplitter createSplitter(Message message) throws SmppException {

        SmppSplitter splitter;
        // use the splitter if provided via header
        if (message.getHeaders().containsKey(SmppConstants.DATA_SPLITTER)) {
            splitter = message.getHeader(SmppConstants.DATA_SPLITTER, SmppSplitter.class);
            if (null != splitter) {
                return splitter;
            }
            throw new SmppException("Invalid splitter given. Must be instance of SmppSplitter");
        }
        Alphabet alphabet = determineAlphabet(message);
        String body = message.getBody(String.class);

        if (SmppUtils.is8Bit(alphabet)) {
            splitter = new Smpp8BitSplitter(body.length());
        } else if (alphabet == Alphabet.ALPHA_UCS2) {
            splitter = new SmppUcs2Splitter(body.length());
        } else {
            splitter = new SmppDefaultSplitter(body.length());
        }
        return splitter;
    }

    protected final byte[] getShortMessage(Message message) {
        if (has8bitDataCoding(message)) {
            return message.getBody(byte[].class);
        } else {
            byte providedAlphabet = getProvidedAlphabet(message);
            Alphabet determinedAlphabet = determineAlphabet(message);
            Charset charset = determineCharset(message, providedAlphabet, determinedAlphabet.value());
            String body = message.getBody(String.class);
            return body.getBytes(charset);
        }
    }

    private static boolean has8bitDataCoding(Message message) {
        Byte dcs = message.getHeader(SmppConstants.DATA_CODING, Byte.class);
        if (dcs != null) {
            return SmppUtils.is8Bit(Alphabet.parseDataCoding(dcs.byteValue()));
        } else {
            Byte alphabet = message.getHeader(SmppConstants.ALPHABET, Byte.class);
            return alphabet != null && SmppUtils.is8Bit(Alphabet.valueOf(alphabet));
        }
    }

    private byte getProvidedAlphabet(Message message) {
        byte alphabet = config.getAlphabet();
        if (message.getHeaders().containsKey(SmppConstants.ALPHABET)) {
            alphabet = message.getHeader(SmppConstants.ALPHABET, Byte.class);
        }

        return alphabet;
    }

    private Charset getCharsetForMessage(Message message) {
        if (message.getHeaders().containsKey(SmppConstants.ENCODING)) {
            String encoding = message.getHeader(SmppConstants.ENCODING, String.class);
            if (Charset.isSupported(encoding)) {
                return Charset.forName(encoding);
            } else {
                logger.warn("Unsupported encoding \"{}\" requested in header.", encoding);
            }
        }
        return null;
    }

    private Charset determineCharset(Message message, byte providedAlphabet, byte determinedAlphabet) {
        Charset result = getCharsetForMessage(message);
        if (result != null) {
            return result;
        }

        if (providedAlphabet == Alphabet.ALPHA_UCS2.value() 
            || (providedAlphabet == SmppConstants.UNKNOWN_ALPHABET && determinedAlphabet == Alphabet.ALPHA_UCS2.value())) {
            // change charset to use multilang messages
            return Charset.forName(SmppConstants.UCS2_ENCODING); 
        }
        
        return defaultCharset;
    }

    private Alphabet determineAlphabet(Message message) {
        String body = message.getBody(String.class);
        byte alphabet = getProvidedAlphabet(message);
        Charset charset = getCharsetForMessage(message);
        if (charset == null) {
            charset = defaultCharset;
        }

        Alphabet alphabetObj;
        if (alphabet == SmppConstants.UNKNOWN_ALPHABET) {
            alphabetObj = Alphabet.ALPHA_UCS2;
            if (isLatin1Compatible(charset)) {
                byte[] messageBytes = body.getBytes(charset);
                if (SmppUtils.isGsm0338Encodeable(messageBytes)) {
                    alphabetObj = Alphabet.ALPHA_DEFAULT;
                }
            }
        } else {
            alphabetObj = Alphabet.valueOf(alphabet);
        }

        return alphabetObj;
    }

    private boolean isLatin1Compatible(Charset c) {
        if (c.equals(ascii) || c.equals(latin1)) {
            return true;
        }
        return false;
    }
}
