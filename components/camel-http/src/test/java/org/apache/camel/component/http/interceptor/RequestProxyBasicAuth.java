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
package org.apache.camel.component.http.interceptor;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.protocol.HttpContext;

public class RequestProxyBasicAuth implements HttpRequestInterceptor {
    @Override
    public void process(final HttpRequest request, final EntityDetails entity, final HttpContext context)
            throws HttpException, IOException {
        String auth = null;

        try {
            String requestLine = request.getUri().toString();
            // assert we set a write GET URI
            if (requestLine.contains("http://localhost")) {
                throw new HttpException("Get a wrong proxy GET url");
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        Header h = request.getFirstHeader(HttpHeaders.PROXY_AUTHORIZATION);
        if (h != null) {
            String s = h.getValue();
            if (s != null) {
                auth = s.trim();
            }
        }

        if (auth != null) {
            int i = auth.indexOf(' ');
            if (i == -1) {
                throw new ProtocolException("Invalid Authorization header: " + auth);
            }
            String authscheme = auth.substring(0, i);
            if (authscheme.equalsIgnoreCase("basic")) {
                String s = auth.substring(i + 1).trim();
                byte[] credsRaw = s.getBytes(StandardCharsets.US_ASCII);
                Base64.Decoder codec = Base64.getDecoder();
                String creds = new String(codec.decode(credsRaw), StandardCharsets.US_ASCII);
                context.setAttribute("proxy-creds", creds);
            }
        }
    }
}
