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

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.salesforce.api.SalesforceException;

public interface RestClient {

    public interface ResponseCallback {
        void onResponse(InputStream response, Map<String, String> headers, SalesforceException exception);
    }

    /**
     * Lists summary information about each API version currently available, including the version, label, and a link to
     * each version's root.
     *
     * @param headers  additional HTTP headers to send
     * @param callback {@link ResponseCallback} to handle response or exception
     */
    void getVersions(Map<String, List<String>> headers, ResponseCallback callback);

    /**
     * Lists available resources for the specified API version, including resource name and URI.
     *
     * @param headers  additional HTTP headers to send
     * @param callback {@link ResponseCallback} to handle response or exception
     */
    void getResources(Map<String, List<String>> headers, ResponseCallback callback);

    /**
     * Lists the available objects and their metadata for your organization's data.
     *
     * @param headers  additional HTTP headers to send
     * @param callback {@link ResponseCallback} to handle response or exception
     */
    void getGlobalObjects(Map<String, List<String>> headers, ResponseCallback callback);

    /**
     * Describes the individual metadata for the specified object.
     *
     * @param sObjectName specified object name
     * @param headers     additional HTTP headers to send
     * @param callback    {@link ResponseCallback} to handle response or exception
     */
    void getBasicInfo(String sObjectName, Map<String, List<String>> headers, ResponseCallback callback);

    /**
     * Completely describes the individual metadata at all levels for the specified object.
     *
     * @param sObjectName specified object name
     * @param headers     additional HTTP headers to send
     * @param callback    {@link ResponseCallback} to handle response or exception
     */
    void getDescription(String sObjectName, Map<String, List<String>> headers, ResponseCallback callback);

    /**
     * Retrieves a record for the specified object ID.
     *
     * @param sObjectName specified object name
     * @param id          object id
     * @param headers     additional HTTP headers to send
     * @param callback    {@link ResponseCallback} to handle response or exception
     */
    void getSObject(
            String sObjectName, String id, String[] fields, Map<String, List<String>> headers, ResponseCallback callback);

    /**
     * Creates a record for the specified object.
     *
     * @param sObjectName specified object name
     * @param headers     additional HTTP headers to send
     * @param sObject     request entity
     * @param callback    {@link ResponseCallback} to handle response or exception
     */
    void createSObject(String sObjectName, InputStream sObject, Map<String, List<String>> headers, ResponseCallback callback);

    /**
     * Updates a record for the specified object ID.
     *
     * @param sObjectName specified object name
     * @param id          object id
     * @param headers     additional HTTP headers to send
     * @param sObject     request entity
     * @param callback    {@link ResponseCallback} to handle response or exception
     */
    void updateSObject(
            String sObjectName, String id, InputStream sObject, Map<String, List<String>> headers, ResponseCallback callback);

    /**
     * Deletes a record for the specified object ID.
     *
     * @param sObjectName specified object name
     * @param id          object id
     * @param headers     additional HTTP headers to send
     * @param callback    {@link ResponseCallback} to handle response or exception
     */
    void deleteSObject(String sObjectName, String id, Map<String, List<String>> headers, ResponseCallback callback);

    /**
     * Retrieves a record for the specified external ID.
     *
     * @param sObjectName specified object name
     * @param fieldName   external field name
     * @param fieldValue  external field value
     * @param headers     additional HTTP headers to send
     * @param callback    {@link ResponseCallback} to handle response or exception
     */
    void getSObjectWithId(
            String sObjectName, String fieldName, String fieldValue, Map<String, List<String>> headers,
            ResponseCallback callback);

    /**
     * Creates or updates a record based on the value of a specified external ID field.
     *
     * @param sObjectName specified object name
     * @param fieldName   external field name
     * @param fieldValue  external field value
     * @param headers     additional HTTP headers to send
     * @param sObject     input object to insert or update
     * @param callback    {@link ResponseCallback} to handle response or exception
     */
    void upsertSObject(
            String sObjectName, String fieldName, String fieldValue, Map<String, List<String>> headers, InputStream sObject,
            ResponseCallback callback);

    /**
     * Deletes a record based on the value of a specified external ID field.
     *
     * @param sObjectName specified object name
     * @param fieldName   external field name
     * @param fieldValue  external field value
     * @param headers     additional HTTP headers to send
     * @param callback    {@link ResponseCallback} to handle response or exception
     */
    void deleteSObjectWithId(
            String sObjectName, String fieldName, String fieldValue, Map<String, List<String>> headers,
            ResponseCallback callback);

    /**
     * Retrieves the specified blob field from an individual record.
     *
     * @param sObjectName   specified object name
     * @param id            identifier of the object
     * @param blobFieldName name of the field holding the blob
     * @param headers       additional HTTP headers to send
     * @param callback      {@link ResponseCallback} to handle response or exception
     */
    void getBlobField(
            String sObjectName, String id, String blobFieldName, Map<String, List<String>> headers, ResponseCallback callback);

    /**
     * Executes the specified SOQL query.
     *
     * @param soqlQuery SOQL query
     * @param headers   additional HTTP headers to send
     * @param callback  {@link ResponseCallback} to handle response or exception
     */
    void query(String soqlQuery, Map<String, List<String>> headers, ResponseCallback callback);

    /**
     * Get SOQL query results using nextRecordsUrl.
     *
     * @param nextRecordsUrl URL for next records to fetch, returned by query()
     * @param headers        additional HTTP headers to send
     * @param callback       {@link ResponseCallback} to handle response or exception
     */
    void queryMore(String nextRecordsUrl, Map<String, List<String>> headers, ResponseCallback callback);

    /**
     * Executes the specified SOQL query including deleted records.
     *
     * @param soqlQuery SOQL query
     * @param headers   additional HTTP headers to send
     * @param callback  {@link ResponseCallback} to handle response or exception
     */
    void queryAll(String soqlQuery, Map<String, List<String>> headers, ResponseCallback callback);

    /**
     * Executes the specified SOSL search.
     *
     * @param soslQuery SOSL query
     * @param headers   additional HTTP headers to send
     * @param callback  {@link ResponseCallback} to handle response or exception
     */
    void search(String soslQuery, Map<String, List<String>> headers, ResponseCallback callback);

    /**
     * Executes a user defined APEX REST API call.
     *
     * @param httpMethod  HTTP method to execute.
     * @param apexUrl     APEX api url.
     * @param queryParams optional query parameters for GET methods, may be empty.
     * @param requestDto  optional input DTO for POST, etc. may be null.
     * @param headers     additional HTTP headers to send
     * @param callback    {@link ResponseCallback} to handle response or exception
     */
    void apexCall(
            String httpMethod, String apexUrl, Map<String, Object> queryParams, InputStream requestDto,
            Map<String, List<String>> headers, ResponseCallback callback);

    /**
     * Fetches recently viewed records.
     *
     * @param limit            optional limit that specifies the maximum number of records to be returned. If this
     *                         parameter is not specified, the default maximum number of records returned is the maximum
     *                         number of entries in RecentlyViewed, which is 200 records per object.
     * @param headers          additional HTTP headers to send
     * @param responseCallback {@link ResponseCallback} to handle response or exception
     */
    void recent(Integer limit, Map<String, List<String>> headers, ResponseCallback responseCallback);

    /**
     * Fetches Organization Limits.
     *
     * @param headers          additional HTTP headers to send
     * @param responseCallback {@link ResponseCallback} to handle response or exception
     */
    void limits(Map<String, List<String>> headers, ResponseCallback responseCallback);

    /**
     * Submits, approves or rejects particular record.
     *
     * @param headers  additional HTTP headers to send
     * @param callback {@link ResponseCallback} to handle response or exception
     */
    void approval(InputStream request, Map<String, List<String>> headers, ResponseCallback callback);

    /**
     * Returns a list of all approval processes.
     *
     * @param headers  additional HTTP headers to send
     * @param callback {@link ResponseCallback} to handle response or exception
     */
    void approvals(Map<String, List<String>> headers, ResponseCallback callback);

    /**
     *
     * @param eventName Name of event
     * @param headers   additional HTTP headers to send
     * @param callback  {@link ResponseCallback} to handle response or exception
     */
    void getEventSchemaByEventName(
            String eventName, String payloadFormat, Map<String, List<String>> headers, ResponseCallback callback);

    /**
     *
     * @param schemaId Id of Schema
     * @param headers  additional HTTP headers to send
     * @param callback {@link ResponseCallback} to handle response or exception
     */
    void getEventSchemaBySchemaId(
            String schemaId, String payloadFormat, Map<String, List<String>> headers, ResponseCallback callback);
}
