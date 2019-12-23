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
package org.apache.camel.component.salesforce.api.dto;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

//CHECKSTYLE:OFF
@JsonFilter("fieldsToNull")
public abstract class AbstractSObjectBase extends AbstractDTOBase {

    // WARNING: these fields have case sensitive names,
    // the field name MUST match the field name used by Salesforce
    // DO NOT change these field names to camel case!!!
    @XStreamOmitField
    private Attributes attributes;
    private String Id;
    private String OwnerId;
    private Boolean IsDeleted;
    private String Name;
    private ZonedDateTime CreatedDate;
    private String CreatedById;
    private ZonedDateTime LastModifiedDate;
    private String LastModifiedById;
    private ZonedDateTime SystemModstamp;
    private ZonedDateTime LastActivityDate;
    private ZonedDateTime LastViewedDate;
    private ZonedDateTime LastReferencedDate;

    @XStreamOmitField
    private Set<String> fieldsToNull = new HashSet<>();

    public AbstractSObjectBase() {
        attributes = new Attributes();
    }

    /**
     * Utility method to clear all system {@link AbstractSObjectBase} fields.
     * <p>
     * Useful when reusing a DTO for a new record, or for update/upsert.
     * </p>
     * <p>
     * This method does not clear {@code Name} to allow updating it, so it must
     * be explicitly set to {@code null} if needed.
     * </p>
     */
    public final void clearBaseFields() {
//
        attributes = null;
        Id = null;
        IsDeleted = null;
        CreatedDate = null;
        CreatedById = null;
        LastModifiedDate = null;
        LastModifiedById = null;
        SystemModstamp = null;
        LastActivityDate = null;
    }

    @JsonProperty("attributes")
    public Attributes getAttributes() {
        return attributes;
    }

    @JsonProperty("attributes")
    public void setAttributes(Attributes attributes) {
        this.attributes = attributes;
    }

    @JsonProperty("Id")
    public String getId() {
        return Id;
    }

    @JsonProperty("Id")
    public void setId(String id) {
        this.Id = id;
    }

    @JsonProperty("OwnerId")
    public String getOwnerId() {
        return OwnerId;
    }

    @JsonProperty("OwnerId")
    public void setOwnerId(String ownerId) {
        this.OwnerId = ownerId;
    }

    @JsonProperty("IsDeleted")
    public Boolean isIsDeleted() {
        return IsDeleted;
    }

    @JsonProperty("IsDeleted")
    public void setIsDeleted(Boolean isDeleted) {
        this.IsDeleted = isDeleted;
    }

    @JsonProperty("Name")
    public String getName() {
        return Name;
    }

    @JsonProperty("Name")
    public void setName(String name) {
        this.Name = name;
    }

    @JsonProperty("CreatedDate")
    public ZonedDateTime getCreatedDate() {
        return CreatedDate;
    }

    @JsonProperty("CreatedDate")
    public void setCreatedDate(ZonedDateTime createdDate) {
        this.CreatedDate = createdDate;
    }

    @JsonProperty("CreatedById")
    public String getCreatedById() {
        return CreatedById;
    }

    @JsonProperty("CreatedById")
    public void setCreatedById(String createdById) {
        this.CreatedById = createdById;
    }

    @JsonProperty("LastModifiedDate")
    public ZonedDateTime getLastModifiedDate() {
        return LastModifiedDate;
    }

    @JsonProperty("LastModifiedDate")
    public void setLastModifiedDate(ZonedDateTime lastModifiedDate) {
        this.LastModifiedDate = lastModifiedDate;
    }

    @JsonProperty("LastModifiedById")
    public String getLastModifiedById() {
        return LastModifiedById;
    }

    @JsonProperty("LastModifiedById")
    public void setLastModifiedById(String lastModifiedById) {
        this.LastModifiedById = lastModifiedById;
    }

    @JsonProperty("SystemModstamp")
    public ZonedDateTime getSystemModstamp() {
        return SystemModstamp;
    }

    @JsonProperty("SystemModstamp")
    public void setSystemModstamp(ZonedDateTime systemModstamp) {
        this.SystemModstamp = systemModstamp;
    }

    @JsonProperty("LastActivityDate")
    public ZonedDateTime getLastActivityDate() {
        return LastActivityDate;
    }

    @JsonProperty("LastActivityDate")
    public void setLastActivityDate(ZonedDateTime lastActivityDate) {
        this.LastActivityDate = lastActivityDate;
    }

    @JsonProperty("LastViewedDate")
    public ZonedDateTime getLastViewedDate() {
        return LastViewedDate;
    }

    @JsonProperty("LastViewedDate")
    public void setLastViewedDate(ZonedDateTime lastViewedDate) {
        LastViewedDate = lastViewedDate;
    }

    @JsonProperty("LastReferencedDate")
    public ZonedDateTime getLastReferencedDate() {
        return LastReferencedDate;
    }

    @JsonProperty("LastReferencedDate")
    public void setLastReferencedDate(ZonedDateTime lastReferencedDate) {
        LastReferencedDate = lastReferencedDate;
    }

    @JsonIgnore
    public Set<String> getFieldsToNull() {
        return fieldsToNull;
    }

    @JsonIgnore
    public void setFieldsToNull(Set<String> fieldsToNull) {
        this.fieldsToNull = fieldsToNull;
    }

}
//CHECKSTYLE:ON
