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
package org.apache.camel.component.http;

import java.net.URI;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpTrace;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;

public enum HttpMethods implements Expression {

    GET(false),
    PATCH(true),
    POST(true),
    PUT(true),
    DELETE(false),
    HEAD(false),
    OPTIONS(false),
    TRACE(false);

    final boolean entity;

    HttpMethods(boolean entity) {
        this.entity = entity;
    }

    public HttpUriRequest createMethod(final String url) {
        return switch (this) {
            case GET -> new HttpGet(url);
            case PATCH -> new HttpPatch(url);
            case POST -> new HttpPost(url);
            case PUT -> new HttpPut(url);
            case DELETE -> new HttpDelete(url);
            case HEAD -> new HttpHead(url);
            case OPTIONS -> new HttpOptions(url);
            case TRACE -> new HttpTrace(url);
        };
    }

    public HttpUriRequest createMethod(final URI uri) {
        return switch (this) {
            case GET -> new HttpGet(uri);
            case PATCH -> new HttpPatch(uri);
            case POST -> new HttpPost(uri);
            case PUT -> new HttpPut(uri);
            case DELETE -> new HttpDelete(uri);
            case HEAD -> new HttpHead(uri);
            case OPTIONS -> new HttpOptions(uri);
            case TRACE -> new HttpTrace(uri);
        };
    }

    public final boolean isEntityEnclosing() {
        return entity;
    }

    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        return ExpressionBuilder.constantExpression(name()).evaluate(exchange, type);
    }

}
