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

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultMessage;

/**
 * Netty HTTP based {@link org.apache.camel.Message}.
 * <p/>
 * This implementation allows direct access to the Netty {@link FullHttpRequest} using
 * the {@link #getHttpRequest()} method.
 */
public class NettyHttpMessage extends DefaultMessage {

    private final transient FullHttpRequest httpRequest;
    private final transient FullHttpResponse httpResponse;

    public NettyHttpMessage(CamelContext camelContext, FullHttpRequest httpRequest, FullHttpResponse httpResponse) {
        super(camelContext);
        this.httpRequest = httpRequest;
        this.httpResponse = httpResponse;
    }

    public FullHttpRequest getHttpRequest() {
        return httpRequest;
    }

    public FullHttpResponse getHttpResponse() {
        return httpResponse;
    }

    @Override
    public DefaultMessage newInstance() {
        return new NettyHttpMessage(getCamelContext(), httpRequest, httpResponse);
    }
}
