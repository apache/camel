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
import com.salesforce.eventbus.protobuf.ReplayPreset;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportMetadata;
import org.apache.camel.component.salesforce.api.dto.approval.ApprovalRequest;
import org.apache.camel.component.salesforce.api.dto.approval.ApprovalRequest.Action;
import org.apache.camel.component.salesforce.api.dto.bulk.ContentType;
import org.apache.camel.component.salesforce.internal.PayloadFormat;
import org.apache.camel.component.salesforce.internal.dto.EventSchemaFormatEnum;
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
    public static final String DEFAULT_VERSION = "56.0";

    // general parameter
    public static final String API_VERSION = "apiVersion";

    // parameters for Rest API
    public static final String FORMAT = "format";
    public static final String RAW_PAYLOAD = "rawPayload";

    public static final String SOBJECT_NAME = "sObjectName";
    public static final String SOBJECT_ID = "sObjectId";
    public static final String SOBJECT_IDS = "sObjectIds";
    public static final String SOBJECT_FIELDS = "sObjectFields";
    public static final String SOBJECT_EXT_ID_NAME = "sObjectIdName";
    public static final String SOBJECT_EXT_ID_VALUE = "sObjectIdValue";
    public static final String SOBJECT_BLOB_FIELD_NAME = "sObjectBlobFieldName";
    public static final String SOBJECT_CLASS = "sObjectClass";
    public static final String SOBJECT_QUERY = "sObjectQuery";
    public static final String STREAM_QUERY_RESULT = "streamQueryResult";
    public static final String SOBJECT_SEARCH = "sObjectSearch";
    public static final String APEX_METHOD = "apexMethod";
    public static final String APEX_URL = "apexUrl";
    public static final String COMPOSITE_METHOD = "compositeMethod";
    public static final String LIMIT = "limit";
    public static final String ALL_OR_NONE = "allOrNone";
    public static final String EVENT_NAME = "eventName";
    public static final String EVENT_SCHEMA_ID = "eventSchemaId";
    public static final String EVENT_SCHEMA_FORMAT = "eventSchemaFormat";

    // prefix for parameters in headers
    public static final String APEX_QUERY_PARAM_PREFIX = "apexQueryParam.";

    // parameters for Bulk API
    public static final String CONTENT_TYPE = "contentType";
    public static final String JOB_ID = "jobId";
    public static final String BATCH_ID = "batchId";
    public static final String RESULT_ID = "resultId";
    public static final String QUERY_LOCATOR = "queryLocator";
    public static final String LOCATOR = "locator";
    public static final String MAX_RECORDS = "maxRecords";
    public static final String PK_CHUNKING = "pkChunking";
    public static final String PK_CHUNKING_CHUNK_SIZE = "pkChunkingChunkSize";
    public static final String PK_CHUNKING_PARENT = "pkChunkingParent";
    public static final String PK_CHUNKING_START_ROW = "pkChunkingStartRow";

    // parameters for Analytics API
    public static final String REPORT_ID = "reportId";
    public static final String INCLUDE_DETAILS = "includeDetails";
    public static final String REPORT_METADATA = "reportMetadata";
    public static final String INSTANCE_ID = "instanceId";

    // parameters for Streaming API
    public static final String DEFAULT_REPLAY_ID = "defaultReplayId";
    public static final String FALL_BACK_REPLAY_ID = "fallBackReplayId";
    public static final String INITIAL_REPLAY_ID_MAP = "initialReplayIdMap";
    public static final long REPLAY_FROM_TIP = -1L;

    // parameters for Pub/Sub API
    public static final String REPLAY_PRESET = "replayPreset";
    public static final String PUB_SUB_DESERIALIZE_TYPE = "pubSubDeserializeType";
    public static final String PUB_SUB_POJO_CLASS = "pubSubPojoClass";

    // parameters for Approval API
    public static final String APPROVAL = "approval";

    // parameters for the RAW operation
    public static final String RAW_PATH = "rawPath";
    public static final String RAW_METHOD = "rawMethod";
    public static final String RAW_QUERY_PARAMETERS = "rawQueryParameters";
    public static final String RAW_HTTP_HEADERS = "rawHttpHeaders";

    // default maximum authentication retries on failed authentication or
    // expired session
    public static final int DEFAULT_MAX_AUTHENTICATION_RETRIES = 4;

    // default increment and limit for Streaming connection restart attempts
    public static final long DEFAULT_BACKOFF_INCREMENT = 1000L;
    public static final long DEFAULT_MAX_BACKOFF = 30000L;

    public static final String NOT_FOUND_BEHAVIOUR = "notFoundBehaviour";

    // general properties
    @UriParam(defaultValue = DEFAULT_VERSION)
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
    @UriParam(displayName = "Stream query result", defaultValue = "false")
    private Boolean streamQueryResult = false;
    @UriParam(displayName = "SObject Search")
    private String sObjectSearch;
    @UriParam
    private String apexMethod;
    @UriParam(displayName = "Event Name", label = "producer")
    private String eventName;
    @UriParam(displayName = "Event Schema Format", label = "producer")
    private EventSchemaFormatEnum eventSchemaFormat;
    @UriParam(displayName = "Event Schema Id", label = "producer")
    private String eventSchemaId;
    @UriParam(label = "producer")
    private String compositeMethod;
    @UriParam(label = "producer", defaultValue = "false", description = "Composite API option to indicate" +
                                                                        " to rollback all records if any are not successful.")
    private boolean allOrNone;
    @UriParam(label = "producer")
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
    @UriParam
    private String queryLocator;
    @UriParam
    private String locator;
    @UriParam(javaType = "java.lang.Integer")
    private Integer maxRecords;
    @UriParam
    private Boolean pkChunking;
    @UriParam
    private Integer pkChunkingChunkSize;
    @UriParam
    private String pkChunkingParent;
    @UriParam
    private String pkChunkingStartRow;

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

    // Pub/Sub API properties
    @UriParam(label = "consumer", defaultValue = "100",
              description = "Max number of events to receive in a batch from the Pub/Sub API.")
    private int pubSubBatchSize = 100;

    @UriParam(label = "consumer", defaultValue = "AVRO",
              description = "How to deserialize events consume from the Pub/Sub API. AVRO will try a " +
                            "SpecificRecord subclass if found, otherwise GenericRecord.",
              enums = "AVRO,SPECIFIC_RECORD,GENERIC_RECORD,POJO,JSON")
    private PubSubDeserializeType pubSubDeserializeType = PubSubDeserializeType.AVRO;

    @UriParam(label = "consumer", description = "Replay preset for Pub/Sub API.", defaultValue = "LATEST",
              enums = "LATEST,EARLIEST,CUSTOM")
    private ReplayPreset replayPreset = ReplayPreset.LATEST;

    @UriParam(label = "consumer", description = "Fully qualified class name to deserialize Pub/Sub API event to.")
    private String pubSubPojoClass;

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
    @UriParam(description = "Default replayId setting if no value is found in initialReplayIdMap",
              defaultValue = "" + REPLAY_FROM_TIP)
    private Long defaultReplayId = REPLAY_FROM_TIP;

    @UriParam(description = "ReplayId to fall back to after an Invalid Replay Id response",
              defaultValue = "" + REPLAY_FROM_TIP)
    private Long fallBackReplayId = REPLAY_FROM_TIP;

    @UriParam
    private Map<String, Long> initialReplayIdMap;

    // Approval API properties
    private ApprovalRequest approval;

    // RAW operation properties
    @UriParam(label = "producer")
    private String rawPath;
    @UriParam(label = "producer")
    private String rawMethod;
    @UriParam(label = "producer")
    private String rawQueryParameters;
    @UriParam(label = "producer")
    private String rawHttpHeaders;

    // Salesforce Jetty9 HttpClient, set using reference
    @UriParam
    private SalesforceHttpClient httpClient;

    // To allow custom ObjectMapper (for registering extra datatype modules)
    @UriParam
    private ObjectMapper objectMapper;

    // Streaming connection restart attempt backoff interval increment
    @UriParam(javaType = "java.time.Duration", defaultValue = "" + DEFAULT_BACKOFF_INCREMENT)
    private long backoffIncrement = DEFAULT_BACKOFF_INCREMENT;

    // Streaming connection restart attempt maximum backoff interval
    @UriParam(javaType = "java.time.Duration", defaultValue = "" + DEFAULT_MAX_BACKOFF)
    private long maxBackoff = DEFAULT_MAX_BACKOFF;

    @UriParam
    private Integer limit;

    @UriParam(defaultValue = "EXCEPTION")
    private NotFoundBehaviour notFoundBehaviour = NotFoundBehaviour.EXCEPTION;

    public SalesforceEndpointConfig copy() {
        try {
            // nothing to deep copy, getApexQueryParams() is readonly, so no
            // need to deep copy
            return (SalesforceEndpointConfig) super.clone();
        } catch (CloneNotSupportedException ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    public PayloadFormat getFormat() {
        return format;
    }

    /**
     * Payload format to use for Salesforce API calls, either JSON or XML, defaults to JSON. As of Camel 3.12, this
     * option only applies to the Raw operation.
     */
    public void setFormat(PayloadFormat format) {
        this.format = format;
    }

    public boolean isRawPayload() {
        return rawPayload;
    }

    /**
     * Use raw payload {@link String} for request and response (either JSON or XML depending on {@code format}), instead
     * of DTOs, false by default
     */
    public void setRawPayload(boolean rawPayload) {
        this.rawPayload = rawPayload;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    /**
     * Salesforce API version.
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
     * Fully qualified SObject class name, usually generated using camel-salesforce-maven-plugin
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

    public Boolean getStreamQueryResult() {
        return streamQueryResult;
    }

    /**
     * If true, streams SOQL query result and transparently handles subsequent requests if there are multiple pages.
     * Otherwise, results are returned one page at a time.
     */
    public void setStreamQueryResult(Boolean streamQueryResult) {
        this.streamQueryResult = streamQueryResult;
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

    public String getCompositeMethod() {
        return compositeMethod;
    }

    /**
     * Composite (raw) method.
     */
    public void setCompositeMethod(String compositeMethod) {
        this.compositeMethod = compositeMethod;
    }

    public boolean isAllOrNone() {
        return allOrNone;
    }

    public void setAllOrNone(boolean allOrNone) {
        this.allOrNone = allOrNone;
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

    public String getQueryLocator() {
        return queryLocator;
    }

    /**
     * Query Locator provided by salesforce for use when a query results in more records than can be retrieved in a
     * single call. Use this value in a subsequent call to retrieve additional records.
     */
    public void setQueryLocator(String queryLocator) {
        this.queryLocator = queryLocator;
    }

    public String getLocator() {
        return locator;
    }

    /**
     * Locator provided by salesforce Bulk 2.0 API for use in getting results for a Query job.
     */
    public void setLocator(String locator) {
        this.locator = locator;
    }

    public Integer getMaxRecords() {
        return maxRecords;
    }

    /**
     * The maximum number of records to retrieve per set of results for a Bulk 2.0 Query. The request is still subject
     * to the size limits. If you are working with a very large number of query results, you may experience a timeout
     * before receiving all the data from Salesforce. To prevent a timeout, specify the maximum number of records your
     * client is expecting to receive in the maxRecords parameter. This splits the results into smaller sets with this
     * value as the maximum size.
     */
    public void setMaxRecords(Integer maxRecords) {
        this.maxRecords = maxRecords;
    }

    public Boolean getPkChunking() {
        return pkChunking;
    }

    /**
     * Use PK Chunking. Only for use in original Bulk API. Bulk 2.0 API performs PK chunking automatically, if
     * necessary.
     */
    public void setPkChunking(Boolean pkChunking) {
        this.pkChunking = pkChunking;
    }

    public Integer getPkChunkingChunkSize() {
        return pkChunkingChunkSize;
    }

    /**
     * Chunk size for use with PK Chunking. If unspecified, salesforce default is 100,000. Maximum size is 250,000.
     */
    public void setPkChunkingChunkSize(Integer pkChunkingChunkSize) {
        this.pkChunkingChunkSize = pkChunkingChunkSize;
    }

    public String getPkChunkingParent() {
        return pkChunkingParent;
    }

    /**
     * Specifies the parent object when you're enabling PK chunking for queries on sharing objects. The chunks are based
     * on the parent object's records rather than the sharing object's records. For example, when querying on
     * AccountShare, specify Account as the parent object. PK chunking is supported for sharing objects as long as the
     * parent object is supported.
     */
    public void setPkChunkingParent(String pkChunkingParent) {
        this.pkChunkingParent = pkChunkingParent;
    }

    public String getPkChunkingStartRow() {
        return pkChunkingStartRow;
    }

    /**
     * Specifies the 15-character or 18-character record ID to be used as the lower boundary for the first chunk. Use
     * this parameter to specify a starting ID when restarting a job that failed between batches.
     */
    public void setPkChunkingStartRow(String pkChunkingStartRow) {
        this.pkChunkingStartRow = pkChunkingStartRow;
    }

    /**
     * Whether to update an existing Push Topic when using the Streaming API, defaults to false
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
     * Notify for operations, options are ALL, CREATE, EXTENDED, UPDATE (API version &lt; 29.0)
     */
    public void setNotifyForOperations(NotifyForOperationsEnum notifyForOperations) {
        this.notifyForOperations = notifyForOperations;
    }

    public Boolean getNotifyForOperationCreate() {
        return notifyForOperationCreate;
    }

    /**
     * Notify for create operation, defaults to false (API version &gt;= 29.0)
     */
    public void setNotifyForOperationCreate(Boolean notifyForOperationCreate) {
        this.notifyForOperationCreate = notifyForOperationCreate;
    }

    public Boolean getNotifyForOperationUpdate() {
        return notifyForOperationUpdate;
    }

    /**
     * Notify for update operation, defaults to false (API version &gt;= 29.0)
     */
    public void setNotifyForOperationUpdate(Boolean notifyForOperationUpdate) {
        this.notifyForOperationUpdate = notifyForOperationUpdate;
    }

    public Boolean getNotifyForOperationDelete() {
        return notifyForOperationDelete;
    }

    /**
     * Notify for delete operation, defaults to false (API version &gt;= 29.0)
     */
    public void setNotifyForOperationDelete(Boolean notifyForOperationDelete) {
        this.notifyForOperationDelete = notifyForOperationDelete;
    }

    public Boolean getNotifyForOperationUndelete() {
        return notifyForOperationUndelete;
    }

    /**
     * Notify for un-delete operation, defaults to false (API version &gt;= 29.0)
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
     * Backoff interval increment for Streaming connection restart attempts for failures beyond CometD auto-reconnect.
     */
    public void setBackoffIncrement(long backoffIncrement) {
        this.backoffIncrement = backoffIncrement;
    }

    public long getMaxBackoff() {
        return maxBackoff;
    }

    /**
     * Maximum backoff interval for Streaming connection restart attempts for failures beyond CometD auto-reconnect.
     */
    public void setMaxBackoff(long maxBackoff) {
        this.maxBackoff = maxBackoff;
    }

    /**
     * Custom Jackson ObjectMapper to use when serializing/deserializing Salesforce objects.
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
        valueMap.put(STREAM_QUERY_RESULT, streamQueryResult);
        valueMap.put(SOBJECT_SEARCH, sObjectSearch);
        valueMap.put(APEX_METHOD, apexMethod);
        valueMap.put(APEX_URL, apexUrl);
        // apexQueryParams are handled explicitly in AbstractRestProcessor
        valueMap.put(COMPOSITE_METHOD, compositeMethod);
        valueMap.put(LIMIT, limit);
        valueMap.put(APPROVAL, approval);
        valueMap.put(EVENT_NAME, eventName);
        valueMap.put(EVENT_SCHEMA_FORMAT, eventSchemaFormat);
        valueMap.put(EVENT_SCHEMA_ID, eventSchemaId);

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
        valueMap.put(FALL_BACK_REPLAY_ID, fallBackReplayId);
        valueMap.put(INITIAL_REPLAY_ID_MAP, initialReplayIdMap);

        // add Pub/Sub API properties
        valueMap.put(REPLAY_PRESET, initialReplayIdMap);
        valueMap.put(PUB_SUB_DESERIALIZE_TYPE, pubSubDeserializeType);
        valueMap.put(PUB_SUB_POJO_CLASS, pubSubPojoClass);

        valueMap.put(NOT_FOUND_BEHAVIOUR, notFoundBehaviour);

        valueMap.put(RAW_PATH, rawPath);
        valueMap.put(RAW_METHOD, rawMethod);
        valueMap.put(RAW_HTTP_HEADERS, rawHttpHeaders);
        valueMap.put(RAW_QUERY_PARAMETERS, rawQueryParameters);

        return Collections.unmodifiableMap(valueMap);
    }

    public Long getDefaultReplayId() {
        return defaultReplayId;
    }

    /**
     * Default replayId setting if no value is found in {@link #initialReplayIdMap}
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

    public Long getFallBackReplayId() {
        return fallBackReplayId;
    }

    /**
     * ReplayId to fall back to after an Invalid Replay Id response
     */
    public void setFallBackReplayId(Long fallBackReplayId) {
        this.fallBackReplayId = fallBackReplayId;
    }

    /**
     * ReplayPreset for Pub/Sub API
     */
    public ReplayPreset getReplayPreset() {
        return replayPreset;
    }

    public void setReplayPreset(ReplayPreset replayPreset) {
        this.replayPreset = replayPreset;
    }

    /**
     * Type of deserialization for Pub/Sub API events
     *
     * @return
     */
    public PubSubDeserializeType getPubSubDeserializeType() {
        return pubSubDeserializeType;
    }

    public void setPubSubDeserializeType(PubSubDeserializeType pubSubDeserializeType) {
        this.pubSubDeserializeType = pubSubDeserializeType;
    }

    /**
     * Class to deserialize Pub/Sub API events to
     *
     * @return
     */
    public String getPubSubPojoClass() {
        return pubSubPojoClass;
    }

    public void setPubSubPojoClass(String pubSubPojoClass) {
        this.pubSubPojoClass = pubSubPojoClass;
    }

    public Integer getLimit() {
        return limit;
    }

    /**
     * Limit on number of returned records. Applicable to some of the API, check the Salesforce documentation.
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
     * If the process requires specification of the next approval, the ID of the user to be assigned the next request.
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
     * If the process requires specification of the next approval, the ID of the user to be assigned the next request.
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
     * Determines whether to evaluate the entry criteria for the process (true) or not (false) if the process definition
     * name or ID isn’t null. If the process definition name or ID isn’t specified, this argument is ignored, and
     * standard evaluation is followed based on process order. By default, the entry criteria isn’t skipped if it’s not
     * set by this request.
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
     * Sets the behaviour of 404 not found status received from Salesforce API. Should the body be set to NULL
     * {@link NotFoundBehaviour#NULL} or should a exception be signaled on the exchange
     * {@link NotFoundBehaviour#EXCEPTION} - the default.
     */
    public void setNotFoundBehaviour(final NotFoundBehaviour notFoundBehaviour) {
        this.notFoundBehaviour = notFoundBehaviour;
    }

    public String getRawPath() {
        return rawPath;
    }

    /**
     * The portion of the endpoint URL after the domain name. E.g., " + "'/services/data/v52.0/sobjects/Account/'
     *
     * @param rawPath the path
     */
    public void setRawPath(String rawPath) {
        this.rawPath = rawPath;
    }

    public String getRawMethod() {
        return rawMethod;
    }

    /**
     * HTTP method to use for the Raw operation
     *
     * @param rawMethod http method
     */
    public void setRawMethod(String rawMethod) {
        this.rawMethod = rawMethod;
    }

    public String getRawQueryParameters() {
        return rawQueryParameters;
    }

    /**
     * Comma separated list of message headers to include as query parameters for Raw operation. Do not url-encode
     * values as this will be done automatically.
     *
     * @param rawQueryParameters
     */
    public void setRawQueryParameters(String rawQueryParameters) {
        this.rawQueryParameters = rawQueryParameters;
    }

    public String getRawHttpHeaders() {
        return rawHttpHeaders;
    }

    /**
     * Comma separated list of message headers to include as HTTP parameters for Raw operation.
     *
     * @param
     */
    public void setRawHttpHeaders(String rawHttpHeaders) {
        this.rawHttpHeaders = rawHttpHeaders;
    }

    public int getPubSubBatchSize() {
        return pubSubBatchSize;
    }

    public void setPubSubBatchSize(int pubSubBatchSize) {
        this.pubSubBatchSize = pubSubBatchSize;
    }

    public String getEventName() {
        return eventName;
    }

    /**
     * Name of Platform Event, Change Data Capture Event, custom event, etc.
     *
     * @param eventName
     */
    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public EventSchemaFormatEnum getEventSchemaFormat() {
        return eventSchemaFormat;
    }

    /**
     * EXPANDED: Apache Avro format but doesn’t strictly adhere to the record complex type. COMPACT: Apache Avro,
     * adheres to the specification for the record complex type. This parameter is available in API version 43.0 and
     * later.
     *
     * @param eventSchemaFormat
     */
    public void setEventSchemaFormat(EventSchemaFormatEnum eventSchemaFormat) {
        this.eventSchemaFormat = eventSchemaFormat;
    }

    public String getEventSchemaId() {
        return eventSchemaId;
    }

    /**
     * The ID of the event schema.
     *
     * @param eventSchemaId
     */
    public void setEventSchemaId(String eventSchemaId) {
        this.eventSchemaId = eventSchemaId;
    }
}
