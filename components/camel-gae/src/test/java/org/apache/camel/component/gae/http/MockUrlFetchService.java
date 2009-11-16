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
package org.apache.camel.component.gae.http;

import java.io.IOException;
import java.net.URL;

import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.MockHttpResponse;
import com.google.appengine.api.urlfetch.URLFetchService;

public class MockUrlFetchService implements URLFetchService {

    public HTTPResponse fetch(URL url) throws IOException {
        throw new UnsupportedOperationException("not impemented");
    }

    public HTTPResponse fetch(HTTPRequest request) throws IOException {
        MockHttpResponse response = new MockHttpResponse(200);
        response.setContent(request.getPayload());
        response.addHeader("testUrl", request.getURL().toString());
        response.addHeader("testQuery", request.getURL().getQuery());
        response.addHeader("testMethod", request.getMethod().toString());
        for (HTTPHeader header : request.getHeaders()) {
            response.addHeader(header.getName(), header.getValue());
        }
        return response;
    }

}
