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
package org.apache.camel.component.salesforce.api.dto.composite;

import java.io.Serializable;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;

/**
 * Contains the individual result of Composite API request.
 */
@XStreamAlias("batchResult")
public final class SObjectCompositeResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @XStreamConverter(MapOfMapsConverter.class)
    private final Object body;

    private final Map<String, String> httpHeaders;

    private final int httpStatusCode;

    private final String referenceId;

    @JsonCreator
    public SObjectCompositeResult(@JsonProperty("body") final Object body,
            @JsonProperty("httpHeaders") final Map<String, String> httpHeaders,
            @JsonProperty("httpStatusCode") final int httpStatusCode,
            @JsonProperty("referenceID") final String referenceId) {
        this.body = body;
        this.httpHeaders = httpHeaders;
        this.httpStatusCode = httpStatusCode;
        this.referenceId = referenceId;
    }

    public Object getBody() {
        return body;
    }

    public Map<String, String> getHttpHeaders() {
        return httpHeaders;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public String getReferenceId() {
        return referenceId;
    }

    @Override
    public String toString() {
        return "SObjectCompositeResult [body=" + body + ", headers=" + httpHeaders + ", httpStatusCode=" + httpStatusCode + ", referenceId=" + referenceId + "]";
    }
}
