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
package org.apache.camel.component.salesforce.internal.client;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.DeleteSObjectResult;
import org.apache.camel.component.salesforce.api.dto.SaveSObjectResult;
import org.apache.camel.component.salesforce.api.dto.UpsertSObjectResult;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectCollection;
import org.apache.camel.component.salesforce.internal.dto.composite.RetrieveSObjectCollectionsDto;

public interface CompositeSObjectCollectionsApiClient {

    @FunctionalInterface
    interface ResponseCallback<T> {
        void onResponse(Optional<T> body, Map<String, String> headers, SalesforceException exception);
    }

    <T> void submitRetrieveCompositeCollections(
            RetrieveSObjectCollectionsDto retrieveDto, Map<String, List<String>> headers,
            ResponseCallback<List<T>> callback, String sObjectName,
            Class<T> returnType)
            throws SalesforceException;

    void createCompositeCollections(
            SObjectCollection collection, Map<String, List<String>> headers,
            ResponseCallback<List<SaveSObjectResult>> callback)
            throws SalesforceException;

    void updateCompositeCollections(
            SObjectCollection collection, Map<String, List<String>> headers,
            ResponseCallback<List<SaveSObjectResult>> callback)
            throws SalesforceException;

    void upsertCompositeCollections(
            SObjectCollection collection, Map<String, List<String>> headers,
            ResponseCallback<List<UpsertSObjectResult>> callback, String sObjectName, String externalIdFieldName)
            throws SalesforceException;

    void submitDeleteCompositeCollections(
            List<String> ids, Boolean allOrNone, Map<String, List<String>> headers,
            ResponseCallback<List<DeleteSObjectResult>> callback);
}
