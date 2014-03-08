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
import java.util.List;

import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.bulk.BatchInfo;
import org.apache.camel.component.salesforce.api.dto.bulk.ContentType;
import org.apache.camel.component.salesforce.api.dto.bulk.JobInfo;

/**
 * Client interface for Salesforce Bulk API
 */
public interface BulkApiClient {

    public interface JobInfoResponseCallback {
        void onResponse(JobInfo jobInfo, SalesforceException ex);
    }

    public interface BatchInfoResponseCallback {
        void onResponse(BatchInfo batchInfo, SalesforceException ex);
    }

    public interface BatchInfoListResponseCallback {
        void onResponse(List<BatchInfo> batchInfoList, SalesforceException ex);
    }

    public interface StreamResponseCallback {
        void onResponse(InputStream inputStream, SalesforceException ex);
    }

    public interface QueryResultIdsCallback {
        void onResponse(List<String> ids, SalesforceException ex);
    }

    /**
     * Creates a Bulk Job
     *
     * @param jobInfo  {@link JobInfo} with required fields
     * @param callback {@link JobInfoResponseCallback} to be invoked on response or error
     */
    void createJob(JobInfo jobInfo,
                   JobInfoResponseCallback callback);

    void getJob(String jobId,
                JobInfoResponseCallback callback);

    void closeJob(String jobId,
                  JobInfoResponseCallback callback);

    void abortJob(String jobId,
                  JobInfoResponseCallback callback);

    void createBatch(InputStream batchStream, String jobId, ContentType contentTypeEnum,
                     BatchInfoResponseCallback callback);

    void getBatch(String jobId, String batchId,
                  BatchInfoResponseCallback callback);

    void getAllBatches(String jobId,
                       BatchInfoListResponseCallback callback);

    void getRequest(String jobId, String batchId,
                    StreamResponseCallback callback);

    void getResults(String jobId, String batchId,
                    StreamResponseCallback callback);

    void createBatchQuery(String jobId, String soqlQuery, ContentType jobContentType,
                          BatchInfoResponseCallback callback);

    void getQueryResultIds(String jobId, String batchId,
                           QueryResultIdsCallback callback);

    void getQueryResult(String jobId, String batchId, String resultId,
                        StreamResponseCallback callback);

}
