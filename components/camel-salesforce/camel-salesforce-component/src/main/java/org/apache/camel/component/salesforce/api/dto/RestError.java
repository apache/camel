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
package org.apache.camel.component.salesforce.api.dto;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
//CHECKSTYLE:OFF
public class RestError extends AbstractDTOBase {

    @XStreamAlias("statusCode")
    private String errorCode;
    private String message;
    @XStreamImplicit
    private List<String> fields;

    // default ctor for unmarshalling
    public RestError() {
    }

    public RestError(String errorCode, String message, List<String> fields) {
        this(errorCode, message);
        this.fields = fields;
    }

    public RestError(String errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }

    @JsonSetter("statusCode")
    void setStatusCode(final String statusCode) {
        errorCode = statusCode;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof RestError)) {
            return false;
        }

        final RestError other = (RestError) obj;

        return Objects.equals(errorCode, other.errorCode) && Objects.equals(message, other.message) && Objects.equals(fields, other.fields);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((errorCode == null) ? 0 : errorCode.hashCode());
        result = prime * result + ((fields == null) ? 0 : fields.hashCode());
        result = prime * result + ((message == null) ? 0 : message.hashCode());
        return result;
    }

}
//CHECKSTYLE:ON
