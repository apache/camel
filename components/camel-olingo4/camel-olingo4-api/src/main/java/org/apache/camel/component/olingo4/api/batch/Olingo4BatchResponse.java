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
package org.apache.camel.component.olingo4.api.batch;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Batch Response part.
 */
public class Olingo4BatchResponse {

    private final int statusCode;
    private final String statusInfo;

    private final String contentId;

    private final Map<String, String> headers;
    private final Object body;

    public Olingo4BatchResponse(int statusCode, String statusInfo, String contentId, Map<String, String> headers, Object body) {
        this.statusCode = statusCode;
        this.statusInfo = statusInfo;
        this.contentId = contentId;
        this.headers = Collections.unmodifiableMap(new HashMap<>(headers));
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatusInfo() {
        return statusInfo;
    }

    public String getContentId() {
        return contentId;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Object getBody() {
        return body;
    }
}
