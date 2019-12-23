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
package org.apache.camel.component.salesforce;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportMetadata;
import org.apache.camel.component.salesforce.api.dto.approval.ApprovalRequest;
import org.apache.camel.component.salesforce.api.dto.approval.ApprovalRequest.Action;
import org.apache.camel.component.salesforce.api.dto.bulk.ContentType;
import org.apache.camel.component.salesforce.internal.PayloadFormat;
import org.apache.camel.component.salesforce.internal.dto.NotifyForFieldsEnum;
import org.apache.camel.component.salesforce.internal.dto.NotifyForOperationsEnum;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Salesforce Endpoint configuration.
 */
@UriParams
public class SalesforceEndpointConfig implements Cloneable {

    // default API version
    public static final String DEFAULT_VERSION = "34.0";

    // general parameter
    public static final String API_VERSION = "apiVersion";

    // parameters for Rest API
    public static final String FORMAT = "format";
    public static final String RAW_PAYLOAD = "rawPayload";

    public static final String SOBJECT_NAME = "sObjectName";
    public static final String SOBJECT_ID = "sObjectId";
    public static final String SOBJECT_FIELDS = "sObjectFields";
    public static final String SOBJECT_EXT_ID_NAME = "sObjectIdName";
    public static final String SOBJECT_EXT_ID_VALUE = "sObjectIdValue";
    public static final String SOBJECT_BLOB_FIELD_NAME = "sObjectBlobFieldName";
    public static final String SOBJECT_CLASS = "sObjectClass";
    public static final String SOBJECT_QUERY = "sObjectQuery";
    public static final String SOBJECT_SEARCH = "sObjectSearch";
    public static final String APEX_METHOD = "apexMethod";
    public static final String APEX_URL = "apexUrl";
    public static final String LIMIT = "limit";

    // prefix for parameters in headers
    public static final String APEX_QUERY_PARAM_PREFIX = "apexQueryParam.";

    // parameters for Bulk API
    public static final String CONTENT_TYPE = "contentType";
    public static final String JOB_ID = "jobId";
    public static final String BATCH_ID = "batchId";
    public static final String RESULT_ID = "resultId";

    // parameters for Analytics API
    public static final String REPORT_ID = "reportId";
    public static final String INCLUDE_DETAILS = "includeDetails";
    public static final String REPORT_METADATA = "reportMetadata";
    public static final String INSTANCE_ID = "instanceId";

    // parameters for Streaming API
    public static final String DEFAULT_REPLAY_ID = "defaultReplayId";
    public static final String INITIAL_REPLAY_ID_MAP = "initialReplayIdMap";

    // parameters for Approval API
    public static final String APPROVAL = "approval";

    // default maximum authentication retries on failed authentication or
    // expired session
    public static final int DEFAULT_MAX_AUTHENTICATION_RETRIES = 4;

    // default increment and limit for Streaming connection restart attempts
    public static final long DEFAULT_BACKOFF_INCREMENT = 1000L;
    public static final long DEFAULT_MAX_BACKOFF = 30000L;

    public static final String NOT_FOUND_BEHAVIOUR = "notFoundBehaviour";

    // general properties
    @UriParam
    private String apiVersion = DEFAULT_VERSION;

    // Rest API properties
    @UriParam
    private PayloadFormat format = PayloadFormat.JSON;
    @UriParam
    private boolean rawPayload;
    @UriParam(displayName = "SObject Name")
    private String sObjectName;
    @UriParam(displayName = "SObject Id")
    private String sObjectId;
    @UriParam(displayName = "SObject Fields")
    private String sObjectFields;
    @UriParam(displayName = "SObject Id Name")
    private String sObjectIdName;
    @UriParam(displayName = "SObject Id Value")
    private String sObjectIdValue;
    @UriParam(displayName = "SObject Blob Field Name")
    private String sObjectBlobFieldName;
    @UriParam(displayName = "SObject Class")
    private String sObjectClass;
    @UriParam(displayName = "SObject Query")
    private String sObjectQuery;
    @UriParam(displayName = "SObject Search")
    private String sObjectSearch;
    @UriParam
    private String apexMethod;
    @UriParam
    private String apexUrl;
    @UriParam
    private Map<String, Object> apexQueryParams;

    // Bulk API properties
    @UriParam
    private ContentType contentType;
    @UriParam
    private String jobId;
    @UriParam
    private String batchId;
    @UriParam
    private String resultId;

    // Streaming API properties
    @UriParam
    private boolean updateTopic;
    @UriParam
    private NotifyForFieldsEnum notifyForFields;
    @UriParam
    private NotifyForOperationsEnum notifyForOperations;
    @UriParam
    private Boolean notifyForOperationCreate;
    @UriParam
    private Boolean notifyForOperationUpdate;
    @UriParam
    private Boolean notifyForOperationDelete;
    @UriParam
    private Boolean notifyForOperationUndelete;

    // Analytics API properties
    @UriParam
    private String reportId;
    @UriParam
    private Boolean includeDetails;
    @UriParam
    private ReportMetadata reportMetadata;
    @UriParam
    private String instanceId;

    // Streaming API properties
    @UriParam
    private Long defaultReplayId;
    @UriParam
    private Map<String, Long> initialReplayIdMap;

    // Approval API properties
    private ApprovalRequest approval;

    // Salesforce Jetty9 HttpClient, set using reference
    @UriParam
    private SalesforceHttpClient httpClient;

    // To allow custom ObjectMapper (for registering extra datatype modules)
    @UriParam
    private ObjectMapper objectMapper;

    // Streaming connection restart attempt backoff interval increment
    @UriParam
    private long backoffIncrement = DEFAULT_BACKOFF_INCREMENT;

    // Streaming connection restart attempt maximum backoff interval
    @UriParam
    private long maxBackoff = DEFAULT_MAX_BACKOFF;

    @UriParam
    private Integer limit;

    @UriParam
    private NotFoundBehaviour notFoundBehaviour = NotFoundBehaviour.EXCEPTION;

    public SalesforceEndpointConfig copy() {
        try {
            final SalesforceEndpointConfig copy = (SalesforceEndpointConfig)super.clone();
            // nothing to deep copy, getApexQueryParams() is readonly, so no
            // need to deep copy
            return copy;
        } catch (CloneNotSupportedException ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    public PayloadFormat getFormat() {
        return format;
    }

    /**
     * Payload format to use for Salesforce API calls, either JSON or XML,
     * defaults to JSON
     */
    public void setFormat(PayloadFormat format) {
        this.format = format;
    }

    public boolean isRawPayload() {
        return rawPayload;
    }

    /**
     * Use raw payload {@link String} for request and response (either JSON or
     * XML depending on {@code format}), instead of DTOs, false by default
     */
    public void setRawPayload(boolean rawPayload) {
        this.rawPayload = rawPayload;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    /**
     * Salesforce API version, defaults to
     * SalesforceEndpointConfig.DEFAULT_VERSION
     */
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getSObjectName() {
        return sObjectName;
    }

    /**
     * SObject name if required or supported by API
     */
    public void setSObjectName(String sObjectName) {
        this.sObjectName = sObjectName;
    }

    public String getSObjectId() {
        return sObjectId;
    }

    /**
     * SObject ID if required by API
     */
    public void setSObjectId(String sObjectId) {
        this.sObjectId = sObjectId;
    }

    public String getSObjectFields() {
        return sObjectFields;
    }

    /**
     * SObject fields to retrieve
     */
    public void setSObjectFields(String sObjectFields) {
        this.sObjectFields = sObjectFields;
    }

    public String getSObjectIdName() {
        return sObjectIdName;
    }

    /**
     * SObject external ID field name
     */
    public void setSObjectIdName(String sObjectIdName) {
        this.sObjectIdName = sObjectIdName;
    }

    public String getSObjectIdValue() {
        return sObjectIdValue;
    }

    /**
     * SObject external ID field value
     */
    public void setSObjectIdValue(String sObjectIdValue) {
        this.sObjectIdValue = sObjectIdValue;
    }

    public String getSObjectBlobFieldName() {
        return sObjectBlobFieldName;
    }

    /**
     * SObject blob field name
     */
    public void setSObjectBlobFieldName(String sObjectBlobFieldName) {
        this.sObjectBlobFieldName = sObjectBlobFieldName;
    }

    public String getSObjectClass() {
        return sObjectClass;
    }

    /**
     * Fully qualified SObject class name, usually generated using
     * camel-salesforce-maven-plugin
     */
    public void setSObjectClass(String sObjectClass) {
        this.sObjectClass = sObjectClass;
    }

    public String getSObjectQuery() {
        return sObjectQuery;
    }

    /**
     * Salesforce SOQL query string
     */
    public void setSObjectQuery(String sObjectQuery) {
        this.sObjectQuery = sObjectQuery;
    }

    public String getSObjectSearch() {
        return sObjectSearch;
    }

    /**
     * Salesforce SOSL search string
     */
    public void setSObjectSearch(String sObjectSearch) {
        this.sObjectSearch = sObjectSearch;
    }

    public String getApexMethod() {
        return apexMethod;
    }

    /**
     * APEX method name
     */
    public void setApexMethod(String apexMethod) {
        this.apexMethod = apexMethod;
    }

    public String getApexUrl() {
        return apexUrl;
    }

    /**
     * APEX method URL
     */
    public void setApexUrl(String apexUrl) {
        this.apexUrl = apexUrl;
    }

    public Map<String, Object> getApexQueryParams() {
        final Map<String, Object> value = Optional.ofNullable(apexQueryParams).orElse(Collections.emptyMap());

        return Collections.unmodifiableMap(value);
    }

    /**
     * Query params for APEX method
     */
    public void setApexQueryParams(Map<String, Object> apexQueryParams) {
        this.apexQueryParams = apexQueryParams;
    }

    public ApprovalRequest getApproval() {
        return approval;
    }

    /**
     * The approval request for Approval API.
     *
     * @param approval
     */
    public void setApproval(final ApprovalRequest approval) {
        this.approval = approval;
    }

    public ContentType getContentType() {
        return contentType;
    }

    /**
     * Bulk API content type, one of XML, CSV, ZIP_XML, ZIP_CSV
     */
    public void setContentType(ContentType contentType) {
        this.contentType = contentType;
    }

    public String getJobId() {
        return jobId;
    }

    /**
     * Bulk API Job ID
     */
    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getBatchId() {
        return batchId;
    }

    /**
     * Bulk API Batch ID
     */
    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getResultId() {
        return resultId;
    }

    /**
     * Bulk API Result ID
     */
    public void setResultId(String resultId) {
        this.resultId = resultId;
    }

    public boolean isUpdateTopic() {
        return updateTopic;
    }

    /**
     * Whether to update an existing Push Topic when using the Streaming API,
     * defaults to false
     */
    public void setUpdateTopic(boolean updateTopic) {
        this.updateTopic = updateTopic;
    }

    public NotifyForFieldsEnum getNotifyForFields() {
        return notifyForFields;
    }

    /**
     * Notify for fields, options are ALL, REFERENCED, SELECT, WHERE
     */
    public void setNotifyForFields(NotifyForFieldsEnum notifyForFields) {
        this.notifyForFields = notifyForFields;
    }

    public NotifyForOperationsEnum getNotifyForOperations() {
        return notifyForOperations;
    }

    /**
     * Notify for operations, options are ALL, CREATE, EXTENDED, UPDATE (API
     * version < 29.0)
     */
    public void setNotifyForOperations(NotifyForOperationsEnum notifyForOperations) {
        this.notifyForOperations = notifyForOperations;
    }

    public Boolean getNotifyForOperationCreate() {
        return notifyForOperationCreate;
    }

    /**
     * Notify for create operation, defaults to false (API version >= 29.0)
     */
    public void setNotifyForOperationCreate(Boolean notifyForOperationCreate) {
        this.notifyForOperationCreate = notifyForOperationCreate;
    }

    public Boolean getNotifyForOperationUpdate() {
        return notifyForOperationUpdate;
    }

    /**
     * Notify for update operation, defaults to false (API version >= 29.0)
     */
    public void setNotifyForOperationUpdate(Boolean notifyForOperationUpdate) {
        this.notifyForOperationUpdate = notifyForOperationUpdate;
    }

    public Boolean getNotifyForOperationDelete() {
        return notifyForOperationDelete;
    }

    /**
     * Notify for delete operation, defaults to false (API version >= 29.0)
     */
    public void setNotifyForOperationDelete(Boolean notifyForOperationDelete) {
        this.notifyForOperationDelete = notifyForOperationDelete;
    }

    public Boolean getNotifyForOperationUndelete() {
        return notifyForOperationUndelete;
    }

    /**
     * Notify for un-delete operation, defaults to false (API version >= 29.0)
     */
    public void setNotifyForOperationUndelete(Boolean notifyForOperationUndelete) {
        this.notifyForOperationUndelete = notifyForOperationUndelete;
    }

    public String getReportId() {
        return reportId;
    }

    /**
     * Salesforce1 Analytics report Id
     */
    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public Boolean getIncludeDetails() {
        return includeDetails;
    }

    /**
     * Include details in Salesforce1 Analytics report, defaults to false.
     */
    public void setIncludeDetails(Boolean includeDetails) {
        this.includeDetails = includeDetails;
    }

    public ReportMetadata getReportMetadata() {
        return reportMetadata;
    }

    /**
     * Salesforce1 Analytics report metadata for filtering
     */
    public void setReportMetadata(ReportMetadata reportMetadata) {
        this.reportMetadata = reportMetadata;
    }

    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Salesforce1 Analytics report execution instance ID
     */
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    /**
     * Custom Jetty Http Client to use to connect to Salesforce.
     */
    public void setHttpClient(SalesforceHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public SalesforceHttpClient getHttpClient() {
        return httpClient;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public long getBackoffIncrement() {
        return backoffIncrement;
    }

    /**
     * Backoff interval increment for Streaming connection restart attempts for
     * failures beyond CometD auto-reconnect.
     */
    public void setBackoffIncrement(long backoffIncrement) {
        this.backoffIncrement = backoffIncrement;
    }

    public long getMaxBackoff() {
        return maxBackoff;
    }

    /**
     * Maximum backoff interval for Streaming connection restart attempts for
     * failures beyond CometD auto-reconnect.
     */
    public void setMaxBackoff(long maxBackoff) {
        this.maxBackoff = maxBackoff;
    }

    /**
     * Custom Jackson ObjectMapper to use when serializing/deserializing
     * Salesforce objects.
     */
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> toValueMap() {

        final Map<String, Object> valueMap = new HashMap<>();
        valueMap.put(FORMAT, format.toString().toLowerCase());
        valueMap.put(API_VERSION, apiVersion);

        valueMap.put(SOBJECT_NAME, sObjectName);
        valueMap.put(SOBJECT_ID, sObjectId);
        valueMap.put(SOBJECT_FIELDS, sObjectFields);
        valueMap.put(SOBJECT_EXT_ID_NAME, sObjectIdName);
        valueMap.put(SOBJECT_BLOB_FIELD_NAME, sObjectBlobFieldName);
        valueMap.put(SOBJECT_EXT_ID_VALUE, sObjectIdValue);
        valueMap.put(SOBJECT_CLASS, sObjectClass);
        valueMap.put(SOBJECT_QUERY, sObjectQuery);
        valueMap.put(SOBJECT_SEARCH, sObjectSearch);
        valueMap.put(APEX_METHOD, apexMethod);
        valueMap.put(APEX_URL, apexUrl);
        valueMap.put(LIMIT, limit);
        valueMap.put(APPROVAL, approval);
        // apexQueryParams are handled explicitly in AbstractRestProcessor

        // add bulk API properties
        if (contentType != null) {
            valueMap.put(CONTENT_TYPE, contentType.value());
        }
        valueMap.put(JOB_ID, jobId);
        valueMap.put(BATCH_ID, batchId);
        valueMap.put(RESULT_ID, resultId);

        // add analytics API properties
        valueMap.put(REPORT_ID, reportId);
        valueMap.put(INCLUDE_DETAILS, includeDetails);
        valueMap.put(REPORT_METADATA, reportMetadata);
        valueMap.put(INSTANCE_ID, instanceId);

        // add streaming API properties
        valueMap.put(DEFAULT_REPLAY_ID, defaultReplayId);
        valueMap.put(INITIAL_REPLAY_ID_MAP, initialReplayIdMap);

        valueMap.put(NOT_FOUND_BEHAVIOUR, notFoundBehaviour);

        return Collections.unmodifiableMap(valueMap);
    }

    public Long getDefaultReplayId() {
        return defaultReplayId;
    }

    /**
     * Default replayId setting if no value is found in
     * {@link #initialReplayIdMap}
     * 
     * @param defaultReplayId
     */
    public void setDefaultReplayId(Long defaultReplayId) {
        this.defaultReplayId = defaultReplayId;
    }

    public Map<String, Long> getInitialReplayIdMap() {
        return Optional.ofNullable(initialReplayIdMap).orElse(Collections.emptyMap());
    }

    /**
     * Replay IDs to start from per channel name.
     */
    public void setInitialReplayIdMap(Map<String, Long> initialReplayIdMap) {
        this.initialReplayIdMap = initialReplayIdMap;
    }

    public Integer getLimit() {
        return limit;
    }

    /**
     * Limit on number of returned records. Applicable to some of the API, check
     * the Salesforce documentation.
     * 
     * @param limit
     */
    public void setLimit(final Integer limit) {
        this.limit = limit;
    }

    public Action getApprovalActionType() {
        if (approval == null) {
            return null;
        }

        return approval.getActionType();
    }

    public String getApprovalComments() {
        if (approval == null) {
            return null;
        }

        return approval.getComments();
    }

    public String getApprovalContextActorId() {
        if (approval == null) {
            return null;
        }

        return approval.getContextActorId();
    }

    public String getApprovalContextId() {
        if (approval == null) {
            return null;
        }

        return approval.getContextId();
    }

    public List<String> getApprovalNextApproverIds() {
        if (approval == null) {
            return null;
        }

        return approval.getNextApproverIds();
    }

    public String getApprovalProcessDefinitionNameOrId() {
        if (approval == null) {
            return null;
        }

        return approval.getProcessDefinitionNameOrId();
    }

    public boolean isApprovalSkipEntryCriteria() {
        if (approval == null) {
            return false;
        }

        return approval.isSkipEntryCriteria();
    }

    /**
     * Represents the kind of action to take: Submit, Approve, or Reject.
     *
     * @param actionType
     */
    public void setApprovalActionType(final Action actionType) {
        if (approval == null) {
            approval = new ApprovalRequest();
        }

        approval.setActionType(actionType);
    }

    /**
     * The comment to add to the history step associated with this request.
     *
     * @param comments
     */
    public void setApprovalComments(final String comments) {
        if (approval == null) {
            approval = new ApprovalRequest();
        }

        approval.setComments(comments);
    }

    /**
     * The ID of the submitter who’s requesting the approval record.
     *
     * @param contextActorId
     */
    public void setApprovalContextActorId(final String contextActorId) {
        if (approval == null) {
            approval = new ApprovalRequest();
        }

        approval.setContextActorId(contextActorId);
    }

    /**
     * The ID of the item that is being acted upon.
     *
     * @param contextId
     */
    public void setApprovalContextId(final String contextId) {
        if (approval == null) {
            approval = new ApprovalRequest();
        }

        approval.setContextId(contextId);
    }

    /**
     * If the process requires specification of the next approval, the ID of the
     * user to be assigned the next request.
     *
     * @param nextApproverIds
     */
    public void setApprovalNextApproverIds(final List<String> nextApproverIds) {
        if (approval == null) {
            approval = new ApprovalRequest();
        }

        approval.setNextApproverIds(nextApproverIds);
    }

    /**
     * If the process requires specification of the next approval, the ID of the
     * user to be assigned the next request.
     *
     * @param nextApproverId
     */
    public void setApprovalNextApproverIds(String nextApproverId) {
        if (approval == null) {
            approval = new ApprovalRequest();
        }

        approval.setNextApproverIds(nextApproverId);
    }

    /**
     * The developer name or ID of the process definition.
     *
     * @param processDefinitionNameOrId
     */
    public void setApprovalProcessDefinitionNameOrId(final String processDefinitionNameOrId) {
        if (approval == null) {
            approval = new ApprovalRequest();
        }

        approval.setProcessDefinitionNameOrId(processDefinitionNameOrId);
    }

    /**
     * Determines whether to evaluate the entry criteria for the process (true)
     * or not (false) if the process definition name or ID isn’t null. If the
     * process definition name or ID isn’t specified, this argument is ignored,
     * and standard evaluation is followed based on process order. By default,
     * the entry criteria isn’t skipped if it’s not set by this request.
     *
     * @param skipEntryCriteria
     */
    public void setApprovalSkipEntryCriteria(final boolean skipEntryCriteria) {
        if (approval == null) {
            approval = new ApprovalRequest();
        }

        approval.setSkipEntryCriteria(skipEntryCriteria);
    }

    public NotFoundBehaviour getNotFoundBehaviour() {
        return notFoundBehaviour;
    }

    /**
     * Sets the behaviour of 404 not found status received from Salesforce API.
     * Should the body be set to NULL {@link NotFoundBehaviour#NULL} or should a
     * exception be signaled on the exchange {@link NotFoundBehaviour#EXCEPTION}
     * - the default.
     */
    public void setNotFoundBehaviour(final NotFoundBehaviour notFoundBehaviour) {
        this.notFoundBehaviour = notFoundBehaviour;
    }
}
