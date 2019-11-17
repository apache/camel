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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import org.apache.camel.component.salesforce.api.dto.XStreamFieldOrder;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectComposite.Method;

@XStreamAlias("compositeRequest")
@XStreamFieldOrder({"method", "url", "referenceId", "body"})
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"method", "url", "referenceId", "body"})
final class CompositeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @XStreamConverter(RichInputConverter.class)
    private final Object body;

    private final Method method;

    private final String referenceId;

    private final String url;

    CompositeRequest(final Method method, final String url, final Object body, final String referenceId) {
        this.method = method;
        this.url = url;
        this.body = body;
        this.referenceId = referenceId;
    }

    CompositeRequest(final Method method, final String url, final String referenceId) {
        this.method = method;
        this.url = url;
        this.referenceId = referenceId;
        body = null;
    }

    public Object getBody() {
        return body;
    }

    public Method getMethod() {
        return method;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return "Batch: " + method + " " + url + ", " + referenceId + ", data:" + body;
    }

}
