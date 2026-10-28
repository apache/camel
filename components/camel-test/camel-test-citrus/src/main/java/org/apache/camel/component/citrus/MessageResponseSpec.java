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

package org.apache.camel.component.citrus;

import java.util.LinkedHashMap;
import java.util.Map;

import org.citrusframework.message.MessagePayloadBuilder;

public class MessageResponseSpec {

    private String body;
    private MessagePayloadBuilder bodyBuilder;
    private final Map<String, Object> headers = new LinkedHashMap<>();

    public MessageResponseSpec body(String body) {
        this.body = body;
        this.bodyBuilder = null;
        return this;
    }

    public MessageResponseSpec body(MessagePayloadBuilder bodyBuilder) {
        this.bodyBuilder = bodyBuilder;
        this.body = null;
        return this;
    }

    public MessageResponseSpec header(String headerName, Object value) {
        this.headers.put(headerName, value);
        return this;
    }

    public String getBody() {
        return body;
    }

    public MessagePayloadBuilder getBodyBuilder() {
        return bodyBuilder;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }
}
