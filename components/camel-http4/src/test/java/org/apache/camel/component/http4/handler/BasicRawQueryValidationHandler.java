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
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.HttpRequest;

/**
 * Similar to {@link BasicValidationHandler} but validates the raw query instead.
 */
public class BasicRawQueryValidationHandler extends BasicValidationHandler {

    public BasicRawQueryValidationHandler(String expectedMethod, String expectedQuery, Object expectedContent, String responseContent) {
        super(expectedMethod, expectedQuery, expectedContent, responseContent);
    }

    protected boolean validateQuery(HttpRequest request) throws IOException {
        try {
            String query = new URI(request.getRequestLine().getUri()).getRawQuery();
            if (expectedQuery != null && !expectedQuery.equals(query)) {
                return false;
            }
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        return true;
    }
}
