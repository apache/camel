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
package org.apache.camel.component.http4.handler;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.protocol.HttpContext;

/**
 *
 * @version 
 */
public class HeaderValidationHandler extends BasicValidationHandler {

    protected Map<String, String> expectedHeaders;

    public HeaderValidationHandler(String expectedMethod, String expectedQuery,
                                   Object expectedContent, String responseContent,
                                   Map<String, String> expectedHeaders) {
        super(expectedMethod, expectedQuery, expectedContent, responseContent);
        this.expectedHeaders = expectedHeaders;
    }

    public void handle(final HttpRequest request, final HttpResponse response,
                       final HttpContext context) throws HttpException, IOException {

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
                    response.setStatusCode(HttpStatus.SC_EXPECTATION_FAILED);
                    return;
                }
            }
        }

        super.handle(request, response, context);
    }

}