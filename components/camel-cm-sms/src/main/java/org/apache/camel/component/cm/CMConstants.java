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
package org.apache.camel.component.cm;

public interface CMConstants {

    String DEFAULT_SCHEME = "https://";

    int DEFAULT_MULTIPARTS = 8;

    int MAX_UNICODE_MESSAGE_LENGTH = 70;
    int MAX_GSM_MESSAGE_LENGTH = 160;
    int MAX_UNICODE_MESSAGE_LENGTH_PER_PART_IF_MULTIPART = 67;
    int MAX_GSM_MESSAGE_LENGTH_PER_PART_IF_MULTIPART = 153;

    // status code 200 - Error substrings - check it contains.
    String ERROR_UNKNOWN = "Unknown error";
    String ERROR_NO_ACCOUNT = "No account found";
    String ERROR_INSUFICIENT_BALANCE = "Insufficient balance";
    String ERROR_UNROUTABLE_MESSAGE = "Message is unroutable";
    String ERROR_INVALID_PRODUCT_TOKEN = "Invalid product token";

    // TODO: Review this pattern.
    // or it should be foundnd an alternative to jcharset to check if a message is GSM 03.38 encodable
    // See:
    // https://en.wikipedia.org/wiki/GSM_03.38
    // http://frightanic.com/software-development/regex-for-gsm-03-38-7bit-character-set/
    String GSM_0338_REGEX = "^[A-Za-z0-9 \\r\\n@£$\u0394_\u03A6\u0393\u039B\u03A9\u03A0\u03A8\u03A3\u0398\u039E!\"#$%&amp;'()*+,\\-./:;&lt;=&gt;?¡¿^{}\\\\\\[~\\]|"
                            + "\u20AC\u00a5\u00e8\u00e9\u00f9\u00ec\u00f2\u00c7\u00d8\u00f8\u00c5\u00e5\u00c6\u00e6\u00df\u00c9\u00c4\u00d6\u00d1\u00dc\u00a7\u00e4\u00f6\u00f1\u00fc\u00e0]*$";
}
