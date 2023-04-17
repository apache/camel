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

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.component.salesforce.SalesforceLoginConfig;
import org.apache.camel.component.salesforce.api.NoSuchSObjectException;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.TypeReferences;
import org.apache.camel.component.salesforce.api.dto.RestError;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.AsyncReportResults;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.RecentReport;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportDescription;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportInstance;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportMetadata;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.SyncReportResults;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.StringUtil;

/**
 * Default implementation of {@link org.apache.camel.component.salesforce.internal.client.AnalyticsApiClient}.
 */
public class DefaultAnalyticsApiClient extends AbstractClientBase implements AnalyticsApiClient {

    private static final String TOKEN_PREFIX = "Bearer ";
    private static final String INCLUDE_DETAILS_QUERY_PARAM = "?includeDetails=";
    private ObjectMapper objectMapper;

    public DefaultAnalyticsApiClient(String version, SalesforceSession session, SalesforceHttpClient httpClient,
                                     SalesforceLoginConfig loginConfig) throws SalesforceException {
        super(version, session, httpClient, loginConfig);
        objectMapper = JsonUtils.createObjectMapper();
    }

    @Override
    public void getRecentReports(final Map<String, List<String>> headers, final RecentReportsResponseCallback callback) {
        final Request request = getRequest(HttpMethod.GET, reportsUrl(), headers);

        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                List<RecentReport> recentReports = null;
                if (response != null) {
                    try {
                        recentReports = unmarshalResponse(response, request, TypeReferences.RECENT_REPORT_LIST_TYPE);
                    } catch (SalesforceException e) {
                        ex = e;
                    }
                }
                callback.onResponse(recentReports, headers, ex);
            }
        });
    }

    @Override
    public void getReportDescription(
            String reportId, final Map<String, List<String>> headers, final ReportDescriptionResponseCallback callback) {

        final Request request = getRequest(HttpMethod.GET, reportsDescribeUrl(reportId), headers);

        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                ReportDescription reportDescription = null;
                try {
                    reportDescription = unmarshalResponse(response, request, ReportDescription.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(reportDescription, headers, ex);
            }
        });
    }

    @Override
    public void executeSyncReport(
            String reportId, Boolean includeDetails, ReportMetadata reportMetadata, final Map<String, List<String>> headers,
            final ReportResultsResponseCallback callback) {

        final boolean useGet = reportMetadata == null;
        final Request request
                = getRequest(useGet ? HttpMethod.GET : HttpMethod.POST, reportsUrl(reportId, includeDetails), headers);

        // set POST data
        if (!useGet) {
            try {
                // wrap reportMetadata in a map
                final HashMap<String, Object> input = new HashMap<>();
                input.put("reportMetadata", reportMetadata);
                marshalRequest(input, request);
            } catch (SalesforceException e) {
                callback.onResponse(null, Collections.emptyMap(), e);
                return;
            }
        }

        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                SyncReportResults reportResults = null;
                try {
                    reportResults = unmarshalResponse(response, request, SyncReportResults.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(reportResults, headers, ex);
            }
        });
    }

    @Override
    public void executeAsyncReport(
            String reportId, Boolean includeDetails, ReportMetadata reportMetadata, final Map<String, List<String>> headers,
            final ReportInstanceResponseCallback callback) {

        final Request request = getRequest(HttpMethod.POST, reportInstancesUrl(reportId, includeDetails), headers);

        // set POST data
        if (reportMetadata != null) {
            try {
                // wrap reportMetadata in a map
                final HashMap<String, Object> input = new HashMap<>();
                input.put("reportMetadata", reportMetadata);
                marshalRequest(input, request);
            } catch (SalesforceException e) {
                callback.onResponse(null, Collections.emptyMap(), e);
                return;
            }
        }

        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                ReportInstance reportInstance = null;
                try {
                    reportInstance = unmarshalResponse(response, request, ReportInstance.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(reportInstance, headers, ex);
            }
        });
    }

    @Override
    public void getReportInstances(
            String reportId, final Map<String, List<String>> headers, final ReportInstanceListResponseCallback callback) {

        final Request request = getRequest(HttpMethod.GET, reportInstancesUrl(reportId), headers);

        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                List<ReportInstance> reportInstances = null;
                if (response != null) {
                    try {
                        reportInstances = unmarshalResponse(response, request, TypeReferences.REPORT_INSTANCE_LIST_TYPE);
                    } catch (SalesforceException e) {
                        ex = e;
                    }
                }
                callback.onResponse(reportInstances, headers, ex);
            }
        });
    }

    @Override
    public void getReportResults(
            String reportId, String instanceId, final Map<String, List<String>> headers,
            final ReportResultsResponseCallback callback) {

        final Request request = getRequest(HttpMethod.GET, reportInstancesUrl(reportId, instanceId), headers);

        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                AsyncReportResults reportResults = null;
                try {
                    reportResults = unmarshalResponse(response, request, AsyncReportResults.class);
                } catch (SalesforceException e) {
                    ex = e;
                }
                callback.onResponse(reportResults, headers, ex);
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
        return reportsUrl() + "/" + reportId;
    }

    private String reportsUrl(String reportId, Boolean includeDetails) {
        return includeDetails == null
                ? reportsUrl(reportId) : reportsUrl(reportId) + INCLUDE_DETAILS_QUERY_PARAM + includeDetails;
    }

    private String reportInstancesUrl(String reportId) {
        return reportsUrl(reportId) + "/instances";
    }

    private String reportInstancesUrl(String reportId, Boolean includeDetails) {
        return includeDetails == null
                ? reportInstancesUrl(reportId) : reportInstancesUrl(reportId) + INCLUDE_DETAILS_QUERY_PARAM + includeDetails;
    }

    private String reportInstancesUrl(String reportId, String instanceId) {
        return reportInstancesUrl(reportId) + "/" + instanceId;
    }

    @Override
    protected void setAccessToken(Request request) {
        // replace old token
        request.header(HttpHeader.AUTHORIZATION, TOKEN_PREFIX + accessToken);
    }

    @Override
    protected SalesforceException createRestException(Response response, InputStream responseContent) {
        final int statusCode = response.getStatus();
        try {
            if (responseContent != null) {
                // unmarshal RestError
                final List<RestError> errors = readErrorsFrom(responseContent, objectMapper);

                if (statusCode == HttpStatus.NOT_FOUND_404) {
                    return new NoSuchSObjectException(errors);
                }

                return new SalesforceException(errors, statusCode);
            }
        } catch (IOException e) {
            // log and ignore
            String msg = "Unexpected Error parsing JSON error response body + [" + responseContent + "] : " + e.getMessage();
            log.warn(msg, e);
        }

        // just report HTTP status info
        String message = String.format("Unexpected error: %s, with content: %s", response.getReason(), responseContent);
        return new SalesforceException(message, statusCode);
    }

    @Override
    protected void doHttpRequest(Request request, ClientResponseCallback callback) {

        // set access token for all requests
        setAccessToken(request);

        // set request and response content type and charset, which is always
        // JSON for analytics API
        request.header(HttpHeader.CONTENT_TYPE, APPLICATION_JSON_UTF8);
        request.header(HttpHeader.ACCEPT, APPLICATION_JSON_UTF8);
        request.header(HttpHeader.ACCEPT_CHARSET, StringUtil.__UTF8);

        super.doHttpRequest(request, callback);
    }

    private void marshalRequest(Object input, Request request) throws SalesforceException {
        try {
            request.content(new BytesContentProvider(objectMapper.writeValueAsBytes(input)));
        } catch (Exception e) {
            throw new SalesforceException(
                    String.format("Error marshaling request for {%s:%s} : %s", request.getMethod(), request.getURI(),
                            e.getMessage()),
                    e);
        }
    }

    private <T> T unmarshalResponse(InputStream response, Request request, TypeReference<T> responseTypeReference)
            throws SalesforceException {

        try {
            return objectMapper.readValue(response, responseTypeReference);
        } catch (Exception e) {
            throw new SalesforceException(
                    String.format("Error unmarshaling response {%s:%s} : %s", request.getMethod(), request.getURI(),
                            e.getMessage()),
                    e);
        }
    }

    private <T> T unmarshalResponse(InputStream response, Request request, Class<T> responseClass) throws SalesforceException {

        if (response == null) {
            return null;
        }

        try {
            return objectMapper.readValue(response, responseClass);
        } catch (Exception e) {
            throw new SalesforceException(
                    String.format("Error unmarshaling response {%s:%s} : %s", request.getMethod(), request.getURI(),
                            e.getMessage()),
                    e);
        }
    }
}
