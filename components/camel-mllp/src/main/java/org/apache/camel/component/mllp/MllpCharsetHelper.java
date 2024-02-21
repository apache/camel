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

}
