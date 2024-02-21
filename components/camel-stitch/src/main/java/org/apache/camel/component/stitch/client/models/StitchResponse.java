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
package org.apache.camel.component.stitch.client.models;

import java.util.LinkedHashMap;
import java.util.Map;

public class StitchResponse implements StitchModel {
    // property names
    public static final String CODE = "code";
    public static final String HEADERS = "headers";
    public static final String STATUS = "status";
    public static final String MESSAGE = "message";

    private final int httpStatusCode;
    private final Map<String, Object> headers;
    private final String status;
    private final String message;

    public StitchResponse(int httpStatusCode, Map<String, Object> headers, String status, String message) {
        this.httpStatusCode = httpStatusCode;
        this.headers = headers;
        this.status = status;
        this.message = message;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    /**
     * Returns true if the request succeeded.
     *
     * @return
     *         <ul>
     *         <li>true - if the request succeeded</li>
     *         <li>false - if the request failed</li>
     *         </ul>
     */
    public boolean isOk() {
        return httpStatusCode < 300;
    }

    public String toString() {

        return "HTTP Status Code: " + httpStatusCode + ", Response Status: " + status + ", Response Message: " + message;
    }

    @Override
    public Map<String, Object> toMap() {
        final Map<String, Object> resultAsMap = new LinkedHashMap<>();

        resultAsMap.put(CODE, httpStatusCode);
        resultAsMap.put(HEADERS, headers);
        resultAsMap.put(STATUS, status);
        resultAsMap.put(MESSAGE, message);

        return resultAsMap;
    }
}
