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
package org.apache.camel.component.cm;

public interface CMConstants {

    String DEFAULT_SCHEME = "https://";

    int DEFAULT_MULTIPARTS = 8;

    int MAX_UNICODE_MESSAGE_LENGTH = 70;
    int MAX_GSM_MESSAGE_LENGTH = 160;
    int MAX_UNICODE_MESSAGE_LENGTH_PER_PART_IF_MULTIPART = 67;
    int MAX_GSM_MESSAGE_LENGTH_PER_PART_IF_MULTIPART = 153;

    // status code 200 - Error substrings - checkk it contains.
    String ERROR_UNKNOWN = "Unknown error";
    String ERROR_NO_ACCOUNT = "No account found";
    String ERROR_INSUFICIENT_BALANCE = "Insufficient balance";
    String ERROR_UNROUTABLE_MESSAGE = "Message is unroutable";
    String ERROR_INVALID_PRODUCT_TOKEN = "Invalid product token";

    String GSM_CHARACTERS_REGEX = "^[A-Za-z0-9 \\r\\n@£$¥èéùìòÇØøÅå\u0394_\u03A6\u0393\u039B\u03A9"
                                  + "\u03A0\u03A8\u03A3\u0398\u039EÆæßÉ!\"#$%&amp;'()*+,\\-./:;&lt;=&gt;?¡ÄÖÑÜ§¿äöñüà^{}\\\\\\[~\\]|\u20AC]*$";
}
