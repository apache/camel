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

import java.io.UnsupportedEncodingException;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.preparser.PreParser;
import org.apache.camel.Exchange;
import org.apache.camel.util.IOHelper;

/**
 * This enumerates the defined charsets for HL7 as defined in Table 0211,
 * mapping them to the Java charset names and back
 */
public enum HL7Charset {

    ISO_8859_1("8859/1", "ISO-8859-1"),
    ISO_8859_2("8859/2", "ISO-8859-2"),
    ISO_8859_3("8859/3", "ISO-8859-3"),
    ISO_8859_4("8859/4", "ISO-8859-4"),
    ISO_8859_5("8859/5", "ISO-8859-5"),
    ISO_8859_6("8859/1", "ISO-8859-6"),
    ISO_8859_7("8859/1", "ISO-8859-7"),
    ISO_8859_8("8859/1", "ISO-8859-8"),
    ISO_8859_9("8859/1", "ISO-8859-9"),
    ASCII("ASCII", "US-ASCII"),
    BIG_5("BIG-5", "Big5"),
    CNS("CNS 11643-1992", "ISO-2022-CN"),
    GB_1830_2000("GB 18030-2000", ""),
    ISO_IR14("ISO IR14", "ISO-2022-JP"),
    ISO_IR159("ISO IR159", "EUC-JP"),
    ISO_IR87("ISO IR87", "EUC-JP"),
    KS_X_1001("KS X 1001", "EUC-KR"),
    UNICODE("UNICODE", "UTF-8"),
    UTF_16("UNICODE UTF-16", "UTF-16"),
    UTF_32("UNICODE UTF-32", "UTF-32"),
    UTF_8("UNICODE UTF-8", "UTF-8");

    private String hl7CharsetName;
    private String javaCharsetName;

    HL7Charset(String hl7CharsetName, String javaCharsetName) {
        this.hl7CharsetName = hl7CharsetName;
        this.javaCharsetName = javaCharsetName;
    }

    public String getHL7CharsetName() {
        return hl7CharsetName;
    }

    public String getJavaCharsetName() {
        return javaCharsetName;
    }

    /**
     * Returns the HL7Charset that matches the parameter
     *
     * @param s charset string
     * @return HL7Charset enum
     */
    public static HL7Charset getHL7Charset(String s) {
        if (s != null && s.length() > 0) {
            for (HL7Charset charset : HL7Charset.values()) {
                if (charset.hl7CharsetName.equals(s) || charset.javaCharsetName.equals(s)) {
                    return charset;
                }
            }
        }
        return null;
    }

    /**
     * Returns the charset to be used for marshalling HL7 messages. If MSH-18 is empty,
     * the charset configured in Camel's charset properties/headers is returned.
     *
     * @param message HL7 message
     * @param exchange Exchange
     * @return Java charset name
     */
    public static String getCharsetName(Message message, Exchange exchange) throws HL7Exception {
        String defaultCharsetName = IOHelper.getCharsetName(exchange);
        String msh18 = ((Segment)message.get("MSH")).getField(18, 0).toString();
        return getCharsetName(msh18, defaultCharsetName);
    }

    /**
     * Returns the charset to be used for unmarshalling HL7 messages. If MSH-18 is empty,
     * the temporary charset name is returned.
     *
     * @param bytes HL7 message as byte array
     * @param guessedCharsetName the temporary charset guessed to be able to read MSH-18
     * @return Java charset name
     *
     * @see org.apache.camel.component.hl7.HL7DataFormat#guessCharsetName(byte[], org.apache.camel.Exchange)
     */
    public static String getCharsetName(byte[] bytes, String guessedCharsetName) throws UnsupportedEncodingException, HL7Exception {
        String tmp = new String(bytes, guessedCharsetName);
        String msh18 = PreParser.getFields(tmp, "MSH-18")[0];
        return getCharsetName(msh18, guessedCharsetName);
    }

    private static String getCharsetName(String msh18, String defaultCharsetName) {
        HL7Charset charset = HL7Charset.getHL7Charset(msh18);
        return charset != null ? charset.getJavaCharsetName() : defaultCharsetName;
    }
}
