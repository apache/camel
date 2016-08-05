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
package org.apache.camel.component.salesforce.internal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;

import org.apache.camel.component.salesforce.api.PicklistEnumConverter;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;

//CHECKSTYLE:OFF
/**
 * Salesforce DTO for SObject PushTopic
 */
@XStreamAlias("PushTopic")
public class PushTopic extends AbstractSObjectBase {

    // WARNING: these fields have case sensitive names,
    // the field name MUST match the field name used by Salesforce
    // DO NOT change these field names to camel case!!!
    private String Query;

    private Double ApiVersion;

    private Boolean IsActive;

    @XStreamConverter(PicklistEnumConverter.class)
    private NotifyForFieldsEnum NotifyForFields;

    @XStreamConverter(PicklistEnumConverter.class)
    private NotifyForOperationsEnum NotifyForOperations;

    private String Description;

    private Boolean NotifyForOperationCreate;

    private Boolean NotifyForOperationUpdate;

    private Boolean NotifyForOperationDelete;

    private Boolean NotifyForOperationUndelete;

    @JsonProperty("Query")
    public String getQuery() {
        return this.Query;
    }

    @JsonProperty("Query")
    public void setQuery(String query) {
        this.Query = query;
    }

    @JsonProperty("ApiVersion")
    public Double getApiVersion() {
        return this.ApiVersion;
    }

    @JsonProperty("ApiVersion")
    public void setApiVersion(Double apiVersion) {
        this.ApiVersion = apiVersion;
    }

    @JsonProperty("IsActive")
    public Boolean getIsActive() {
        return this.IsActive;
    }

    @JsonProperty("IsActive")
    public void setIsActive(Boolean isActive) {
        this.IsActive = isActive;
    }

    @JsonProperty("NotifyForFields")
    public NotifyForFieldsEnum getNotifyForFields() {
        return this.NotifyForFields;
    }

    @JsonProperty("NotifyForFields")
    public void setNotifyForFields(NotifyForFieldsEnum notifyForFields) {
        this.NotifyForFields = notifyForFields;
    }

    @JsonProperty("NotifyForOperations")
    public NotifyForOperationsEnum getNotifyForOperations() {
        return this.NotifyForOperations;
    }

    @JsonProperty("NotifyForOperations")
    public void setNotifyForOperations(NotifyForOperationsEnum notifyForOperations) {
        this.NotifyForOperations = notifyForOperations;
    }

    @JsonProperty("Description")
    public String getDescription() {
        return this.Description;
    }

    @JsonProperty("Description")
    public void setDescription(String description) {
        this.Description = description;
    }

    @JsonProperty("NotifyForOperationCreate")
    public Boolean getNotifyForOperationCreate() {
        return this.NotifyForOperationCreate;
    }

    @JsonProperty("NotifyForOperationCreate")
    public void setNotifyForOperationCreate(Boolean notifyForOperationCreate) {
        this.NotifyForOperationCreate = notifyForOperationCreate;
    }

    @JsonProperty("NotifyForOperationUpdate")
    public Boolean getNotifyForOperationUpdate() {
        return this.NotifyForOperationUpdate;
    }

    @JsonProperty("NotifyForOperationUpdate")
    public void setNotifyForOperationUpdate(Boolean notifyForOperationUpdate) {
        this.NotifyForOperationUpdate = notifyForOperationUpdate;
    }

    @JsonProperty("NotifyForOperationDelete")
    public Boolean getNotifyForOperationDelete() {
        return this.NotifyForOperationDelete;
    }

    @JsonProperty("NotifyForOperationDelete")
    public void setNotifyForOperationDelete(Boolean notifyForOperationDelete) {
        this.NotifyForOperationDelete = notifyForOperationDelete;
    }

    @JsonProperty("NotifyForOperationUndelete")
    public Boolean getNotifyForOperationUndelete() {
        return this.NotifyForOperationUndelete;
    }

    @JsonProperty("NotifyForOperationUndelete")
    public void setNotifyForOperationUndelete(Boolean notifyForOperationUndelete) {
        this.NotifyForOperationUndelete = notifyForOperationUndelete;
    }
}
//CHECKSTYLE:ON
