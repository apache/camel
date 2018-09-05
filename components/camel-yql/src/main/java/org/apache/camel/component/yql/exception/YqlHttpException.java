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
package org.apache.camel.component.yql.exception;

/**
 * Signal a non 200 HTTP response
 */
public final class YqlHttpException extends YqlException {

    private final int httpStatus;
    private final String body;
    private final String originalRequest;

    private YqlHttpException(final int httpStatus, final String body, final String originalRequest) {
        super("HTTP request to YQL failed");
        this.httpStatus = httpStatus;
        this.body = body;
        this.originalRequest = originalRequest;
    }

    public static YqlHttpException failedWith(final int httpStatus, final String body, final String originalRequest) {
        return new YqlHttpException(httpStatus, body, originalRequest);
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String body() {
        return body;
    }

    public String getOriginalRequest() {
        return originalRequest;
    }
}
