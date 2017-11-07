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
package org.apache.camel.component.salesforce.internal;

public enum OperationName {

    // rest API
    GET_VERSIONS("getVersions"),
    GET_RESOURCES("getResources"),
    GET_GLOBAL_OBJECTS("getGlobalObjects"),
    GET_BASIC_INFO("getBasicInfo"),
    GET_DESCRIPTION("getDescription"),
    GET_SOBJECT("getSObject"),
    CREATE_SOBJECT("createSObject"),
    UPDATE_SOBJECT("updateSObject"),
    DELETE_SOBJECT("deleteSObject"),
    GET_SOBJECT_WITH_ID("getSObjectWithId"),
    UPSERT_SOBJECT("upsertSObject"),
    DELETE_SOBJECT_WITH_ID("deleteSObjectWithId"),
    GET_BLOB_FIELD("getBlobField"),
    QUERY("query"),
    QUERY_MORE("queryMore"),
    QUERY_ALL("queryAll"),
    SEARCH("search"),
    APEX_CALL("apexCall"),
    RECENT("recent"),

    // bulk API
    CREATE_JOB("createJob"),
    GET_JOB("getJob"),
    CLOSE_JOB("closeJob"),
    ABORT_JOB("abortJob"),
    CREATE_BATCH("createBatch"),
    GET_BATCH("getBatch"),
    GET_ALL_BATCHES("getAllBatches"),
    GET_REQUEST("getRequest"),
    GET_RESULTS("getResults"),
    CREATE_BATCH_QUERY("createBatchQuery"),
    GET_QUERY_RESULT_IDS("getQueryResultIds"),
    GET_QUERY_RESULT("getQueryResult"),
    
    // analytics API
    GET_RECENT_REPORTS("getRecentReports"),
    GET_REPORT_DESCRIPTION("getReportDescription"),
    EXECUTE_SYNCREPORT("executeSyncReport"),
    EXECUTE_ASYNCREPORT("executeAsyncReport"),
    GET_REPORT_INSTANCES("getReportInstances"),
    GET_REPORT_RESULTS("getReportResults"),

    // limits API
    LIMITS("limits"),

    // Approval Processes and Process Rules API
    APPROVAL("approval"),
    APPROVALS("approvals"),

    // Composite API
    COMPOSITE_TREE("composite-tree"),
    COMPOSITE_BATCH("composite-batch"),
    COMPOSITE("composite");

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
