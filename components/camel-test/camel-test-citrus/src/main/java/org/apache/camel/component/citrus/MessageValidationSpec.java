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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.citrusframework.message.MessagePayloadBuilder;
import org.citrusframework.validation.context.ValidationContext;

public class MessageValidationSpec {

    private String body;
    private MessagePayloadBuilder bodyBuilder;
    private final Map<String, Object> headers = new LinkedHashMap<>();
    private final List<ValidationContext.Builder<?, ?>> validationContextBuilders = new ArrayList<>();

    public MessageValidationSpec body(String body) {
        this.body = body;
        this.bodyBuilder = null;
        return this;
    }

    public MessageValidationSpec body(MessagePayloadBuilder bodyBuilder) {
        this.bodyBuilder = bodyBuilder;
        this.body = null;
        return this;
    }

    public MessageValidationSpec header(String headerName, Object value) {
        this.headers.put(headerName, value);
        return this;
    }

    public MessageValidationSpec validate(ValidationContext.Builder<?, ?>... validationContexts) {
        this.validationContextBuilders.addAll(List.of(validationContexts));
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

    public List<ValidationContext.Builder<?, ?>> getValidationContextBuilders() {
        return validationContextBuilders;
    }
}
