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
package org.apache.camel.component.salesforce.internal;

public enum OperationName {

    // rest API
    GET_VERSIONS("getVersions"),
    GET_RESOURCES("getResources"),
    GET_GLOBAL_OBJECTS("getGlobalObjects"),
    GET_BASIC_INFO("getBasicInfo"),
    GET_DESCRIPTION(
                    "getDescription"),
    GET_SOBJECT("getSObject"),
    CREATE_SOBJECT("createSObject"),
    UPDATE_SOBJECT("updateSObject"),
    DELETE_SOBJECT("deleteSObject"),
    GET_SOBJECT_WITH_ID(
                        "getSObjectWithId"),
    UPSERT_SOBJECT("upsertSObject"),
    DELETE_SOBJECT_WITH_ID("deleteSObjectWithId"),
    GET_BLOB_FIELD(
                   "getBlobField"),
    QUERY("query"),
    QUERY_MORE("queryMore"),
    QUERY_ALL("queryAll"),
    SEARCH("search"),
    APEX_CALL("apexCall"),
    RECENT("recent"),
    GET_EVENT_SCHEMA("getEventSchema"),

    // bulk API
    CREATE_JOB("createJob"),
    GET_JOB("getJob"),
    CLOSE_JOB("closeJob"),
    ABORT_JOB("abortJob"),
    CREATE_BATCH("createBatch"),
    GET_BATCH("getBatch"),
    GET_ALL_BATCHES(
                    "getAllBatches"),
    GET_REQUEST(
                "getRequest"),
    GET_RESULTS("getResults"),
    CREATE_BATCH_QUERY("createBatchQuery"),
    GET_QUERY_RESULT_IDS("getQueryResultIds"),
    GET_QUERY_RESULT("getQueryResult"),

    // Bulk API 2.0
    BULK2_CREATE_JOB("bulk2CreateJob"),
    BULK2_GET_JOB("bulk2GetJob"),
    BULK2_CREATE_BATCH("bulk2CreateBatch"),
    BULK2_CLOSE_JOB("bulk2CloseJob"),
    BULK2_ABORT_JOB("bulk2AbortJob"),
    BULK2_DELETE_JOB("bulk2DeleteJob"),
    BULK2_GET_SUCCESSFUL_RESULTS("bulk2GetSuccessfulResults"),
    BULK2_GET_FAILED_RESULTS("bulk2GetFailedResults"),
    BULK2_GET_UNPROCESSED_RECORDS("bulk2GetUnprocessedRecords"),
    BULK2_GET_ALL_JOBS("bulk2GetAllJobs"),
    BULK2_CREATE_QUERY_JOB("bulk2CreateQueryJob"),
    BULK2_GET_QUERY_JOB("bulk2GetQueryJob"),
    BULK2_GET_ALL_QUERY_JOBS("bulk2GetAllQueryJobs"),
    BULK2_GET_QUERY_JOB_RESULTS("bulk2GetQueryJobResults"),
    BULK2_ABORT_QUERY_JOB("bulk2AbortQueryJob"),
    BULK2_DELETE_QUERY_JOB("bulk2DeleteQueryJob"),

    // analytics API
    GET_RECENT_REPORTS("getRecentReports"),
    GET_REPORT_DESCRIPTION("getReportDescription"),
    EXECUTE_SYNCREPORT("executeSyncReport"),
    EXECUTE_ASYNCREPORT(
                        "executeAsyncReport"),
    GET_REPORT_INSTANCES("getReportInstances"),
    GET_REPORT_RESULTS("getReportResults"),

    // limits API
    LIMITS("limits"),

    // Approval Processes and Process Rules API
    APPROVAL("approval"),
    APPROVALS("approvals"),

    // Composite API
    COMPOSITE("composite"),
    COMPOSITE_BATCH("composite-batch"),
    COMPOSITE_TREE("composite-tree"),

    // Composite sObject Collections API
    COMPOSITE_CREATE_SOBJECT_COLLECTIONS("compositeCreateSObjectCollections"),
    COMPOSITE_UPDATE_SOBJECT_COLLECTIONS("compositeUpdateSObjectCollections"),
    COMPOSITE_UPSERT_SOBJECT_COLLECTIONS("compositeUpsertSObjectCollections"),
    COMPOSITE_RETRIEVE_SOBJECT_COLLECTIONS("compositeRetrieveSObjectCollections"),
    COMPOSITE_DELETE_SOBJECT_COLLECTIONS("compositeDeleteSObjectCollections"),

    // Raw operation
    RAW("raw"),

    // Streaming API
    SUBSCRIBE("subscribe"),

    // Pub/Sub API
    PUBSUB_PUBLISH("pubSubPublish"),
    PUBSUB_SUBSCRIBE("pubSubSubscribe");

    private final String value;

    OperationName(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static OperationName fromValue(String value) {
        for (OperationName operationName : OperationName.values()) {
            if (operationName.value.equals(value)) {
                return operationName;
            }
        }
        throw new IllegalArgumentException(value);
    }
}
