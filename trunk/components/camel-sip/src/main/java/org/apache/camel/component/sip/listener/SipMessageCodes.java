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
package org.apache.camel.component.sip.listener;

import java.util.HashMap;
import java.util.Map;

public interface SipMessageCodes {
    Map<Integer, String> SIP_MESSAGE_CODES = 
        new HashMap<Integer, String>() 
        {
            {
                put(new Integer(100), "Trying");
                put(new Integer(180), "Ringing");
                put(new Integer(181), "Call Being Forwarded");
                put(new Integer(182), "Call Queued");
                put(new Integer(183), "Session Progress");
                put(new Integer(200), "OK");
                put(new Integer(202), "Accepted");
                put(new Integer(300), "Multiple Choices");
                put(new Integer(301), "Moved Permanently");
                put(new Integer(302), "Moved Temporarily");
                put(new Integer(305), "Use Proxy");
                put(new Integer(380), "Alternative Service");
                put(new Integer(400), "Bad Request");
                put(new Integer(401), "Unauthorized");
                put(new Integer(402), "Payment Required");
                put(new Integer(403), "Forbidden");
                put(new Integer(404), "Not Found");
                put(new Integer(405), "Method Not Allowed");
                put(new Integer(406), "Not Acceptable");
                put(new Integer(407), "Proxy Authentication Required");
                put(new Integer(408), "Request Timeout");
                put(new Integer(409), "Conflict");
                put(new Integer(410), "Gone");
                put(new Integer(411), "Length Required");
                put(new Integer(413), "Request Entity Too Large");
                put(new Integer(414), "Request URI Too Long");
                put(new Integer(415), "Unsupported Media Type");
                put(new Integer(416), "Unsupported URI Scheme");
                put(new Integer(420), "Bad Extension");
                put(new Integer(421), "Extension Required");
                put(new Integer(423), "Interval Too Brief");
                put(new Integer(480), "Temporarily Unavailable");
                put(new Integer(481), "Call/Transaction Does Not Exist");
                put(new Integer(482), "Loop Detected");
                put(new Integer(483), "Too Many Hops");
                put(new Integer(484), "Address Incomplete");
                put(new Integer(485), "Ambiguous");
                put(new Integer(486), "Busy Here");
                put(new Integer(487), "Request Terminated");
                put(new Integer(488), "Not Acceptable Here");
                put(new Integer(491), "Request Pending");
                put(new Integer(493), "Undecipherable");
                put(new Integer(500), "Server Internal Error");
                put(new Integer(501), "Not Implemented");
                put(new Integer(502), "Bad Gateway");
                put(new Integer(503), "Service Unavailable");
                put(new Integer(504), "Server Time-Out");
                put(new Integer(505), "Version Not Supported");
                put(new Integer(513), "Message Too Large");
                put(new Integer(600), "Busy Everywhere");
                put(new Integer(603), "Declined");
                put(new Integer(604), "Does Not Exist Anywhere");
                put(new Integer(605), "Not Acceptable");
            }
        };
}
