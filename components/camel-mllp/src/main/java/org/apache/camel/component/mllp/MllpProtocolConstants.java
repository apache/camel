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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Constants for the MLLP Protocol and the Camel MLLP component.
 */
public final class MllpProtocolConstants {
    public static final char START_OF_BLOCK = 0x0b;      // VT (vertical tab)        - decimal 11, octal 013
    public static final char END_OF_BLOCK = 0x1c;        // FS (file separator)      - decimal 28, octal 034
    public static final char END_OF_DATA = 0x0d;         // CR (carriage return)     - decimal 13, octal 015
    public static final int END_OF_STREAM = -1;          //
    public static final char SEGMENT_DELIMITER = 0x0d;   // CR (carriage return)     - decimal 13, octal 015
    public static final char MESSAGE_TERMINATOR = 0x0a;  // LF (line feed, new line) - decimal 10, octal 012

    public static final byte[] PAYLOAD_TERMINATOR;

    public static final Map<String, Charset> MSH18_VALUES;

    static {
        PAYLOAD_TERMINATOR = new byte[2];
        PAYLOAD_TERMINATOR[0] = MllpProtocolConstants.END_OF_BLOCK;
        PAYLOAD_TERMINATOR[1] = MllpProtocolConstants.END_OF_DATA;

        MSH18_VALUES = new HashMap<>(15);
        MSH18_VALUES.put("ASCII", StandardCharsets.US_ASCII);
        MSH18_VALUES.put("8859/1", StandardCharsets.ISO_8859_1);
        MSH18_VALUES.put("8859/2", Charset.forName("ISO-8859-2"));
        MSH18_VALUES.put("8859/3", Charset.forName("ISO-8859-3"));
        MSH18_VALUES.put("8859/4", Charset.forName("ISO-8859-4"));
        MSH18_VALUES.put("8859/5", Charset.forName("ISO-8859-5"));
        MSH18_VALUES.put("8859/6", Charset.forName("ISO-8859-6"));
        MSH18_VALUES.put("8859/7", Charset.forName("ISO-8859-7"));
        MSH18_VALUES.put("8859/8", Charset.forName("ISO-8859-8"));
        MSH18_VALUES.put("8859/9", Charset.forName("ISO-8859-9"));
        MSH18_VALUES.put("8859/15", Charset.forName("ISO-8859-15"));
        MSH18_VALUES.put("UNICODE UTF-8", StandardCharsets.UTF_8);

        /*
          These values are defined in the HL7 Spec, but currently not mapped to a Java charset

          JAS2020
          JIS X 0202
          ISO IR6
          ISO IR14
          ISO IR87
          ISO IR159
          GB 18030-2000
          KS X 1001
          CNS 11643-1992
          BIG-5
          UNICODE
          UNICODE UTF-16
          UNICODE UTF-32

          see: https://terminology.hl7.org/CodeSystem-v2-0211.html
        */
    }

    private MllpProtocolConstants() {
        //utility class, never constructed
    }
}
