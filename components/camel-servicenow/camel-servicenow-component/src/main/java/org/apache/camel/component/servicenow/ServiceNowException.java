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
package org.apache.camel.component.servicenow;

import java.util.Collections;
import java.util.Map;

import org.apache.camel.CamelException;

public class ServiceNowException extends CamelException {
    private final Integer code;
    private final String status;
    private final String detail;
    private final Map<Object, Object> attributes;

    public ServiceNowException(Integer code, String status, String message, String detail) {
        super(message);
        this.code = code;
        this.status = status;
        this.detail = detail;
        this.attributes = Collections.emptyMap();
    }

    public ServiceNowException(Integer code, Map<Object, Object> attributes) {
        super(String.format("Status (%d)", code));
        this.code = code;
        this.status = null;
        this.detail = null;
        this.attributes = Collections.unmodifiableMap(attributes);
    }

    public Integer getCode() {
        return code;
    }

    public String getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public Map<Object, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String toString() {
        return getMessage() != null
            ? "" + this.status + ": " + getMessage()
            : super.toString();
    }

}
