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
package org.apache.camel.component.rest.postman;

import java.util.Map;

/**
 * The outcome of one request when a folder or a whole collection is run.
 * <p>
 * A list of these becomes the message body of the exchange, so a route can split over them, filter the failures, or
 * report on them.
 *
 * @param requestId  the id of the request within the collection
 * @param name       the request name as written in the collection
 * @param folderPath the enclosing folders, separated by a slash, or {@code null} at the top level
 * @param method     the HTTP method used
 * @param uri        the URI called
 * @param httpStatus the HTTP status code, or {@code null} when the call did not complete
 * @param body       the response body, or {@code null}
 * @param headers    the response headers, never {@code null}
 * @param failure    the failure message when the call did not succeed, otherwise {@code null}
 */
public record PostmanRunResult(
        String requestId,
        String name,
        String folderPath,
        String method,
        String uri,
        Integer httpStatus,
        Object body,
        Map<String, Object> headers,
        String failure) {

    /**
     * Whether this request completed without an exception.
     */
    public boolean isSuccess() {
        return failure == null;
    }

    @Override
    public String toString() {
        return "PostmanRunResult[" + requestId + " " + method + " " + uri
               + (failure != null ? " FAILED: " + failure : " -> " + httpStatus) + "]";
    }
}
