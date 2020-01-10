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

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpTrace;

public enum HttpMethods implements Expression {

    GET(HttpGet.class) {
        public HttpRequestBase createMethod(String url) {
            return new HttpGet(url);
        }
    },
    PATCH(HttpPatch.class) {
        public HttpRequestBase createMethod(String url) {
            return new HttpPatch(url);
        }
    },
    POST(HttpPost.class) {
        public HttpRequestBase createMethod(String url) {
            return new HttpPost(url);
        }
    },
    PUT(HttpPut.class) {
        public HttpRequestBase createMethod(String url) {
            return new HttpPut(url);
        }
    },
    DELETE(HttpDelete.class) {
        public HttpRequestBase createMethod(String url) {
            return new HttpDelete(url);
        }
    },
    HEAD(HttpHead.class) {
        public HttpRequestBase createMethod(String url) {
            return new HttpHead(url);
        }
    },
    OPTIONS(HttpOptions.class) {
        public HttpRequestBase createMethod(String url) {
            return new HttpOptions(url);
        }
    },
    TRACE(HttpTrace.class) {
        public HttpRequestBase createMethod(String url) {
            return new HttpTrace(url);
        }
    };

    final boolean entity;

    HttpMethods(Class<? extends HttpRequestBase> clazz) {
        entity = HttpEntityEnclosingRequestBase.class.isAssignableFrom(clazz);
    }

    public abstract HttpRequestBase createMethod(String url);

    public final boolean isEntityEnclosing() {
        return entity;
    }

    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        return ExpressionBuilder.constantExpression(name()).evaluate(exchange, type);
    }

}
