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
package org.apache.camel.component.a2a.exception;

import org.apache.camel.RuntimeCamelException;

/**
 * Exception thrown by the A2A component.
 */
public class A2AException extends RuntimeCamelException {

    private final A2AErrorCode errorCode;

    public A2AException(A2AErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public A2AException(A2AErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public A2AErrorCode getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return errorCode.getHttpStatus();
    }

    /**
     * A2A error codes mapped to HTTP status codes.
     */
    public enum A2AErrorCode {
        TASK_NOT_FOUND(404),
        TASK_NOT_CANCELABLE(409),
        PUSH_NOTIFICATION_NOT_SUPPORTED(501),
        UNSUPPORTED_OPERATION(405),
        CONTENT_TYPE_NOT_SUPPORTED(415),
        INVALID_AGENT_RESPONSE(502),
        EXTENSION_SUPPORT_REQUIRED(400),
        VERSION_NOT_SUPPORTED(400),
        SERVER_BUSY(429),
        INTERNAL_ERROR(500);

        private final int httpStatus;

        A2AErrorCode(int httpStatus) {
            this.httpStatus = httpStatus;
        }

        public int getHttpStatus() {
            return httpStatus;
        }
    }
}
