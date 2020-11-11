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
package org.apache.camel.zipkin;

import brave.Request;
import brave.Span;
import brave.Span.Kind;
import org.apache.camel.Message;

public class CamelRequest extends Request {

    private final Message message;
    private final Span.Kind spanKind;

    public CamelRequest(Message message, Span.Kind spanKind) {
        this.message = message;
        this.spanKind = spanKind;
    }

    @Override
    public Kind spanKind() {
        return this.spanKind;
    }

    @Override
    public Object unwrap() {
        return this.message;
    }

    public void setHeader(String key, String value) {
        message.setHeader(key, value);
    }

    public String getHeader(String key) {
        return message.getHeader(key, String.class);
    }

}
