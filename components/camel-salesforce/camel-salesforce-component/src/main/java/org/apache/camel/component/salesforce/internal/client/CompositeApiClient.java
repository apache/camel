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
package org.apache.camel.component.salesforce.internal.client;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatch;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatchResponse;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectComposite;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectCompositeResponse;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectTree;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectTreeResponse;

public interface CompositeApiClient {

    @FunctionalInterface
    interface Operation<T, R> {

        void submit(T body, Map<String, List<String>> headers, ResponseCallback<R> callback) throws SalesforceException;

    }

    @FunctionalInterface
    public interface ResponseCallback<T> {
        void onResponse(Optional<T> body, Map<String, String> headers, SalesforceException exception);
    }

    void submitComposite(SObjectComposite composite, Map<String, List<String>> headers,
        ResponseCallback<SObjectCompositeResponse> callback) throws SalesforceException;

    void submitCompositeBatch(SObjectBatch batch, Map<String, List<String>> headers,
        ResponseCallback<SObjectBatchResponse> callback) throws SalesforceException;

    /**
     * Submits given nodes (records) of SObjects and their children as a tree in
     * a single request. And updates the <code>Id</code> parameter of each
     * object to the value returned from the API call.
     *
     * @param tree SObject tree to submit
     * @param callback {@link ResponseCallback} to handle response or exception
     */
    void submitCompositeTree(SObjectTree tree, Map<String, List<String>> headers,
        ResponseCallback<SObjectTreeResponse> callback) throws SalesforceException;

}
