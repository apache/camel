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
package org.apache.camel.component.typesafeai;

import org.apache.camel.CamelException;

/** An HTTP failure without the service's error body, which may echo submitted data. */
public class TypeSafeAiHttpException extends CamelException {
    private final int statusCode;
    private final String requestId;
    private final String retryAfter;

    public TypeSafeAiHttpException(int statusCode, String requestId, String retryAfter) {
        super("TypeSafe AI evaluation failed with HTTP status " + statusCode);
        this.statusCode = statusCode;
        this.requestId = requestId;
        this.retryAfter = retryAfter;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getRetryAfter() {
        return retryAfter;
    }
}
