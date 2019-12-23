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
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * The response of the composite request it contains individual results of each
 * request submitted in a request at the same index.
 */
@XStreamAlias("compositeResults")
public final class SObjectCompositeResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<SObjectCompositeResult> compositeResponse;

    @JsonCreator
    public SObjectCompositeResponse(@JsonProperty("results")
    final List<SObjectCompositeResult> compositeResponse) {
        this.compositeResponse = compositeResponse;
    }

    public List<SObjectCompositeResult> getCompositeResponse() {
        return compositeResponse;
    }

    @Override
    public String toString() {
        return "compositeResponse: " + compositeResponse;
    }
}
