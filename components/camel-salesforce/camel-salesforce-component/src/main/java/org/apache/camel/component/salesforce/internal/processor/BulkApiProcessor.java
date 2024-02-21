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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.StreamCache;
import org.apache.camel.component.salesforce.SalesforceEndpoint;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.bulk.BatchInfo;
import org.apache.camel.component.salesforce.api.dto.bulk.ContentType;
import org.apache.camel.component.salesforce.api.dto.bulk.JobInfo;
import org.apache.camel.component.salesforce.internal.client.BulkApiClient;
import org.apache.camel.component.salesforce.internal.client.DefaultBulkApiClient;
import org.apache.camel.converter.stream.StreamCacheConverter;
import org.apache.camel.support.service.ServiceHelper;

import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.BATCH_ID;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.CONTENT_TYPE;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.JOB_ID;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.RESULT_ID;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.SOBJECT_QUERY;

public class BulkApiProcessor extends AbstractSalesforceProcessor {

    private BulkApiClient bulkClient;

    public BulkApiProcessor(SalesforceEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.bulkClient = new DefaultBulkApiClient(
                (String) endpointConfigMap.get(SalesforceEndpointConfig.API_VERSION), session, httpClient, loginConfig);
        ServiceHelper.startService(bulkClient);
    }

    @Override
    public void doStop() {
        // stop the client
        ServiceHelper.stopService(bulkClient);
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        boolean done = false;

        try {
            switch (operationName) {
                case CREATE_JOB:
                    processCreateJob(exchange, callback);
                    break;
                case GET_JOB:
                    processGetJob(exchange, callback);
                    break;
                case CLOSE_JOB:
                    processCloseJob(exchange, callback);
                    break;
                case ABORT_JOB:
                    processAbortJob(exchange, callback);
                    break;
                case CREATE_BATCH:
                    processCreateBatch(exchange, callback);
                    break;
                case GET_BATCH:
                    processGetBatch(exchange, callback);
                    break;
                case GET_ALL_BATCHES:
                    processGetAllBatches(exchange, callback);
                    break;
                case GET_REQUEST:
                    processGetRequest(exchange, callback);
                    break;
                case GET_RESULTS:
                    processGetResults(exchange, callback);
                    break;
                case CREATE_BATCH_QUERY:
                    processCreateBatchQuery(exchange, callback);
                    break;
                case GET_QUERY_RESULT_IDS:
                    processGetQueryResultIds(exchange, callback);
                    break;
                case GET_QUERY_RESULT:
                    processGetQueryResult(exchange, callback);
                    break;
                default:
                    throw new SalesforceException("Unknown operation name: " + operationName.value(), null);
            }
        } catch (SalesforceException e) {
            exchange.setException(new SalesforceException(
                    String.format("Error processing %s: [%s] \"%s\"", operationName.value(), e.getStatusCode(), e.getMessage()),
                    e));
            callback.done(true);
            done = true;
        } catch (InvalidPayloadException | RuntimeException e) {
            exchange.setException(new SalesforceException(
                    String.format("Unexpected Error processing %s: \"%s\"", operationName.value(), e.getMessage()), e));
            callback.done(true);
            done = true;
        }

        // continue routing asynchronously if false
        return done;
    }

    @Override
    public Map<String, List<String>> determineHeaders(Exchange exchange) {
        Map<String, List<String>> headers = super.determineHeaders(exchange);
        try {
            Boolean pkChunking = getParameter(
                    SalesforceEndpointConfig.PK_CHUNKING, exchange, IGNORE_BODY, IS_OPTIONAL,
                    Boolean.class);
            if (pkChunking != null && pkChunking) {
                List<String> values = new ArrayList<>();
                values.add("true");
                Integer chunkSize = getParameter(
                        SalesforceEndpointConfig.PK_CHUNKING_CHUNK_SIZE, exchange, IGNORE_BODY, IS_OPTIONAL,
                        Integer.class);
                if (chunkSize != null) {
                    values.add("chunkSize=" + chunkSize);
                }
                String startRow = getParameter(
                        SalesforceEndpointConfig.PK_CHUNKING_START_ROW, exchange, IGNORE_BODY, IS_OPTIONAL,
                        String.class);
                if (startRow != null) {
                    values.add("startRow=" + startRow);
                }
                String parent = getParameter(
                        SalesforceEndpointConfig.PK_CHUNKING_PARENT, exchange, IGNORE_BODY, IS_OPTIONAL,
                        String.class);
                if (parent != null) {
                    values.add("parent=" + parent);
                }
                headers.put("Sforce-Enable-PKChunking", values);
            }
        } catch (SalesforceException e) {
            throw new RuntimeException(e);
        }
        return headers;
    }

    private void processCreateJob(final Exchange exchange, final AsyncCallback callback)
            throws InvalidPayloadException {
        JobInfo jobBody = exchange.getIn().getMandatoryBody(JobInfo.class);
        bulkClient.createJob(jobBody, determineHeaders(exchange), new BulkApiClient.JobInfoResponseCallback() {
            @Override
            public void onResponse(JobInfo jobInfo, Map<String, String> headers, SalesforceException ex) {
                processResponse(exchange, jobInfo, headers, ex, callback);
            }
        });
    }

    private void processGetJob(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        JobInfo jobBody;
        jobBody = exchange.getIn().getBody(JobInfo.class);
        String jobId;
        if (jobBody != null) {
            jobId = jobBody.getId();
        } else {
            jobId = getParameter(JOB_ID, exchange, USE_BODY, NOT_OPTIONAL);
        }
        bulkClient.getJob(jobId, determineHeaders(exchange), new BulkApiClient.JobInfoResponseCallback() {
            @Override
            public void onResponse(JobInfo jobInfo, Map<String, String> headers, SalesforceException ex) {
                processResponse(exchange, jobInfo, headers, ex, callback);
            }
        });
    }

    private void processCloseJob(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        JobInfo jobBody;
        String jobId;
        jobBody = exchange.getIn().getBody(JobInfo.class);
        if (jobBody != null) {
            jobId = jobBody.getId();
        } else {
            jobId = getParameter(JOB_ID, exchange, USE_BODY, NOT_OPTIONAL);
        }
        bulkClient.closeJob(jobId, determineHeaders(exchange), new BulkApiClient.JobInfoResponseCallback() {
            @Override
            public void onResponse(JobInfo jobInfo, Map<String, String> headers, SalesforceException ex) {
                processResponse(exchange, jobInfo, headers, ex, callback);
            }
        });
    }

    private void processAbortJob(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        JobInfo jobBody;
        String jobId;
        jobBody = exchange.getIn().getBody(JobInfo.class);
        if (jobBody != null) {
            jobId = jobBody.getId();
        } else {
            jobId = getParameter(JOB_ID, exchange, USE_BODY, NOT_OPTIONAL);
        }
        bulkClient.abortJob(jobId, determineHeaders(exchange), new BulkApiClient.JobInfoResponseCallback() {
            @Override
            public void onResponse(JobInfo jobInfo, Map<String, String> headers, SalesforceException ex) {
                processResponse(exchange, jobInfo, headers, ex, callback);
            }
        });
    }

    private void processCreateBatch(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        String jobId;
        // since request is in the body, use headers or endpoint params
        ContentType contentType = ContentType.fromValue(getParameter(CONTENT_TYPE, exchange, IGNORE_BODY, NOT_OPTIONAL));
        jobId = getParameter(JOB_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);

        InputStream request;
        try {
            request = exchange.getIn().getMandatoryBody(InputStream.class);
        } catch (CamelException e) {
            String msg = "Error preparing batch request: " + e.getMessage();
            throw new SalesforceException(msg, e);
        }

        bulkClient.createBatch(request, jobId, contentType, determineHeaders(exchange),
                new BulkApiClient.BatchInfoResponseCallback() {
                    @Override
                    public void onResponse(BatchInfo batchInfo, Map<String, String> headers, SalesforceException ex) {
                        processResponse(exchange, batchInfo, headers, ex, callback);
                    }
                });
    }

    private void processGetBatch(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        String jobId;
        BatchInfo batchBody = exchange.getIn().getBody(BatchInfo.class);
        String batchId;
        if (batchBody != null) {
            jobId = batchBody.getJobId();
            batchId = batchBody.getId();
        } else {
            jobId = getParameter(JOB_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
            batchId = getParameter(BATCH_ID, exchange, USE_BODY, NOT_OPTIONAL);
        }
        bulkClient.getBatch(jobId, batchId, determineHeaders(exchange), new BulkApiClient.BatchInfoResponseCallback() {
            @Override
            public void onResponse(BatchInfo batchInfo, Map<String, String> headers, SalesforceException ex) {
                processResponse(exchange, batchInfo, headers, ex, callback);
            }
        });
    }

    private void processGetAllBatches(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        JobInfo jobBody;
        String jobId;
        jobBody = exchange.getIn().getBody(JobInfo.class);
        if (jobBody != null) {
            jobId = jobBody.getId();
        } else {
            jobId = getParameter(JOB_ID, exchange, USE_BODY, NOT_OPTIONAL);
        }
        bulkClient.getAllBatches(jobId, determineHeaders(exchange), new BulkApiClient.BatchInfoListResponseCallback() {
            @Override
            public void onResponse(List<BatchInfo> batchInfoList, Map<String, String> headers, SalesforceException ex) {
                processResponse(exchange, batchInfoList, headers, ex, callback);
            }
        });
    }

    private void processGetRequest(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        String jobId;
        BatchInfo batchBody;
        String batchId;
        batchBody = exchange.getIn().getBody(BatchInfo.class);
        if (batchBody != null) {
            jobId = batchBody.getJobId();
            batchId = batchBody.getId();
        } else {
            jobId = getParameter(JOB_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
            batchId = getParameter(BATCH_ID, exchange, USE_BODY, NOT_OPTIONAL);
        }

        bulkClient.getRequest(jobId, batchId, determineHeaders(exchange), new BulkApiClient.StreamResponseCallback() {
            @Override
            public void onResponse(InputStream inputStream, Map<String, String> headers, SalesforceException ex) {
                // read the request stream into a StreamCache temp file
                // ensures the connection is read
                StreamCache body = null;
                if (inputStream != null) {
                    try {
                        body = StreamCacheConverter.convertToStreamCache(inputStream, exchange);
                    } catch (IOException e) {
                        String msg = "Error retrieving batch request: " + e.getMessage();
                        ex = new SalesforceException(msg, e);
                    } finally {
                        // close the input stream to release the Http connection
                        try {
                            inputStream.close();
                        } catch (IOException ignore) {
                        }
                    }
                }
                processResponse(exchange, body, headers, ex, callback);
            }
        });
    }

    private void processGetResults(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        String jobId;
        BatchInfo batchBody;
        String batchId;
        batchBody = exchange.getIn().getBody(BatchInfo.class);
        if (batchBody != null) {
            jobId = batchBody.getJobId();
            batchId = batchBody.getId();
        } else {
            jobId = getParameter(JOB_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
            batchId = getParameter(BATCH_ID, exchange, USE_BODY, NOT_OPTIONAL);
        }
        bulkClient.getResults(jobId, batchId, determineHeaders(exchange), new BulkApiClient.StreamResponseCallback() {
            @Override
            public void onResponse(InputStream inputStream, Map<String, String> headers, SalesforceException ex) {
                // read the result stream into a StreamCache temp file
                // ensures the connection is read
                StreamCache body = null;
                if (inputStream != null) {
                    try {
                        body = StreamCacheConverter.convertToStreamCache(inputStream, exchange);
                    } catch (IOException e) {
                        String msg = "Error retrieving batch results: " + e.getMessage();
                        ex = new SalesforceException(msg, e);
                    } finally {
                        // close the input stream to release the Http connection
                        try {
                            inputStream.close();
                        } catch (IOException ignore) {
                        }
                    }
                }
                processResponse(exchange, body, headers, ex, callback);
            }
        });
    }

    private void processCreateBatchQuery(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        JobInfo jobBody;
        String jobId;
        ContentType contentType;
        jobBody = exchange.getIn().getBody(JobInfo.class);
        String soqlQuery;
        if (jobBody != null) {
            jobId = jobBody.getId();
            contentType = jobBody.getContentType();
            // use SOQL query from header or endpoint config
            soqlQuery = getParameter(SOBJECT_QUERY, exchange, IGNORE_BODY, NOT_OPTIONAL);
        } else {
            jobId = getParameter(JOB_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
            contentType = ContentType.fromValue(getParameter(CONTENT_TYPE, exchange, IGNORE_BODY, NOT_OPTIONAL));
            // reuse SOBJECT_QUERY property
            soqlQuery = getParameter(SOBJECT_QUERY, exchange, USE_BODY, NOT_OPTIONAL);
        }
        bulkClient.createBatchQuery(jobId, soqlQuery, contentType, determineHeaders(exchange),
                new BulkApiClient.BatchInfoResponseCallback() {
                    @Override
                    public void onResponse(BatchInfo batchInfo, Map<String, String> headers, SalesforceException ex) {
                        processResponse(exchange, batchInfo, headers, ex, callback);
                    }
                });
    }

    private void processGetQueryResultIds(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        String jobId;
        BatchInfo batchBody;
        String batchId;
        batchBody = exchange.getIn().getBody(BatchInfo.class);
        if (batchBody != null) {
            jobId = batchBody.getJobId();
            batchId = batchBody.getId();
        } else {
            jobId = getParameter(JOB_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
            batchId = getParameter(BATCH_ID, exchange, USE_BODY, NOT_OPTIONAL);
        }
        bulkClient.getQueryResultIds(jobId, batchId, determineHeaders(exchange), new BulkApiClient.QueryResultIdsCallback() {
            @Override
            public void onResponse(List<String> ids, Map<String, String> headers, SalesforceException ex) {
                processResponse(exchange, ids, headers, ex, callback);
            }
        });
    }

    private void processGetQueryResult(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        String jobId;
        BatchInfo batchBody;
        String batchId;
        batchBody = exchange.getIn().getBody(BatchInfo.class);
        String resultId;
        if (batchBody != null) {
            jobId = batchBody.getJobId();
            batchId = batchBody.getId();
            resultId = getParameter(RESULT_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
        } else {
            jobId = getParameter(JOB_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
            batchId = getParameter(BATCH_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
            resultId = getParameter(RESULT_ID, exchange, USE_BODY, NOT_OPTIONAL);
        }
        bulkClient.getQueryResult(jobId, batchId, resultId, determineHeaders(exchange),
                new BulkApiClient.StreamResponseCallback() {
                    @Override
                    public void onResponse(InputStream inputStream, Map<String, String> headers, SalesforceException ex) {
                        StreamCache body = null;
                        if (inputStream != null) {
                            // read the result stream into a StreamCache temp file
                            // ensures the connection is read
                            try {
                                body = StreamCacheConverter.convertToStreamCache(inputStream, exchange);
                            } catch (IOException e) {
                                String msg = "Error retrieving query result: " + e.getMessage();
                                ex = new SalesforceException(msg, e);
                            } finally {
                                // close the input stream to release the Http connection
                                try {
                                    inputStream.close();
                                } catch (IOException e) {
                                    // ignore
                                }
                            }
                        }
                        processResponse(exchange, body, headers, ex, callback);
                    }
                });
    }

    private void processResponse(
            Exchange exchange, Object body, Map<String, String> headers, SalesforceException ex, AsyncCallback callback) {
        final Message out = exchange.getOut();
        if (ex != null) {
            exchange.setException(ex);
        } else {
            out.setBody(body);
        }

        // copy headers
        Message inboundMessage = exchange.getIn();
        Map<String, Object> outboundHeaders = out.getHeaders();
        outboundHeaders.putAll(inboundMessage.getHeaders());
        outboundHeaders.putAll(headers);

        // signal exchange completion
        callback.done(false);
    }
}
