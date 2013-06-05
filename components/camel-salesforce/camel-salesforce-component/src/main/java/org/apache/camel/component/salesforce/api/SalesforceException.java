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
package org.apache.camel.component.salesforce.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.camel.CamelException;
import org.apache.camel.component.salesforce.api.dto.RestError;

public class SalesforceException extends CamelException {

    private List<RestError> errors;
    private int statusCode;

    public SalesforceException(List<RestError> errors, int statusCode) {
        this(toErrorMessage(errors, statusCode), statusCode);

        this.errors = errors;
    }

    public SalesforceException(String message, int statusCode) {
        super(message);

        this.statusCode = statusCode;
    }

    public SalesforceException(String message, Throwable cause) {
        super(message, cause);
    }

    public SalesforceException(Throwable cause) {
        super(cause);
    }

    public List<RestError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public void setErrors(List<RestError> errors) {
        if (this.errors != null) {
            this.errors.clear();
        } else {
            this.errors = new ArrayList<RestError>();
        }
        this.errors.addAll(errors);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    @Override
    public String toString() {
        if (errors != null) {
            return toErrorMessage(errors, statusCode);
        } else {
            // make sure we include the custom message
            final StringBuilder builder = new StringBuilder("{ ");
            builder.append(getMessage());
            builder.append(", statusCode: ");
            builder.append(statusCode);
            builder.append("}");

            return builder.toString();
        }
    }

    private static String toErrorMessage(List<RestError> errors, int statusCode) {
        StringBuilder builder = new StringBuilder("{ ");
        if (errors != null) {
            builder.append(" errors: [");
            for (RestError error : errors) {
                builder.append(error.toString());
            }
            builder.append("], ");
        }
        builder.append("statusCode: ");
        builder.append(statusCode);
        builder.append("}");

        return builder.toString();
    }

}
