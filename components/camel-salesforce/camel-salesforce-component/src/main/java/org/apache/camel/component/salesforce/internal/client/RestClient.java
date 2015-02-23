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

import java.io.InputStream;
import java.util.Map;

import org.apache.camel.component.salesforce.api.SalesforceException;

public interface RestClient {

    public interface ResponseCallback {
        void onResponse(InputStream response, SalesforceException exception);
    }

    /**
     * Lists summary information about each API version currently available,
     * including the version, label, and a link to each version's root.
     *
     * @param callback {@link ResponseCallback} to handle response or exception
     */
    void getVersions(ResponseCallback callback);

    /**
     * Lists available resources for the specified API version, including resource name and URI.
     *
     * @param callback {@link ResponseCallback} to handle response or exception
     */
    void getResources(ResponseCallback callback);

    /**
     * Lists the available objects and their metadata for your organization's data.
     *
     * @param callback {@link ResponseCallback} to handle response or exception
     */
    void getGlobalObjects(ResponseCallback callback);

    /**
     * Describes the individual metadata for the specified object.
     *
     * @param sObjectName specified object name
     * @param callback    {@link ResponseCallback} to handle response or exception
     */
    void getBasicInfo(String sObjectName, ResponseCallback callback);

    /**
     * Completely describes the individual metadata at all levels for the specified object.
     *
     * @param sObjectName specified object name
     * @param callback    {@link ResponseCallback} to handle response or exception
     */
    void getDescription(String sObjectName, ResponseCallback callback);

    /**
     * Retrieves a record for the specified object ID.
     *
     * @param sObjectName specified object name
     * @param id          object id
     * @param callback    {@link ResponseCallback} to handle response or exception
     */
    void getSObject(String sObjectName, String id, String[] fields, ResponseCallback callback);

    /**
     * Creates a record for the specified object.
     *
     * @param sObjectName specified object name
     * @param sObject     request entity
     * @param callback    {@link ResponseCallback} to handle response or exception
     */
    void createSObject(String sObjectName, InputStream sObject, ResponseCallback callback);

    /**
     * Updates a record for the specified object ID.
     *
     * @param sObjectName specified object name
     * @param id          object id
     * @param sObject     request entity
     * @param callback    {@link ResponseCallback} to handle response or exception
     */
    void updateSObject(String sObjectName, String id, InputStream sObject, ResponseCallback callback);

    /**
     * Deletes a record for the specified object ID.
     *
     * @param sObjectName specified object name
     * @param id          object id
     * @param callback    {@link ResponseCallback} to handle response or exception
     */
    void deleteSObject(String sObjectName, String id, ResponseCallback callback);

    /**
     * Retrieves a record for the specified external ID.
     *
     * @param sObjectName specified object name
     * @param fieldName   external field name
     * @param fieldValue  external field value
     * @param callback    {@link ResponseCallback} to handle response or exception
     */
    void getSObjectWithId(String sObjectName, String fieldName, String fieldValue, ResponseCallback callback);

    /**
     * Creates or updates a record based on the value of a specified external ID field.
     *
     * @param sObjectName specified object name
     * @param fieldName   external field name
     * @param fieldValue  external field value
     * @param sObject     input object to insert or update
     * @param callback    {@link ResponseCallback} to handle response or exception
     */
    void upsertSObject(String sObjectName,
                       String fieldName, String fieldValue, InputStream sObject, ResponseCallback callback);

    /**
     * Deletes a record based on the value of a specified external ID field.
     *
     * @param sObjectName specified object name
     * @param fieldName   external field name
     * @param fieldValue  external field value
     * @param callback    {@link ResponseCallback} to handle response or exception
     */
    void deleteSObjectWithId(String sObjectName,
                             String fieldName, String fieldValue, ResponseCallback callback);


    /**
     * Retrieves the specified blob field from an individual record.
     */
    void getBlobField(String sObjectName, String id, String blobFieldName, ResponseCallback callback);

/*
    TODO
    SObject User Password
    /vXX.X/sobjects/User/user id/password
    /vXX.X/sobjects/SelfServiceUser/self service user id/password

    These methods set, reset, or get information about a user password.
*/

    /**
     * Executes the specified SOQL query.
     *
     * @param soqlQuery SOQL query
     * @param callback  {@link ResponseCallback} to handle response or exception
     */
    void query(String soqlQuery, ResponseCallback callback);

    /**
     * Get SOQL query results using nextRecordsUrl.
     *
     * @param nextRecordsUrl URL for next records to fetch, returned by query()
     * @param callback       {@link ResponseCallback} to handle response or exception
     */
    void queryMore(String nextRecordsUrl, ResponseCallback callback);

    /**
     * Executes the specified SOSL search.
     *
     * @param soslQuery SOSL query
     * @param callback  {@link ResponseCallback} to handle response or exception
     */
    void search(String soslQuery, ResponseCallback callback);

    /**
     * Executes a user defined APEX REST API call.
     *
     * @param httpMethod    HTTP method to execute.
     * @param apexUrl       APEX api url.
     * @param queryParams   optional query parameters for GET methods, may be empty.
     * @param requestDto    optional input DTO for POST, etc. may be null.
     * @param callback      {@link ResponseCallback} to handle response or exception
     */
    void apexCall(String httpMethod, String apexUrl, Map<String, Object> queryParams, InputStream requestDto,
                  ResponseCallback callback);

}
