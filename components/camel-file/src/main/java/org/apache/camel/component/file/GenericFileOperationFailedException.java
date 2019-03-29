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
package org.apache.camel.component.file;

import org.apache.camel.RuntimeCamelException;

/**
 * Exception thrown in case of last file operation failed.
 */
public class GenericFileOperationFailedException extends RuntimeCamelException {
    private static final long serialVersionUID = -64176625836814418L;

    private final int code;
    private final String reason;

    public GenericFileOperationFailedException(String message) {
        super(message);
        this.code = 0;
        this.reason = null;
    }

    public GenericFileOperationFailedException(String message, Throwable cause) {
        super(message, cause);
        this.code = 0;
        this.reason = null;
    }

    public GenericFileOperationFailedException(int code, String reason) {
        super("File operation failed" + (reason != null ? ": " + reason : "") + ". Code: " + code);
        this.code = code;
        this.reason = reason;
    }

    public GenericFileOperationFailedException(int code, String reason, Throwable cause) {
        super("File operation failed" + (reason != null ? ": " + reason : "") + ". Code: " + code, cause);
        this.code = code;
        this.reason = reason;
    }

    public GenericFileOperationFailedException(int code, String reason, String message) {
        this(code, reason + " " + message);
    }

    public GenericFileOperationFailedException(int code, String reason, String message, Throwable cause) {
        this(code, reason + " " + message, cause);
    }

    /**
     * Return the file failure code (if any)
     */
    public int getCode() {
        return code;
    }

    /**
     * Return the file failure reason (if any)
     */
    public String getReason() {
        return reason;
    }
}
