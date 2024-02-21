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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.salesforce.SalesforceEndpoint;
import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.component.salesforce.SalesforceLoginConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.RestError;
import org.apache.camel.component.salesforce.api.dto.bulkv2.Job;
import org.apache.camel.component.salesforce.api.dto.bulkv2.JobStateEnum;
import org.apache.camel.component.salesforce.api.dto.bulkv2.Jobs;
import org.apache.camel.component.salesforce.api.dto.bulkv2.QueryJob;
import org.apache.camel.component.salesforce.api.dto.bulkv2.QueryJobs;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.eclipse.jetty.client.BytesRequestContent;
import org.eclipse.jetty.client.InputStreamRequestContent;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;

public class DefaultBulkApiV2Client extends AbstractClientBase implements BulkApiV2Client {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ObjectMapper objectMapper;

    public DefaultBulkApiV2Client(String version, SalesforceSession session, SalesforceHttpClient httpClient,
                                  SalesforceLoginConfig loginConfig, SalesforceEndpoint endpoint) throws SalesforceException {
        super(version, session, httpClient, loginConfig);
        if (endpoint.getConfiguration().getObjectMapper() != null) {
            this.objectMapper = endpoint.getConfiguration().getObjectMapper();
        } else {
            this.objectMapper = JsonUtils.createObjectMapper();
        }
    }

    @Override
    public void createJob(Job job, Map<String, List<String>> headers, JobResponseCallback callback) {
        final Request request = getRequest(HttpMethod.POST, jobUrl(null), headers);
        try {
            marshalRequest(job, request);
        } catch (SalesforceException e) {
            callback.onResponse(null, Collections.emptyMap(), e);
            return;
        }
        doHttpRequestWithJobResponse(callback, request);
    }

    @Override
    public void getJob(String jobId, Map<String, List<String>> headers, JobResponseCallback callback) {
        final Request request = getRequest(HttpMethod.GET, jobUrl(jobId), headers);
        doHttpRequestWithJobResponse(callback, request);
    }

    @Override
    public void createBatch(
            InputStream batchStream, String jobId, Map<String, List<String>> headers, ResponseCallback callback) {
        final Request request = getRequest(HttpMethod.PUT, jobUrl(jobId) + "/batches", headers);
        request.body(new InputStreamRequestContent(batchStream));
        request.headers(h -> h.add(HttpHeader.CONTENT_TYPE, "text/csv"));
        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(
                    InputStream response, Map<String, String> headers, SalesforceException ex) {
                callback.onResponse(headers, ex);
            }
        });
    }

    @Override
    public void changeJobState(
            String jobId, JobStateEnum state, Map<String, List<String>> headers, JobResponseCallback callback) {
        final Request request = getRequest(HttpMethod.PATCH, jobUrl(jobId), headers);
        Job job = new Job();
        job.setId(jobId);
        job.setState(state);
        try {
            marshalRequest(job, request);
        } catch (SalesforceException e) {
            callback.onResponse(null, Collections.emptyMap(), e);
            return;
        }
        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                if (ex != null) {
                    callback.onResponse(null, headers, ex);
                }
                Job responseJob = null;
                try {
                    responseJob = unmarshalResponse(response, request, Job.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(responseJob, headers, ex);
            }
        });
    }

    @Override
    public void deleteJob(String jobId, Map<String, List<String>> headers, ResponseCallback callback) {
        final Request request = getRequest(HttpMethod.DELETE, jobUrl(jobId), headers);
        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                callback.onResponse(headers, ex);
            }
        });
    }

    @Override
    public void getSuccessfulResults(
            String jobId, Map<String, List<String>> headers, StreamResponseCallback callback) {
        final Request request = getRequest(HttpMethod.GET, jobUrl(jobId) + "/successfulResults", headers);
        doRequestWithCsvResponse(callback, request);
    }

    @Override
    public void getFailedResults(String jobId, Map<String, List<String>> headers, StreamResponseCallback callback) {
        final Request request = getRequest(HttpMethod.GET, jobUrl(jobId) + "/failedResults", headers);
        doRequestWithCsvResponse(callback, request);
    }

    @Override
    public void getUnprocessedRecords(
            String jobId, Map<String, List<String>> headers, StreamResponseCallback callback) {
        final Request request = getRequest(HttpMethod.GET, jobUrl(jobId) + "/unprocessedrecords", headers);
        doRequestWithCsvResponse(callback, request);
    }

    @Override
    public void getAllJobs(String queryLocator, Map<String, List<String>> headers, JobsResponseCallback callback) {
        String url = jobUrl(null);
        if (queryLocator != null) {
            url = url + "?queryLocator=" + queryLocator;
        }
        final Request request = getRequest(HttpMethod.GET, url, headers);
        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> responseHeaders, SalesforceException ex) {
                if (ex != null) {
                    callback.onResponse(null, responseHeaders, ex);
                }
                Jobs responseJobs = null;
                try {
                    responseJobs = DefaultBulkApiV2Client.this.unmarshalResponse(response, request, Jobs.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(responseJobs, responseHeaders, ex);
            }
        });
    }

    @Override
    public void createQueryJob(
            QueryJob queryJob, Map<String, List<String>> headers, QueryJobResponseCallback callback) {
        final Request request = getRequest(HttpMethod.POST, queryJobUrl(null), headers);
        try {
            marshalRequest(queryJob, request);
        } catch (SalesforceException e) {
            callback.onResponse(null, Collections.emptyMap(), e);
            return;
        }
        doHttpRequestWithQueryJobResponse(callback, request);
    }

    @Override
    public void getQueryJob(String jobId, Map<String, List<String>> headers, QueryJobResponseCallback callback) {
        final Request request = getRequest(HttpMethod.GET, queryJobUrl(jobId), headers);
        doHttpRequestWithQueryJobResponse(callback, request);
    }

    @Override
    public void getQueryJobResults(
            String jobId, String locator, Integer maxRecords, Map<String, List<String>> headers,
            StreamResponseCallback callback) {
        String query = null;
        if (locator != null) {
            query = "locator=" + locator;
        }
        if (maxRecords != null) {
            query = (query != null ? query + "&" : "") + "maxRecords=" + maxRecords;
        }
        String url = queryJobUrl(jobId) + "/results";
        if (query != null) {
            url = url + "?" + query;
        }
        final Request request = getRequest(HttpMethod.GET, url, headers);
        doRequestWithCsvResponse(callback, request);
    }

    @Override
    public void changeQueryJobState(
            String jobId, JobStateEnum state, Map<String, List<String>> headers, QueryJobResponseCallback callback) {
        final Request request = getRequest(HttpMethod.PATCH, queryJobUrl(jobId), headers);
        QueryJob job = new QueryJob();
        job.setId(jobId);
        job.setState(state);
        try {
            marshalRequest(job, request);
        } catch (SalesforceException e) {
            callback.onResponse(null, Collections.emptyMap(), e);
            return;
        }
        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                if (ex != null) {
                    callback.onResponse(null, headers, ex);
                }
                QueryJob responseJob = null;
                try {
                    responseJob = unmarshalResponse(response, request, QueryJob.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(responseJob, headers, ex);
            }
        });
    }

    @Override
    public void deleteQueryJob(String jobId, Map<String, List<String>> headers, ResponseCallback callback) {
        final Request request = getRequest(HttpMethod.DELETE, queryJobUrl(jobId), headers);
        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                callback.onResponse(headers, ex);
            }
        });
    }

    @Override
    public void getAllQueryJobs(
            String queryLocator, Map<String, List<String>> headers, QueryJobsResponseCallback callback) {
        String url = queryJobUrl(null);
        if (queryLocator != null) {
            url = url + "?queryLocator=" + queryLocator;
        }
        final Request request = getRequest(HttpMethod.GET, url, headers);
        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> responseHeaders, SalesforceException ex) {
                if (ex != null) {
                    callback.onResponse(null, responseHeaders, ex);
                }
                QueryJobs responseJobs = null;
                try {
                    responseJobs = unmarshalResponse(response, request, QueryJobs.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(responseJobs, responseHeaders, ex);
            }
        });
    }

    @Override
    protected void doHttpRequest(Request request, ClientResponseCallback callback) {
        // set access token for all requests
        setAccessToken(request);
        if (!request.getHeaders().contains(HttpHeader.CONTENT_TYPE)) {
            request.headers(h -> h.add(HttpHeader.CONTENT_TYPE, "application/json"));
        }
        request.headers(h -> h.add(HttpHeader.ACCEPT_CHARSET, StandardCharsets.UTF_8.name()));
        request.headers(h -> h.add(HttpHeader.ACCEPT, "application/json"));
        super.doHttpRequest(request, callback);
    }

    @Override
    protected SalesforceException createRestException(Response response, InputStream responseContent) {
        // this must be of type Error
        try {
            final List<RestError> errors = unmarshalResponse(responseContent, response.getRequest(),
                    new TypeReference<List<RestError>>() {
                    });
            return new SalesforceException(errors, response.getStatus());
        } catch (SalesforceException e) {
            String msg = "Error un-marshaling Salesforce Error: " + e.getMessage();
            return new SalesforceException(msg, e);
        }
    }

    @Override
    protected void setAccessToken(Request request) {
        request.headers(h -> h.add(AUTHORIZATION_HEADER, BEARER_PREFIX + accessToken));
    }

    private String jobUrl(String jobId) {
        return super.instanceUrl + "/services/data/v" + version + "/jobs/ingest" +
               (jobId != null ? "/" + jobId : "");
    }

    private String queryJobUrl(String jobId) {
        return super.instanceUrl + "/services/data/v" + version + "/jobs/query" +
               (jobId != null ? "/" + jobId : "");
    }

    private void doRequestWithCsvResponse(StreamResponseCallback callback, Request request) {
        request.accept("text/csv");
        doHttpRequest(request, callback::onResponse);
    }

    private void doHttpRequestWithJobResponse(JobResponseCallback callback, Request request) {
        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> responseHeaders, SalesforceException ex) {
                if (ex != null) {
                    callback.onResponse(null, responseHeaders, ex);
                }
                Job responseJob = null;
                try {
                    responseJob = DefaultBulkApiV2Client.this.unmarshalResponse(response, request,
                            Job.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(responseJob, responseHeaders, ex);
            }
        });
    }

    private void doHttpRequestWithQueryJobResponse(QueryJobResponseCallback callback, Request request) {
        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> responseHeaders, SalesforceException ex) {
                if (ex != null) {
                    callback.onResponse(null, responseHeaders, ex);
                }
                QueryJob queryJob = null;
                try {
                    queryJob = DefaultBulkApiV2Client.this.unmarshalResponse(response, request,
                            QueryJob.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(queryJob, responseHeaders, ex);
            }
        });
    }

    private void marshalRequest(Object input, Request request) throws SalesforceException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            objectMapper.writeValue(outputStream, input);
        } catch (IOException e) {
            String message = "Error marshaling request: " + e.getMessage();
            throw new SalesforceException(message, e);
        }
        request.body(new BytesRequestContent(outputStream.toByteArray()));
    }

    private <T> T unmarshalResponse(InputStream response, Request request, Class<T> resultClass)
            throws SalesforceException {
        T result = null;
        if (response != null) {
            try {
                result = objectMapper.readValue(response, resultClass);
            } catch (IOException e) {
                throw new SalesforceException(
                        String.format("Error unmarshalling response for {%s:%s} : %s",
                                request.getMethod(), request.getURI(), e.getMessage()),
                        e);
            }
        }
        return result;
    }

    private <T> T unmarshalResponse(InputStream response, Request request, TypeReference<T> typeRef)
            throws SalesforceException {
        T result = null;
        if (response != null) {
            try {
                result = objectMapper.readValue(response, typeRef);
            } catch (IOException e) {
                throw new SalesforceException(
                        String.format("Error unmarshalling response for {%s:%s} : %s",
                                request.getMethod(), request.getURI(), e.getMessage()),
                        e);
            }
        }
        return result;
    }
}
