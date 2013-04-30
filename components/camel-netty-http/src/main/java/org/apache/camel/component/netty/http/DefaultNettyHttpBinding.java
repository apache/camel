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
package org.apache.camel.component.netty.http;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;

/**
 * Default {@link NettyHttpBinding}.
 */
public class DefaultNettyHttpBinding implements NettyHttpBinding {

    @Override
    public Message toCamelMessage(HttpRequest request, Exchange exchange) {
        NettyHttpMessage answer = new NettyHttpMessage(request);
        answer.setHeader(Exchange.HTTP_METHOD, request.getMethod().getName());
        answer.setHeader(Exchange.HTTP_URI, request.getUri());

        for (String name : request.getHeaderNames()) {
            List<String> values = request.getHeaders(name);
            if (values.size() == 1) {
                // flatten the list and store as single value
                answer.setHeader(name, values.get(0));
            } else {
                // if multiple values store them as list
                answer.setHeader(name, values);
            }
        }

        // keep the body as is, and use type converters
        answer.setBody(request.getContent());
        return answer;
    }

    @Override
    public HttpResponse fromCamelMessage(Message msg) {

        // the status code is default 200, but a header can override that
        Integer code = msg.getHeader(Exchange.HTTP_RESPONSE_CODE, 200, Integer.class);
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(code));

        byte[] data = msg.getBody(byte[].class);
        if (data != null) {
            response.setContent(ChannelBuffers.copiedBuffer(data));
            response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, response.getContent().readableBytes());
        }

        // map Camel headers to http (use header filter)
        String contentType = msg.getHeader(Exchange.CONTENT_TYPE, "text/plain; charset=UTF-8", String.class);
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, contentType);
        response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);

        return response;
    }
}
