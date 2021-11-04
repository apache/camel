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
import org.apache.camel.component.salesforce.api.dto.bulkv2.Job;
import org.apache.camel.component.salesforce.api.dto.bulkv2.JobStateEnum;
import org.apache.camel.component.salesforce.api.dto.bulkv2.Jobs;
import org.apache.camel.component.salesforce.api.dto.bulkv2.QueryJob;
import org.apache.camel.component.salesforce.api.dto.bulkv2.QueryJobs;

public interface BulkApiV2Client {

    interface JobResponseCallback {
        void onResponse(Job job, Map<String, String> headers, SalesforceException ex);
    }

    interface JobsResponseCallback {
        void onResponse(Jobs jobs, Map<String, String> headers, SalesforceException ex);
    }

    interface ResponseCallback {
        void onResponse(Map<String, String> headers, SalesforceException ex);
    }

    interface StreamResponseCallback {
        void onResponse(InputStream inputStream, Map<String, String> headers, SalesforceException ex);
    }

    interface QueryJobResponseCallback {
        void onResponse(QueryJob queryJob, Map<String, String> headers, SalesforceException ex);
    }

    interface QueryJobsResponseCallback {
        void onResponse(QueryJobs queryJobs, Map<String, String> headers, SalesforceException ex);
    }

    void createJob(Job job, Map<String, List<String>> header, JobResponseCallback callback);

    void getAllJobs(String queryLocator, Map<String, List<String>> headers, JobsResponseCallback callback);

    void getJob(String jobId, Map<String, List<String>> header, JobResponseCallback callback);

    void createBatch(
            InputStream batchStream, String jobId, Map<String, List<String>> headers, ResponseCallback callback);

    void changeJobState(
            String jobId, JobStateEnum state, Map<String, List<String>> headers, JobResponseCallback callback);

    void deleteJob(String jobId, Map<String, List<String>> headers, ResponseCallback callback);

    void getSuccessfulResults(String jobId, Map<String, List<String>> headers, StreamResponseCallback callback);

    void getFailedResults(String jobId, Map<String, List<String>> headers, StreamResponseCallback callback);

    void getUnprocessedRecords(String jobId, Map<String, List<String>> headers, StreamResponseCallback callback);

    void createQueryJob(QueryJob queryJob, Map<String, List<String>> headers, QueryJobResponseCallback callback);

    void getQueryJob(String jobId, Map<String, List<String>> headers, QueryJobResponseCallback callback);

    void getQueryJobResults(
            String jobId, String locator, Integer maxRecords, Map<String, List<String>> headers,
            StreamResponseCallback callback);

    void changeQueryJobState(
            String jobId, JobStateEnum state, Map<String, List<String>> headers, QueryJobResponseCallback callback);

    void deleteQueryJob(String jobId, Map<String, List<String>> headers, ResponseCallback callback);

    void getAllQueryJobs(String queryLocator, Map<String, List<String>> headers, QueryJobsResponseCallback callback);
}
