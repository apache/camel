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
package org.apache.camel.component.netty4.http;

import java.io.IOException;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.camel.spi.RestApiResponseAdapter;

public class NettyRestApiResponseAdapter implements RestApiResponseAdapter {

    private final FullHttpResponse httpResponse;

    public NettyRestApiResponseAdapter(FullHttpResponse httpResponse) {
        this.httpResponse = httpResponse;
    }

    @Override
    public void addHeader(String name, String value) {
        httpResponse.headers().set(name, value);
    }

    @Override
    public void writeBytes(byte[] bytes) throws IOException {
        httpResponse.content().writeBytes(bytes);
    }

    @Override
    public void noContent() {
        httpResponse.setStatus(HttpResponseStatus.NO_CONTENT);

    }
}
