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

package org.apache.camel.support.http;

import org.apache.camel.Exchange;
import org.apache.camel.Message;

public final class HttpUtil {
    private static final int INTERNAL_SERVER_ERROR = 500;
    private static final int OK = 200;
    private static final int NO_CONTENT = 204;

    private HttpUtil() {
    }

    /**
     * Given an exchange handling HTTP, determines the status response code to return for the caller
     *
     * @param  camelExchange the exchange to evaluate
     * @param  body          an optional payload (i.e.: the message body) carrying a response code
     * @return               An integer value with the response code
     */
    public static int determineResponseCode(Exchange camelExchange, Object body) {
        boolean failed = camelExchange.isFailed();
        int defaultCode = failed ? INTERNAL_SERVER_ERROR : OK;

        Message message = camelExchange.getMessage();
        Integer currentCode = message.getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        int codeToUse = currentCode == null ? defaultCode : currentCode;

        if (codeToUse != INTERNAL_SERVER_ERROR) {
            if (body == null || body instanceof String && ((String) body).isBlank()) {
                // no content
                codeToUse = currentCode == null ? NO_CONTENT : currentCode;
            }
        }

        return codeToUse;
    }

}
