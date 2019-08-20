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
 * The response of the batch request it contains individual results of each
 * request submitted in a batch at the same index. The flag {@link #hasErrors()}
 * indicates if any of the requests in the batch has failed with status 400 or
 * 500.
 */
@XStreamAlias("batchResults")
public final class SObjectBatchResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean hasErrors;

    private final List<SObjectBatchResult> results;

    @JsonCreator
    public SObjectBatchResponse(@JsonProperty("hasErrors")
    final boolean hasErrors, @JsonProperty("results")
    final List<SObjectBatchResult> results) {
        this.hasErrors = hasErrors;
        this.results = results;
    }

    public List<SObjectBatchResult> getResults() {
        return results;
    }

    public boolean hasErrors() {
        return hasErrors;
    }

    @Override
    public String toString() {
        return "hasErrors: " + hasErrors + ", results: " + results;
    }
}
