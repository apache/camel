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
package org.apache.camel.component.salesforce.api;

import java.util.Collections;
import java.util.List;

import org.apache.camel.CamelException;
import org.apache.camel.component.salesforce.api.dto.RestError;

public class SalesforceException extends CamelException {

    private static final long serialVersionUID = 1L;

    private final List<RestError> errors;
    private final int statusCode;

    public SalesforceException(Throwable cause) {
        this(null, 0, null, cause);
    }

    public SalesforceException(String message, Throwable cause) {
        this(null, 0, message, cause);
    }

    public SalesforceException(String message, int statusCode) {
        this(null, statusCode, message, null);
    }

    public SalesforceException(String message, int statusCode, Throwable cause) {
        this(null, statusCode, message, cause);
    }

    public SalesforceException(List<RestError> errors, int statusCode) {
        this(errors, statusCode, null, null);
    }

    public SalesforceException(List<RestError> errors, int statusCode, Throwable cause) {
        this(errors, statusCode, null, cause);
    }

    public SalesforceException(List<RestError> errors, int statusCode, String message) {
        this(errors, statusCode, message, null);
    }

    public SalesforceException(List<RestError> errors, int statusCode, String message, Throwable cause) {
        super(message == null ? toErrorMessage(errors, statusCode) : message, cause);
        this.errors = errors;
        this.statusCode = statusCode;
    }

    public List<RestError> getErrors() {
        return errors == null ? Collections.emptyList() : Collections.unmodifiableList(errors);
    }

    public int getStatusCode() {
        return statusCode;
    }

    private static String toErrorMessage(List<RestError> errors, int statusCode) {
        StringBuilder builder = new StringBuilder("{");
        if (errors != null) {
            builder.append("errors:[");
            for (RestError error : errors) {
                builder.append(error.toString());
            }
            builder.append("],");
        }
        builder.append("statusCode:");
        builder.append(statusCode);
        builder.append("}");

        return builder.toString();
    }

}
