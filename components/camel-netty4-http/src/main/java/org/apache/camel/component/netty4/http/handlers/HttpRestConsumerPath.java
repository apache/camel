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
package org.apache.camel.component.netty4.http.handlers;

import org.apache.camel.support.RestConsumerContextPathMatcher;

public class HttpRestConsumerPath implements RestConsumerContextPathMatcher.ConsumerPath<HttpServerChannelHandler> {

    private final HttpServerChannelHandler handler;

    public HttpRestConsumerPath(HttpServerChannelHandler handler) {
        this.handler = handler;
    }

    @Override
    public String getRestrictMethod() {
        return handler.getConsumer().getEndpoint().getHttpMethodRestrict();
    }

    @Override
    public String getConsumerPath() {
        return handler.getConsumer().getConfiguration().getPath();
    }

    @Override
    public HttpServerChannelHandler getConsumer() {
        return handler;
    }

    @Override
    public boolean isMatchOnUriPrefix() {
        return handler.getConsumer().getEndpoint().getConfiguration().isMatchOnUriPrefix();
    }

    @Override
    public String toString() {
        return getConsumerPath();
    }
}
