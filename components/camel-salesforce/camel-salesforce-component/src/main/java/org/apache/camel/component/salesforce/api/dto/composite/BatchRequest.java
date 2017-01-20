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
package org.apache.camel.component.salesforce.api.dto.composite;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;

import org.apache.camel.component.salesforce.api.dto.XStreamFieldOrder;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatch.Method;

@XStreamAlias("batchRequest")
@XStreamFieldOrder({"method", "url", "richInput"})
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"method", "url", "richInput"})
final class BatchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Method method;

    @XStreamConverter(RichInputConverter.class)
    private final Object richInput;

    private final String url;

    BatchRequest(final Method method, final String url) {
        this(method, url, null);
    }

    BatchRequest(final Method method, final String url, final Object richInput) {
        this.method = method;
        this.url = url;
        this.richInput = richInput;
    }

    public Method getMethod() {
        return method;
    }

    public Object getRichInput() {
        return richInput;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return "Batch: " + method + " " + url + ", data:" + richInput;
    }

}
