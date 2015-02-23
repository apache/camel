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
package org.apache.camel.component.salesforce;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.salesforce.api.dto.bulk.ContentType;
import org.apache.camel.component.salesforce.internal.PayloadFormat;
import org.apache.camel.component.salesforce.internal.dto.NotifyForFieldsEnum;
import org.apache.camel.component.salesforce.internal.dto.NotifyForOperationsEnum;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.eclipse.jetty.client.HttpClient;

/**
 * Salesforce Endpoint configuration.
 */
@UriParams
public class SalesforceEndpointConfig implements Cloneable {

    // default API version
    public static final String DEFAULT_VERSION = "33.0";

    // general parameter
    public static final String API_VERSION = "apiVersion";

    // parameters for Rest API
    public static final String FORMAT = "format";
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

    // prefix for parameters in headers
    public static final String APEX_QUERY_PARAM_PREFIX = "apexQueryParam.";

    // parameters for Bulk API
    public static final String CONTENT_TYPE = "contentType";
    public static final String JOB_ID = "jobId";
    public static final String BATCH_ID = "batchId";
    public static final String RESULT_ID = "resultId";

    // general properties
    @UriParam
    private String apiVersion = DEFAULT_VERSION;

    // Rest API properties
    @UriParam
    private PayloadFormat format = PayloadFormat.JSON;
    @UriParam
    private String sObjectName;
    @UriParam
    private String sObjectId;
    @UriParam
    private String sObjectFields;
    @UriParam
    private String sObjectIdName;
    @UriParam
    private String sObjectIdValue;
    @UriParam
    private String sObjectBlobFieldName;
    @UriParam
    private String sObjectClass;
    @UriParam
    private String sObjectQuery;
    @UriParam
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

    // Jetty HttpClient, set using reference
    @UriParam
    private HttpClient httpClient;

    public SalesforceEndpointConfig copy() {
        try {
            final SalesforceEndpointConfig copy = (SalesforceEndpointConfig) super.clone();
            // nothing to deep copy
            return copy;
        } catch (CloneNotSupportedException ex) {
            throw new RuntimeCamelException(ex);
        }
    }

    public PayloadFormat getFormat() {
        return format;
    }

    public void setFormat(PayloadFormat format) {
        this.format = format;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getSObjectName() {
        return sObjectName;
    }

    public void setSObjectName(String sObjectName) {
        this.sObjectName = sObjectName;
    }

    public String getSObjectId() {
        return sObjectId;
    }

    public void setSObjectId(String sObjectId) {
        this.sObjectId = sObjectId;
    }

    public String getSObjectFields() {
        return sObjectFields;
    }

    public void setSObjectFields(String sObjectFields) {
        this.sObjectFields = sObjectFields;
    }

    public String getSObjectIdName() {
        return sObjectIdName;
    }

    public void setSObjectIdName(String sObjectIdName) {
        this.sObjectIdName = sObjectIdName;
    }

    public String getSObjectIdValue() {
        return sObjectIdValue;
    }

    public void setSObjectIdValue(String sObjectIdValue) {
        this.sObjectIdValue = sObjectIdValue;
    }

    public String getSObjectBlobFieldName() {
        return sObjectBlobFieldName;
    }

    public void setSObjectBlobFieldName(String sObjectBlobFieldName) {
        this.sObjectBlobFieldName = sObjectBlobFieldName;
    }

    public String getSObjectClass() {
        return sObjectClass;
    }

    public void setSObjectClass(String sObjectClass) {
        this.sObjectClass = sObjectClass;
    }

    public String getSObjectQuery() {
        return sObjectQuery;
    }

    public void setSObjectQuery(String sObjectQuery) {
        this.sObjectQuery = sObjectQuery;
    }

    public String getSObjectSearch() {
        return sObjectSearch;
    }

    public void setSObjectSearch(String sObjectSearch) {
        this.sObjectSearch = sObjectSearch;
    }

    public String getApexMethod() {
        return apexMethod;
    }

    public void setApexMethod(String apexMethod) {
        this.apexMethod = apexMethod;
    }

    public String getApexUrl() {
        return apexUrl;
    }

    public void setApexUrl(String apexUrl) {
        this.apexUrl = apexUrl;
    }

    public Map<String, Object> getApexQueryParams() {
        return apexQueryParams == null ? Collections.EMPTY_MAP : Collections.unmodifiableMap(apexQueryParams);
    }

    public void setApexQueryParams(Map<String, Object> apexQueryParams) {
        this.apexQueryParams = apexQueryParams;
    }

    public ContentType getContentType() {
        return contentType;
    }

    public void setContentType(ContentType contentType) {
        this.contentType = contentType;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getResultId() {
        return resultId;
    }

    public void setResultId(String resultId) {
        this.resultId = resultId;
    }

    public boolean isUpdateTopic() {
        return updateTopic;
    }

    public void setUpdateTopic(boolean updateTopic) {
        this.updateTopic = updateTopic;
    }

    public NotifyForFieldsEnum getNotifyForFields() {
        return notifyForFields;
    }

    public void setNotifyForFields(NotifyForFieldsEnum notifyForFields) {
        this.notifyForFields = notifyForFields;
    }

    public NotifyForOperationsEnum getNotifyForOperations() {
        return notifyForOperations;
    }

    public void setNotifyForOperations(NotifyForOperationsEnum notifyForOperations) {
        this.notifyForOperations = notifyForOperations;
    }

    public Boolean getNotifyForOperationCreate() {
        return notifyForOperationCreate;
    }

    public void setNotifyForOperationCreate(Boolean notifyForOperationCreate) {
        this.notifyForOperationCreate = notifyForOperationCreate;
    }

    public Boolean getNotifyForOperationUpdate() {
        return notifyForOperationUpdate;
    }

    public void setNotifyForOperationUpdate(Boolean notifyForOperationUpdate) {
        this.notifyForOperationUpdate = notifyForOperationUpdate;
    }

    public Boolean getNotifyForOperationDelete() {
        return notifyForOperationDelete;
    }

    public void setNotifyForOperationDelete(Boolean notifyForOperationDelete) {
        this.notifyForOperationDelete = notifyForOperationDelete;
    }

    public Boolean getNotifyForOperationUndelete() {
        return notifyForOperationUndelete;
    }

    public void setNotifyForOperationUndelete(Boolean notifyForOperationUndelete) {
        this.notifyForOperationUndelete = notifyForOperationUndelete;
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public Map<String, String> toValueMap() {

        final Map<String, String> valueMap = new HashMap<String, String>();
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
        // apexQueryParams are handled explicitly in AbstractRestProcessor

        // add bulk API properties
        if (contentType != null) {
            valueMap.put(CONTENT_TYPE, contentType.value());
        }
        valueMap.put(JOB_ID, jobId);
        valueMap.put(BATCH_ID, batchId);
        valueMap.put(RESULT_ID, resultId);

        return Collections.unmodifiableMap(valueMap);
    }
}