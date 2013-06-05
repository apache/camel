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

public class AbstractSObjectBase extends AbstractDTOBase {

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

    /**
     * Utility method to clear all {@link AbstractSObjectBase} fields.
     * <p>Used when reusing a DTO for a new record.</p>
     */
    public final void clearBaseFields() {
        attributes = null;
        Id = null;
        OwnerId = null;
        IsDeleted = null;
        Name = null;
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
        Id = id;
    }

    @JsonProperty("OwnerId")
    public String getOwnerId() {
        return OwnerId;
    }

    @JsonProperty("OwnerId")
    public void setOwnerId(String ownerId) {
        OwnerId = ownerId;
    }

    @JsonProperty("IsDeleted")
    public Boolean isIsDeleted() {
        return IsDeleted;
    }

    @JsonProperty("IsDeleted")
    public void setIsDeleted(Boolean isDeleted) {
        IsDeleted = isDeleted;
    }

    @JsonProperty("Name")
    public String getName() {
        return Name;
    }

    @JsonProperty("Name")
    public void setName(String name) {
        Name = name;
    }

    @JsonProperty("CreatedDate")
    public DateTime getCreatedDate() {
        return CreatedDate;
    }

    @JsonProperty("CreatedDate")
    public void setCreatedDate(DateTime createdDate) {
        CreatedDate = createdDate;
    }

    @JsonProperty("CreatedById")
    public String getCreatedById() {
        return CreatedById;
    }

    @JsonProperty("CreatedById")
    public void setCreatedById(String createdById) {
        CreatedById = createdById;
    }

    @JsonProperty("LastModifiedDate")
    public DateTime getLastModifiedDate() {
        return LastModifiedDate;
    }

    @JsonProperty("LastModifiedDate")
    public void setLastModifiedDate(DateTime lastModifiedDate) {
        LastModifiedDate = lastModifiedDate;
    }

    @JsonProperty("LastModifiedById")
    public String getLastModifiedById() {
        return LastModifiedById;
    }

    @JsonProperty("LastModifiedById")
    public void setLastModifiedById(String lastModifiedById) {
        LastModifiedById = lastModifiedById;
    }

    @JsonProperty("SystemModstamp")
    public DateTime getSystemModstamp() {
        return SystemModstamp;
    }

    @JsonProperty("SystemModstamp")
    public void setSystemModstamp(DateTime systemModstamp) {
        SystemModstamp = systemModstamp;
    }

    @JsonProperty("LastActivityDate")
    public String getLastActivityDate() {
        return LastActivityDate;
    }

    @JsonProperty("LastActivityDate")
    public void setLastActivityDate(String lastActivityDate) {
        LastActivityDate = lastActivityDate;
    }

}
