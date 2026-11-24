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
package org.apache.camel.component.mllp;

import java.nio.charset.Charset;

import org.apache.camel.Exchange;
import org.apache.camel.component.mllp.internal.Hl7Util;
import org.apache.camel.support.ExchangeHelper;

public final class MllpCharsetHelper {

    private MllpCharsetHelper() {
    }

    public static Charset getCharset(Exchange exchange, Charset defaultCharset) {
        String exchangeCharsetName = ExchangeHelper.getCharsetName(exchange, false);
        if (exchangeCharsetName != null && !exchangeCharsetName.isEmpty()) {
            try {
                if (Charset.isSupported(exchangeCharsetName)) {
                    return Charset.forName(exchangeCharsetName);
                }
            } catch (Exception charsetEx) {
                // ignore
            }
        }

        return defaultCharset;
    }

    public static Charset getCharset(Exchange exchange, byte[] hl7Bytes, Hl7Util hl7Util, Charset defaultCharset) {
        Charset msh18Charset = findMsh18Charset(hl7Bytes, hl7Util, defaultCharset);
        if (msh18Charset != null) {
            return msh18Charset;
        }

        return getCharset(exchange, defaultCharset);
    }

    /**
     * The charset of an HL7 message: the charset named in MSH-18, or the given charset when the message has no MSH-18
     * or MSH-18 names a charset that is not supported. The CamelCharsetName header and exchange property are not used,
     * as they may come from another endpoint.
     *
     * @param  hl7Bytes       the HL7 message
     * @param  hl7Util        the HL7 utility to find MSH-18 with
     * @param  defaultCharset the charset to use when the message has no (supported) MSH-18
     * @return                the charset of the message
     */
    public static Charset getMsh18Charset(byte[] hl7Bytes, Hl7Util hl7Util, Charset defaultCharset) {
        Charset msh18Charset = findMsh18Charset(hl7Bytes, hl7Util, defaultCharset);
        return msh18Charset != null ? msh18Charset : defaultCharset;
    }

    /**
     * Encodes an HL7 message in its charset: the charset named in MSH-18, or the given charset when the message has no
     * MSH-18 or MSH-18 names a charset that is not supported.
     *
     * @param  hl7Message     the HL7 message
     * @param  hl7Util        the HL7 utility to find MSH-18 with
     * @param  defaultCharset the charset to use when the message has no (supported) MSH-18
     * @return                the encoded message
     */
    public static byte[] encode(String hl7Message, Hl7Util hl7Util, Charset defaultCharset) {
        byte[] hl7Bytes = hl7Message.getBytes(defaultCharset);
        Charset msh18Charset = findMsh18Charset(hl7Bytes, hl7Util, defaultCharset);
        if (msh18Charset != null && !msh18Charset.equals(defaultCharset)) {
            hl7Bytes = hl7Message.getBytes(msh18Charset);
        }
        return hl7Bytes;
    }

    private static Charset findMsh18Charset(byte[] hl7Bytes, Hl7Util hl7Util, Charset defaultCharset) {
        String msh18 = hl7Util.findMsh18(hl7Bytes, defaultCharset);
        if (msh18 != null && !msh18.isEmpty()) {
            if (MllpProtocolConstants.MSH18_VALUES.containsKey(msh18)) {
                return MllpProtocolConstants.MSH18_VALUES.get(msh18);
            }
            try {
                if (Charset.isSupported(msh18)) {
                    return Charset.forName(msh18);
                }
            } catch (Exception charsetEx) {
                // ignore
            }
        }
        return null;
    }

}
