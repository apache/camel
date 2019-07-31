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
package org.apache.camel.component.sip.listener;

import java.util.HashMap;
import java.util.Map;

public interface SipMessageCodes {
    Map<Integer, String> SIP_MESSAGE_CODES = new HashMap<Integer, String>() {
            private static final long serialVersionUID = 1L;
            {
                put(100, "Trying");
                put(180, "Ringing");
                put(181, "Call Being Forwarded");
                put(182, "Call Queued");
                put(183, "Session Progress");
                put(200, "OK");
                put(202, "Accepted");
                put(300, "Multiple Choices");
                put(301, "Moved Permanently");
                put(302, "Moved Temporarily");
                put(305, "Use Proxy");
                put(380, "Alternative Service");
                put(400, "Bad Request");
                put(401, "Unauthorized");
                put(402, "Payment Required");
                put(403, "Forbidden");
                put(404, "Not Found");
                put(405, "Method Not Allowed");
                put(406, "Not Acceptable");
                put(407, "Proxy Authentication Required");
                put(408, "Request Timeout");
                put(409, "Conflict");
                put(410, "Gone");
                put(411, "Length Required");
                put(413, "Request Entity Too Large");
                put(414, "Request URI Too Long");
                put(415, "Unsupported Media Type");
                put(416, "Unsupported URI Scheme");
                put(420, "Bad Extension");
                put(421, "Extension Required");
                put(423, "Interval Too Brief");
                put(480, "Temporarily Unavailable");
                put(481, "Call/Transaction Does Not Exist");
                put(482, "Loop Detected");
                put(483, "Too Many Hops");
                put(484, "Address Incomplete");
                put(485, "Ambiguous");
                put(486, "Busy Here");
                put(487, "Request Terminated");
                put(488, "Not Acceptable Here");
                put(491, "Request Pending");
                put(493, "Undecipherable");
                put(500, "Server Internal Error");
                put(501, "Not Implemented");
                put(502, "Bad Gateway");
                put(503, "Service Unavailable");
                put(504, "Server Time-Out");
                put(505, "Version Not Supported");
                put(513, "Message Too Large");
                put(600, "Busy Everywhere");
                put(603, "Declined");
                put(604, "Does Not Exist Anywhere");
                put(605, "Not Acceptable");
            }
        };
}
