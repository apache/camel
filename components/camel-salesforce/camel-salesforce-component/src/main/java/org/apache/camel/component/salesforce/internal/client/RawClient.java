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
package org.apache.camel.component.salesforce.internal.client;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.internal.PayloadFormat;

public interface RawClient {

    /**
     * Make a raw HTTP request to salesforce
     *
     * @param method   HTTP method. "GET", "POST", etc.
     * @param path     the path of the URL. Must begin with a "/"
     * @param body     Optional HTTP body
     * @param headers  HTTP headers
     * @param callback callback instance that will be invoked when the HTTP call returns
     */
    void makeRequest(
            String method, String path, PayloadFormat format, InputStream body, Map<String, List<String>> headers,
            ResponseCallback callback);

    interface ResponseCallback {
        void onResponse(InputStream response, Map<String, String> headers, SalesforceException exception);
    }
}
