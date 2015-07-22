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

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;

import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.RestError;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.AsyncReportResults;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.RecentReport;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportDescription;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportInstance;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportMetadata;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.SyncReportResults;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.http.HttpMethods;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.eclipse.jetty.util.StringUtil;

/**
 * Default implementation of {@link org.apache.camel.component.salesforce.internal.client.AnalyticsApiClient}.
 */
public class DefaultAnalyticsApiClient extends AbstractClientBase implements AnalyticsApiClient {

    private static final String TOKEN_PREFIX = "Bearer ";
    private static final String INCLUDE_DETAILS_QUERY_PARAM = "?includeDetails=";
    private ObjectMapper objectMapper;


    public DefaultAnalyticsApiClient(String version, SalesforceSession session, HttpClient httpClient) throws SalesforceException {
        super(version, session, httpClient);

        objectMapper = new ObjectMapper();
    }

    @Override
    public void getRecentReports(final RecentReportsResponseCallback callback) {

        final ContentExchange contentExchange = getContentExchange(HttpMethods.GET, reportsUrl());

        doHttpRequest(contentExchange, new ClientResponseCallback() {
            @Override
            @SuppressWarnings("unchecked")
            public void onResponse(InputStream response, SalesforceException ex) {
                List<RecentReport> recentReports = null;
                if (response != null) {
                    try {
                        recentReports = unmarshalResponse(response, contentExchange,
                            new TypeReference<List<RecentReport>>() {
                            }
                        );
                    } catch (SalesforceException e) {
                        ex = e;
                    }
                }
                callback.onResponse(recentReports, ex);
            }
        });
    }

    @Override
    public void getReportDescription(String reportId, final ReportDescriptionResponseCallback callback) {

        final ContentExchange contentExchange = getContentExchange(HttpMethods.GET, reportsDescribeUrl(reportId));

        doHttpRequest(contentExchange, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, SalesforceException ex) {
                ReportDescription reportDescription = null;
                try {
                    reportDescription = unmarshalResponse(response, contentExchange, ReportDescription.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(reportDescription, ex);
            }
        });
    }

    @Override
    public void executeSyncReport(String reportId, Boolean includeDetails, ReportMetadata reportMetadata,
                                  final ReportResultsResponseCallback callback) {

        final boolean useGet = reportMetadata == null;
        final ContentExchange contentExchange = getContentExchange(
            useGet ? HttpMethods.GET : HttpMethods.POST, reportsUrl(reportId, includeDetails));

        // set POST data
        if (!useGet) {
            try {
                // wrap reportMetadata in a map
                final HashMap<String, Object> request = new HashMap<String, Object>();
                request.put("reportMetadata", reportMetadata);
                marshalRequest(request, contentExchange);
            } catch (SalesforceException e) {
                callback.onResponse(null, e);
                return;
            }
        }

        doHttpRequest(contentExchange, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, SalesforceException ex) {
                SyncReportResults reportResults = null;
                try {
                    reportResults = unmarshalResponse(response, contentExchange, SyncReportResults.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(reportResults, ex);
            }
        });
    }

    @Override
    public void executeAsyncReport(String reportId, Boolean includeDetails, ReportMetadata reportMetadata,
                                   final ReportInstanceResponseCallback callback) {

        final ContentExchange contentExchange = getContentExchange(HttpMethods.POST,
            reportInstancesUrl(reportId, includeDetails));

        // set POST data
        if (reportMetadata != null) {
            try {
                // wrap reportMetadata in a map
                final HashMap<String, Object> request = new HashMap<String, Object>();
                request.put("reportMetadata", reportMetadata);
                marshalRequest(request, contentExchange);
            } catch (SalesforceException e) {
                callback.onResponse(null, e);
                return;
            }
        }

        doHttpRequest(contentExchange, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, SalesforceException ex) {
                ReportInstance reportInstance = null;
                try {
                    reportInstance = unmarshalResponse(response, contentExchange, ReportInstance.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(reportInstance, ex);
            }
        });
    }

    @Override
    public void getReportInstances(String reportId, final ReportInstanceListResponseCallback callback) {

        final ContentExchange contentExchange = getContentExchange(HttpMethods.GET, reportInstancesUrl(reportId));

        doHttpRequest(contentExchange, new ClientResponseCallback() {
            @Override
            @SuppressWarnings("unchecked")
            public void onResponse(InputStream response, SalesforceException ex) {
                List<ReportInstance> reportInstances = null;
                if (response != null) {
                    try {
                        reportInstances = unmarshalResponse(response, contentExchange,
                            new TypeReference<List<ReportInstance>>() {
                            }
                        );
                    } catch (SalesforceException e) {
                        ex = e;
                    }
                }
                callback.onResponse(reportInstances, ex);
            }
        });
    }

    @Override
    public void getReportResults(String reportId, String instanceId, final ReportResultsResponseCallback callback) {

        final ContentExchange contentExchange = getContentExchange(HttpMethods.GET,
            reportInstancesUrl(reportId, instanceId));

        doHttpRequest(contentExchange, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, SalesforceException ex) {
                AsyncReportResults reportResults = null;
                try {
                    reportResults = unmarshalResponse(response, contentExchange, AsyncReportResults.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(reportResults, ex);
            }
        });
    }

    private String reportsUrl() {
        // NOTE the prefix 'v' for the version number
        return instanceUrl + "/services/data/v" + version + "/analytics/reports";
    }

    private String reportsDescribeUrl(String reportId) {
        return reportsUrl(reportId) + "/describe";
    }

    private String reportsUrl(String reportId) {
        return reportsUrl() +  "/" + reportId;
    }

    private String reportsUrl(String reportId, Boolean includeDetails) {
        return includeDetails == null ? reportsUrl(reportId)
                : reportsUrl(reportId) + INCLUDE_DETAILS_QUERY_PARAM + includeDetails;
    }

    private String reportInstancesUrl(String reportId) {
        return reportsUrl(reportId) + "/instances";
    }

    private String reportInstancesUrl(String reportId, Boolean includeDetails) {
        return includeDetails == null ? reportInstancesUrl(reportId)
            : reportInstancesUrl(reportId) + INCLUDE_DETAILS_QUERY_PARAM + includeDetails;
    }

    private String reportInstancesUrl(String reportId, String instanceId) {
        return reportInstancesUrl(reportId) + "/" + instanceId;
    }

    @Override
    protected void setAccessToken(HttpExchange httpExchange) {
        httpExchange.setRequestHeader(HttpHeaders.AUTHORIZATION, TOKEN_PREFIX + accessToken);
    }

    @Override
    protected SalesforceException createRestException(ContentExchange httpExchange, String reason) {
        final int statusCode = httpExchange.getResponseStatus();
        String responseContent = null;
        try {
            responseContent = httpExchange.getResponseContent();
            if (responseContent != null) {
                // unmarshal RestError
                final List<RestError> errors = objectMapper.readValue(responseContent,
                    new TypeReference<List<RestError>>() {
                    });
                return new SalesforceException(errors, statusCode);
            }
        } catch (UnsupportedEncodingException e) {
            // log and ignore
            String msg = "Unexpected Error parsing JSON error response body + ["
                + responseContent + "] : " + e.getMessage();
            log.warn(msg, e);
        } catch (IOException e) {
            // log and ignore
            String msg = "Unexpected Error parsing JSON error response body + ["
                + responseContent + "] : " + e.getMessage();
            log.warn(msg, e);
        }

        // just report HTTP status info
        return new SalesforceException("Unexpected error: " + reason + ", with content: " + responseContent,
            statusCode);
    }

    @Override
    protected void doHttpRequest(ContentExchange request, ClientResponseCallback callback) {

        // set access token for all requests
        setAccessToken(request);

        // set request and response content type and charset, which is always JSON for analytics API
        request.setRequestHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_UTF8);
        request.setRequestHeader(HttpHeaders.ACCEPT, APPLICATION_JSON_UTF8);
        request.setRequestHeader(HttpHeaders.ACCEPT_CHARSET, StringUtil.__UTF8);

        super.doHttpRequest(request, callback);
    }

    private void marshalRequest(Object input, ContentExchange request) throws SalesforceException {
        try {
            request.setRequestContent(new ByteArrayBuffer(objectMapper.writeValueAsBytes(input)));
        } catch (IOException e) {
            throw new SalesforceException(
                String.format("Error marshaling request for {%s:%s} : %s",
                    request.getMethod(), request.getRequestURI(), e.getMessage()),
                e);
        }
    }

    private <T> T unmarshalResponse(InputStream response, ContentExchange request,
                                      TypeReference<T> responseTypeReference)
        throws SalesforceException {

        try {
            return objectMapper.readValue(response, responseTypeReference);
        } catch (IOException e) {
            throw new SalesforceException(
                String.format("Error unmarshaling response {%s:%s} : %s",
                    request.getMethod(), request.getRequestURI(), e.getMessage()),
                e);
        }
    }

    private <T> T unmarshalResponse(InputStream response, ContentExchange request, Class<T> responseClass)
        throws SalesforceException {

        if (response == null) {
            return null;
        }

        try {
            return objectMapper.readValue(response, responseClass);
        } catch (IOException e) {
            throw new SalesforceException(
                String.format("Error unmarshaling response {%s:%s} : %s",
                    request.getMethod(), request.getRequestURI(), e.getMessage()),
                e);
        }
    }
}
