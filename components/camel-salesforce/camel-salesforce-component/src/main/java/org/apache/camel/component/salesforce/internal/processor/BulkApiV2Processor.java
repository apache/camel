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
package org.apache.camel.component.salesforce.internal.processor;

import java.io.InputStream;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.component.salesforce.SalesforceEndpoint;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.bulkv2.Job;
import org.apache.camel.component.salesforce.api.dto.bulkv2.JobStateEnum;
import org.apache.camel.component.salesforce.api.dto.bulkv2.Jobs;
import org.apache.camel.component.salesforce.api.dto.bulkv2.QueryJob;
import org.apache.camel.component.salesforce.api.dto.bulkv2.QueryJobs;
import org.apache.camel.component.salesforce.internal.client.BulkApiV2Client;
import org.apache.camel.component.salesforce.internal.client.DefaultBulkApiV2Client;
import org.apache.camel.support.service.ServiceHelper;

import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.JOB_ID;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.LOCATOR;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.MAX_RECORDS;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.QUERY_LOCATOR;
import static org.apache.camel.component.salesforce.internal.client.BulkApiV2Client.JobResponseCallback;
import static org.apache.camel.component.salesforce.internal.client.BulkApiV2Client.ResponseCallback;
import static org.apache.camel.component.salesforce.internal.client.BulkApiV2Client.StreamResponseCallback;

public class BulkApiV2Processor extends AbstractSalesforceProcessor {

    private BulkApiV2Client bulkClient;

    public BulkApiV2Processor(SalesforceEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        boolean done = false;

        try {
            switch (operationName) {
                case BULK2_CREATE_JOB:
                    processCreateJob(exchange, callback);
                    break;
                case BULK2_GET_JOB:
                    processGetJob(exchange, callback);
                    break;
                case BULK2_CREATE_BATCH:
                    processCreateBatch(exchange, callback);
                    break;
                case BULK2_CLOSE_JOB:
                    processCloseJob(exchange, callback);
                    break;
                case BULK2_ABORT_JOB:
                    processAbortJob(exchange, callback);
                    break;
                case BULK2_DELETE_JOB:
                    deleteJob(exchange, callback);
                    break;
                case BULK2_GET_SUCCESSFUL_RESULTS:
                    processGetSuccessfulResults(exchange, callback);
                    break;
                case BULK2_GET_FAILED_RESULTS:
                    processGetFailedResults(exchange, callback);
                    break;
                case BULK2_GET_UNPROCESSED_RECORDS:
                    processGetUnprocessedRecords(exchange, callback);
                    break;
                case BULK2_GET_ALL_JOBS:
                    processGetAllJobs(exchange, callback);
                    break;
                case BULK2_CREATE_QUERY_JOB:
                    processCreateQueryJob(exchange, callback);
                    break;
                case BULK2_GET_QUERY_JOB:
                    processGetQueryJob(exchange, callback);
                    break;
                case BULK2_GET_QUERY_JOB_RESULTS:
                    processGetQueryJobResults(exchange, callback);
                    break;
                case BULK2_ABORT_QUERY_JOB:
                    processAbortQueryJob(exchange, callback);
                    break;
                case BULK2_DELETE_QUERY_JOB:
                    processDeleteQueryJob(exchange, callback);
                    break;
                case BULK2_GET_ALL_QUERY_JOBS:
                    processGetAllQueryJobs(exchange, callback);
                    break;
                default:
                    throw new SalesforceException(
                            "Unknown operation name: " + operationName.value(), null);
            }
        } catch (SalesforceException e) {
            exchange.setException(new SalesforceException(
                    String.format("Error processing %s: [%s] \"%s\"", operationName.value(),
                            e.getStatusCode(), e.getMessage()),
                    e));
            callback.done(true);
            done = true;
        } catch (InvalidPayloadException | RuntimeException e) {
            exchange.setException(new SalesforceException(
                    String.format("Unexpected Error processing %s: \"%s\"", operationName.value(),
                            e.getMessage()),
                    e));
            callback.done(true);
            done = true;
        }

        // continue routing asynchronously if false
        return done;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.bulkClient = new DefaultBulkApiV2Client(
                (String) endpointConfigMap.get(SalesforceEndpointConfig.API_VERSION), session,
                httpClient, loginConfig, endpoint);
        ServiceHelper.startService(bulkClient);
    }

    @Override
    public void doStop() {
        // stop the client
        ServiceHelper.stopService(bulkClient);
    }

    private void processCreateJob(Exchange exchange, AsyncCallback callback)
            throws InvalidPayloadException {
        Job job = exchange.getIn().getMandatoryBody(Job.class);
        bulkClient.createJob(job, determineHeaders(exchange),
                new JobResponseCallback() {
                    @Override
                    public void onResponse(Job job, Map<String, String> headers, SalesforceException ex) {
                        processResponse(exchange, job, headers, ex, callback);
                    }
                });
    }

    private void processGetJob(Exchange exchange, AsyncCallback callback)
            throws SalesforceException {
        Job job = exchange.getIn().getBody(Job.class);
        String jobId;
        if (job != null) {
            jobId = job.getId();
        } else {
            jobId = getParameter(JOB_ID, exchange, USE_BODY, NOT_OPTIONAL);
        }
        bulkClient.getJob(jobId, determineHeaders(exchange),
                new JobResponseCallback() {
                    @Override
                    public void onResponse(Job job, Map<String, String> headers, SalesforceException ex) {
                        processResponse(exchange, job, headers, ex, callback);
                    }
                });
    }

    private void processCreateBatch(Exchange exchange, AsyncCallback callback)
            throws SalesforceException {
        String jobId = getParameter(JOB_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
        InputStream input;
        try {
            input = exchange.getIn().getMandatoryBody(InputStream.class);
        } catch (CamelException e) {
            String msg = "Error preparing batch request: " + e.getMessage();
            throw new SalesforceException(msg, e);
        }
        bulkClient.createBatch(input, jobId, determineHeaders(exchange),
                new ResponseCallback() {
                    @Override
                    public void onResponse(Map<String, String> headers, SalesforceException ex) {
                        processResponse(exchange, null, headers, ex, callback);
                    }
                });
    }

    private void deleteJob(Exchange exchange, AsyncCallback callback) throws SalesforceException {
        String jobId = getParameter(JOB_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
        bulkClient.deleteJob(jobId, determineHeaders(exchange),
                new ResponseCallback() {
                    @Override
                    public void onResponse(Map<String, String> headers, SalesforceException ex) {
                        processResponse(exchange, null, headers, ex, callback);
                    }
                });
    }

    private void processAbortJob(Exchange exchange, AsyncCallback callback)
            throws SalesforceException {
        String jobId = getParameter(JOB_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
        bulkClient.changeJobState(jobId, JobStateEnum.ABORTED, determineHeaders(exchange),
                new JobResponseCallback() {
                    @Override
                    public void onResponse(Job job, Map<String, String> headers, SalesforceException ex) {
                        processResponse(exchange, job, headers, ex, callback);
                    }
                });
    }

    private void processCloseJob(Exchange exchange, AsyncCallback callback)
            throws SalesforceException {
        String jobId = getParameter(JOB_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
        bulkClient.changeJobState(jobId, JobStateEnum.UPLOAD_COMPLETE, determineHeaders(exchange),
                new JobResponseCallback() {
                    @Override
                    public void onResponse(Job job, Map<String, String> headers, SalesforceException ex) {
                        processResponse(exchange, job, headers, ex, callback);
                    }
                });
    }

    private void processGetAllJobs(Exchange exchange, AsyncCallback callback)
            throws SalesforceException {
        String queryLocator = getParameter(QUERY_LOCATOR, exchange, IGNORE_BODY, IS_OPTIONAL);
        bulkClient.getAllJobs(queryLocator, determineHeaders(exchange),
                new BulkApiV2Client.JobsResponseCallback() {
                    @Override
                    public void onResponse(Jobs jobs, Map<String, String> headers, SalesforceException ex) {
                        BulkApiV2Processor.this.processResponse(exchange, jobs, headers, ex, callback);
                    }
                });
    }

    private void processGetSuccessfulResults(Exchange exchange, AsyncCallback callback)
            throws SalesforceException {
        String jobId = getParameter(JOB_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
        bulkClient.getSuccessfulResults(jobId, determineHeaders(exchange),
                new StreamResponseCallback() {
                    @Override
                    public void onResponse(
                            InputStream inputStream, Map<String, String> headers, SalesforceException ex) {
                        processResponse(exchange, inputStream, headers, ex, callback);
                    }
                });
    }

    private void processGetFailedResults(Exchange exchange, AsyncCallback callback)
            throws SalesforceException {
        String jobId = getParameter(JOB_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
        bulkClient.getFailedResults(jobId, determineHeaders(exchange),
                new StreamResponseCallback() {
                    @Override
                    public void onResponse(
                            InputStream inputStream, Map<String, String> headers, SalesforceException ex) {
                        processResponse(exchange, inputStream, headers, ex, callback);
                    }
                });
    }

    private void processGetUnprocessedRecords(Exchange exchange, AsyncCallback callback)
            throws SalesforceException {
        String jobId = getParameter(JOB_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
        bulkClient.getUnprocessedRecords(jobId, determineHeaders(exchange),
                new StreamResponseCallback() {
                    @Override
                    public void onResponse(
                            InputStream inputStream, Map<String, String> headers, SalesforceException ex) {
                        processResponse(exchange, inputStream, headers, ex, callback);
                    }
                });
    }

    private void processCreateQueryJob(Exchange exchange, AsyncCallback callback)
            throws InvalidPayloadException {
        QueryJob job = exchange.getIn().getMandatoryBody(QueryJob.class);
        bulkClient.createQueryJob(job, determineHeaders(exchange),
                new BulkApiV2Client.QueryJobResponseCallback() {
                    @Override
                    public void onResponse(
                            QueryJob job, Map<String, String> headers, SalesforceException ex) {
                        processResponse(exchange, job, headers, ex, callback);
                    }
                });
    }

    private void processGetQueryJob(Exchange exchange, AsyncCallback callback)
            throws SalesforceException {
        QueryJob job = exchange.getIn().getBody(QueryJob.class);
        String jobId;
        if (job != null) {
            jobId = job.getId();
        } else {
            jobId = getParameter(JOB_ID, exchange, USE_BODY, NOT_OPTIONAL);
        }
        bulkClient.getQueryJob(jobId, determineHeaders(exchange),
                new BulkApiV2Client.QueryJobResponseCallback() {
                    @Override
                    public void onResponse(QueryJob job, Map<String, String> headers, SalesforceException ex) {
                        processResponse(exchange, job, headers, ex, callback);
                    }
                });
    }

    private void processGetQueryJobResults(Exchange exchange, AsyncCallback callback)
            throws SalesforceException {
        String jobId = getParameter(JOB_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
        String locator = getParameter(LOCATOR, exchange, false, true);
        Integer maxRecords = getParameter(MAX_RECORDS, exchange, false, true, Integer.class);
        bulkClient.getQueryJobResults(jobId, locator, maxRecords, determineHeaders(exchange),
                new StreamResponseCallback() {
                    @Override
                    public void onResponse(InputStream inputStream, Map<String, String> headers, SalesforceException ex) {
                        processResponse(exchange, inputStream, headers, ex, callback);
                    }
                });
    }

    private void processAbortQueryJob(Exchange exchange, AsyncCallback callback)
            throws SalesforceException {
        String jobId = getParameter(JOB_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
        bulkClient.changeQueryJobState(jobId, JobStateEnum.ABORTED, determineHeaders(exchange),
                new BulkApiV2Client.QueryJobResponseCallback() {
                    @Override
                    public void onResponse(QueryJob job, Map<String, String> headers, SalesforceException ex) {
                        processResponse(exchange, job, headers, ex, callback);
                    }
                });
    }

    private void processDeleteQueryJob(Exchange exchange, AsyncCallback callback)
            throws SalesforceException {
        String jobId = getParameter(JOB_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
        bulkClient.deleteQueryJob(jobId, determineHeaders(exchange),
                new ResponseCallback() {
                    @Override
                    public void onResponse(Map<String, String> headers, SalesforceException ex) {
                        processResponse(exchange, null, headers, ex, callback);
                    }
                });
    }

    private void processGetAllQueryJobs(Exchange exchange, AsyncCallback callback)
            throws SalesforceException {
        String queryLocator = getParameter(QUERY_LOCATOR, exchange, IGNORE_BODY, IS_OPTIONAL);
        bulkClient.getAllQueryJobs(queryLocator, determineHeaders(exchange),
                new BulkApiV2Client.QueryJobsResponseCallback() {
                    @Override
                    public void onResponse(QueryJobs jobs, Map<String, String> headers, SalesforceException ex) {
                        processResponse(exchange, jobs, headers, ex, callback);
                    }
                });
    }

    private void processResponse(
            Exchange exchange, Object body, Map<String, String> headers, SalesforceException ex,
            AsyncCallback callback) {
        final Message message = exchange.getMessage();
        if (ex != null) {
            exchange.setException(ex);
        } else {
            message.setBody(body);
        }
        message.getHeaders().putAll(headers);
        callback.done(false);
    }
}
