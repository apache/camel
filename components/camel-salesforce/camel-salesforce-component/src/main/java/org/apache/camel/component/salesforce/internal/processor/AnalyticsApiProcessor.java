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

import java.util.List;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.salesforce.SalesforceEndpoint;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.AbstractReportResultsBase;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.RecentReport;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportDescription;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportInstance;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportMetadata;
import org.apache.camel.component.salesforce.internal.client.AnalyticsApiClient;
import org.apache.camel.component.salesforce.internal.client.DefaultAnalyticsApiClient;
import org.apache.camel.support.service.ServiceHelper;

import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.INCLUDE_DETAILS;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.INSTANCE_ID;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.REPORT_ID;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.REPORT_METADATA;

/**
 * Exchange processor for Analytics API.
 */
public class AnalyticsApiProcessor extends AbstractSalesforceProcessor {

    private AnalyticsApiClient analyticsClient;

    public AnalyticsApiProcessor(SalesforceEndpoint endpoint) throws SalesforceException {
        super(endpoint);

        this.analyticsClient = new DefaultAnalyticsApiClient((String)endpointConfigMap.get(SalesforceEndpointConfig.API_VERSION), session, httpClient);
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        boolean done = false;

        try {
            switch (operationName) {
                case GET_RECENT_REPORTS:
                    processGetRecentReports(exchange, callback);
                    break;
                case GET_REPORT_DESCRIPTION:
                    processGetReportDescription(exchange, callback);
                    break;
                case EXECUTE_SYNCREPORT:
                    processExecuteSyncReport(exchange, callback);
                    break;
                case EXECUTE_ASYNCREPORT:
                    processExecuteAsyncReport(exchange, callback);
                    break;
                case GET_REPORT_INSTANCES:
                    processGetReportInstances(exchange, callback);
                    break;
                case GET_REPORT_RESULTS:
                    processGetReportResults(exchange, callback);
                    break;
                default:
                    throw new SalesforceException("Unknown operation name: " + operationName.value(), null);
            }
        } catch (SalesforceException e) {
            exchange.setException(new SalesforceException(String.format("Error processing %s: [%s] \"%s\"", operationName.value(), e.getStatusCode(), e.getMessage()), e));
            callback.done(true);
            done = true;
        } catch (RuntimeException e) {
            exchange.setException(new SalesforceException(String.format("Unexpected Error processing %s: \"%s\"", operationName.value(), e.getMessage()), e));
            callback.done(true);
            done = true;
        }

        // continue routing asynchronously if false
        return done;
    }

    private void processGetRecentReports(final Exchange exchange, final AsyncCallback callback) {

        analyticsClient.getRecentReports(determineHeaders(exchange), new AnalyticsApiClient.RecentReportsResponseCallback() {
            @Override
            public void onResponse(List<RecentReport> reportDescription, Map<String, String> headers, SalesforceException ex) {
                processResponse(exchange, reportDescription, headers, ex, callback);
            }
        });
    }

    private void processGetReportDescription(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {

        final String reportId = getParameter(REPORT_ID, exchange, USE_BODY, NOT_OPTIONAL);

        analyticsClient.getReportDescription(reportId, determineHeaders(exchange), new AnalyticsApiClient.ReportDescriptionResponseCallback() {
            @Override
            public void onResponse(ReportDescription reportDescription, Map<String, String> headers, SalesforceException ex) {
                processResponse(exchange, reportDescription, headers, ex, callback);
            }
        });
    }

    private void processExecuteSyncReport(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {

        String reportId;
        final Boolean includeDetails = getParameter(INCLUDE_DETAILS, exchange, IGNORE_BODY, IS_OPTIONAL, Boolean.class);

        // try getting report metadata from body first
        ReportMetadata reportMetadata = exchange.getIn().getBody(ReportMetadata.class);
        if (reportMetadata != null) {
            reportId = reportMetadata.getId();
            if (reportId == null) {
                reportId = getParameter(REPORT_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
            }
        } else {
            reportId = getParameter(REPORT_ID, exchange, USE_BODY, NOT_OPTIONAL);
            reportMetadata = getParameter(REPORT_METADATA, exchange, IGNORE_BODY, IS_OPTIONAL, ReportMetadata.class);
        }

        analyticsClient.executeSyncReport(reportId, includeDetails, reportMetadata, determineHeaders(exchange), new AnalyticsApiClient.ReportResultsResponseCallback() {
            @Override
            public void onResponse(AbstractReportResultsBase reportResults, Map<String, String> headers, SalesforceException ex) {
                processResponse(exchange, reportResults, headers, ex, callback);
            }
        });
    }

    private void processExecuteAsyncReport(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {

        String reportId;
        final Boolean includeDetails = getParameter(INCLUDE_DETAILS, exchange, IGNORE_BODY, IS_OPTIONAL, Boolean.class);

        // try getting report metadata from body first
        ReportMetadata reportMetadata = exchange.getIn().getBody(ReportMetadata.class);
        if (reportMetadata != null) {
            reportId = reportMetadata.getId();
            if (reportId == null) {
                reportId = getParameter(REPORT_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
            }
        } else {
            reportId = getParameter(REPORT_ID, exchange, USE_BODY, NOT_OPTIONAL);
            reportMetadata = getParameter(REPORT_METADATA, exchange, IGNORE_BODY, IS_OPTIONAL, ReportMetadata.class);
        }

        analyticsClient.executeAsyncReport(reportId, includeDetails, reportMetadata, determineHeaders(exchange), new AnalyticsApiClient.ReportInstanceResponseCallback() {
            @Override
            public void onResponse(ReportInstance reportInstance, Map<String, String> headers, SalesforceException ex) {
                processResponse(exchange, reportInstance, headers, ex, callback);
            }
        });
    }

    private void processGetReportInstances(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {

        final String reportId = getParameter(REPORT_ID, exchange, USE_BODY, NOT_OPTIONAL);

        analyticsClient.getReportInstances(reportId, determineHeaders(exchange), new AnalyticsApiClient.ReportInstanceListResponseCallback() {
            @Override
            public void onResponse(List<ReportInstance> reportInstances, Map<String, String> headers, SalesforceException ex) {
                processResponse(exchange, reportInstances, headers, ex, callback);
            }
        });
    }

    private void processGetReportResults(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {

        final String reportId = getParameter(REPORT_ID, exchange, USE_BODY, NOT_OPTIONAL);
        final String instanceId = getParameter(INSTANCE_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);

        analyticsClient.getReportResults(reportId, instanceId, determineHeaders(exchange), new AnalyticsApiClient.ReportResultsResponseCallback() {
            @Override
            public void onResponse(AbstractReportResultsBase reportResults, Map<String, String> headers, SalesforceException ex) {
                processResponse(exchange, reportResults, headers, ex, callback);
            }
        });
    }

    private void processResponse(Exchange exchange, Object body, Map<String, String> headers, SalesforceException ex, AsyncCallback callback) {
        final Message out = exchange.getOut();
        if (ex != null) {
            exchange.setException(ex);
        } else {
            out.setBody(body);
        }

        // copy headers
        final Message inboundMessage = exchange.getIn();
        final Map<String, Object> outputHeaders = out.getHeaders();
        outputHeaders.putAll(inboundMessage.getHeaders());
        outputHeaders.putAll(headers);

        // signal exchange completion
        callback.done(false);
    }

    @Override
    public void start() {
        ServiceHelper.startService(analyticsClient);
    }

    @Override
    public void stop() {
        // stop the client
        ServiceHelper.stopService(analyticsClient);
    }
}
