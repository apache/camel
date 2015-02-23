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
package org.apache.camel.component.salesforce.api.dto;

import org.codehaus.jackson.annotate.JsonProperty;
import org.joda.time.DateTime;
//CHECKSTYLE:OFF
public class AbstractSObjectBase extends AbstractDTOBase {

    // WARNING: these fields have case sensitive names,
    // the field name MUST match the field name used by Salesforce
    // DO NOT change these field names to camel case!!!
    private Attributes attributes;
    private String Id;
    private String OwnerId;
    private Boolean IsDeleted;
    private String Name;
    private DateTime CreatedDate;
    private String CreatedById;
    private DateTime LastModifiedDate;
    private String LastModifiedById;
    private DateTime SystemModstamp;
    private String LastActivityDate;
    private DateTime LastViewedDate;
    private DateTime LastReferencedDate;

    /**
     * Utility method to clear all system {@link AbstractSObjectBase} fields.
     * <p>Useful when reusing a DTO for a new record, or for update/upsert.</p>
     * <p>This method does not clear {@code Name} to allow updating it, so it must be explicitly set to {@code null} if needed.</p>
     */
    public final void clearBaseFields() {
        attributes = null;
        Id = null;
        OwnerId = null;
        IsDeleted = null;
        CreatedDate = null;
        CreatedById = null;
        LastModifiedDate = null;
        LastModifiedById = null;
        SystemModstamp = null;
        LastActivityDate = null;
    }

    public Attributes getAttributes() {
        return attributes;
    }

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
    public DateTime getCreatedDate() {
        return CreatedDate;
    }

    @JsonProperty("CreatedDate")
    public void setCreatedDate(DateTime createdDate) {
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
    public DateTime getLastModifiedDate() {
        return LastModifiedDate;
    }

    @JsonProperty("LastModifiedDate")
    public void setLastModifiedDate(DateTime lastModifiedDate) {
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
    public DateTime getSystemModstamp() {
        return SystemModstamp;
    }

    @JsonProperty("SystemModstamp")
    public void setSystemModstamp(DateTime systemModstamp) {
        this.SystemModstamp = systemModstamp;
    }

    @JsonProperty("LastActivityDate")
    public String getLastActivityDate() {
        return LastActivityDate;
    }

    @JsonProperty("LastActivityDate")
    public void setLastActivityDate(String lastActivityDate) {
        this.LastActivityDate = lastActivityDate;
    }

    @JsonProperty("LastViewedDate")
    public DateTime getLastViewedDate() {
        return LastViewedDate;
    }

    @JsonProperty("LastViewedDate")
    public void setLastViewedDate(DateTime lastViewedDate) {
        LastViewedDate = lastViewedDate;
    }

    @JsonProperty("LastReferencedDate")
    public DateTime getLastReferencedDate() {
        return LastReferencedDate;
    }

    @JsonProperty("LastReferencedDate")
    public void setLastReferencedDate(DateTime lastReferencedDate) {
        LastReferencedDate = lastReferencedDate;
    }
}
//CHECKSTYLE:ON
