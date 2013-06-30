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

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import org.apache.camel.component.salesforce.api.PicklistEnumConverter;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;
import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Salesforce DTO for SObject PushTopic
 */
@XStreamAlias("PushTopic")
public class PushTopic extends AbstractSObjectBase {

    private String query;
    private Double apiVersion;
    private Boolean isActive;
    @XStreamConverter(PicklistEnumConverter.class)
    private NotifyForFieldsEnum notifyForFields;
    @XStreamConverter(PicklistEnumConverter.class)
    private NotifyForOperationsEnum notifyForOperations;
    private String description;

    @JsonProperty("Query")
    public String getQuery() {
        return this.query;
    }

    @JsonProperty("Query")
    public void setQuery(String query) {
        this.query = query;
    }

    @JsonProperty("ApiVersion")
    public Double getApiVersion() {
        return this.apiVersion;
    }

    @JsonProperty("ApiVersion")
    public void setApiVersion(Double apiVersion) {
        this.apiVersion = apiVersion;
    }

    @JsonProperty("IsActive")
    public Boolean getIsActive() {
        return this.isActive;
    }

    @JsonProperty("IsActive")
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    @JsonProperty("NotifyForFields")
    public NotifyForFieldsEnum getNotifyForFields() {
        return this.notifyForFields;
    }

    @JsonProperty("NotifyForFields")
    public void setNotifyForFields(NotifyForFieldsEnum notifyForFields) {
        this.notifyForFields = notifyForFields;
    }

    @JsonProperty("NotifyForOperations")
    public NotifyForOperationsEnum getNotifyForOperations() {
        return this.notifyForOperations;
    }

    @JsonProperty("NotifyForOperations")
    public void setNotifyForOperations(NotifyForOperationsEnum notifyForOperations) {
        this.notifyForOperations = notifyForOperations;
    }

    @JsonProperty("Description")
    public String getDescription() {
        return this.description;
    }

    @JsonProperty("Description")
    public void setDescription(String description) {
        this.description = description;
    }
}