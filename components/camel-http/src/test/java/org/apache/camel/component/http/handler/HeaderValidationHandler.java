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
package org.apache.camel.component.http.handler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.protocol.HttpContext;

public class HeaderValidationHandler extends BasicValidationHandler {

    // Map of headers and values that are expected to be present
    // in HttpRequest.
    protected Map<String, String> expectedHeaders;
    // List of headers that are expected to be absent from HttpRequest
    // (e.g. for testing filtering).
    protected List<String> absentHeaders;

    public HeaderValidationHandler(String expectedMethod, String expectedQuery,
                                   Object expectedContent, String responseContent,
                                   Map<String, String> expectedHeaders) {
        super(expectedMethod, expectedQuery, expectedContent, responseContent);
        this.expectedHeaders = expectedHeaders;
    }

    public HeaderValidationHandler(String expectedMethod, String expectedQuery,
                                   Object expectedContent, String responseContent,
                                   Map<String, String> expectedHeaders,
                                   List<String> absentHeaders) {
        this(expectedMethod, expectedQuery, expectedContent, responseContent, expectedHeaders);
        this.absentHeaders = absentHeaders;
    }

    @Override
    public void handle(
            final ClassicHttpRequest request, final ClassicHttpResponse response,
            final HttpContext context)
            throws HttpException, IOException {

        if (expectedHeaders != null) {
            for (Entry<String, String> entry : expectedHeaders.entrySet()) {
                boolean headerExist = false;
                Header[] headers = request.getHeaders(entry.getKey());

                for (Header header : headers) {
                    if (header.getValue().equalsIgnoreCase(entry.getValue())) {
                        headerExist = true;
                        break;
                    }
                }

                if (!headerExist) {
                    response.setCode(HttpStatus.SC_EXPECTATION_FAILED);
                    return;
                }
            }
        }

        if (absentHeaders != null) {
            for (String header : absentHeaders) {
                if (request.getHeaders(header).length > 0) {
                    response.setCode(HttpStatus.SC_EXPECTATION_FAILED);
                    return;
                }
            }
        }

        super.handle(request, response, context);
    }

}
