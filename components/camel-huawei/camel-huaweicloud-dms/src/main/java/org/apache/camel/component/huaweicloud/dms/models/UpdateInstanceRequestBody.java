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
package org.apache.camel.component.huaweicloud.dms.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Update instance request body object
 */
public class UpdateInstanceRequestBody {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "name")
    private String name;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "description")
    private String description;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "maintain_begin")
    private String maintainBegin;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "maintain_end")
    private String maintainEnd;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "security_group_id")
    private String securityGroupId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "enable_publicip")
    private Boolean enablePublicip;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "publicip_id")
    private String publicipId;

    public UpdateInstanceRequestBody withName(String name) {
        this.name = name;
        return this;
    }

    public UpdateInstanceRequestBody withDescription(String description) {
        this.description = description;
        return this;
    }

    public UpdateInstanceRequestBody withMaintainBegin(String maintainBegin) {
        this.maintainBegin = maintainBegin;
        return this;
    }

    public UpdateInstanceRequestBody withMaintainEnd(String maintainEnd) {
        this.maintainEnd = maintainEnd;
        return this;
    }

    public UpdateInstanceRequestBody withSecurityGroupId(String securityGroupId) {
        this.securityGroupId = securityGroupId;
        return this;
    }

    public UpdateInstanceRequestBody withEnablePublicip(Boolean enablePublicip) {
        this.enablePublicip = enablePublicip;
        return this;
    }

    public UpdateInstanceRequestBody withPublicipId(String publicipId) {
        this.publicipId = publicipId;
        return this;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMaintainBegin() {
        return maintainBegin;
    }

    public void setMaintainBegin(String maintainBegin) {
        this.maintainBegin = maintainBegin;
    }

    public String getMaintainEnd() {
        return maintainEnd;
    }

    public void setMaintainEnd(String maintainEnd) {
        this.maintainEnd = maintainEnd;
    }

    public String getSecurityGroupId() {
        return securityGroupId;
    }

    public void setSecurityGroupId(String securityGroupId) {
        this.securityGroupId = securityGroupId;
    }

    public Boolean getEnablePublicip() {
        return enablePublicip;
    }

    public void setEnablePublicip(Boolean enablePublicip) {
        this.enablePublicip = enablePublicip;
    }

    public String getPublicipId() {
        return publicipId;
    }

    public void setPublicipId(String publicipId) {
        this.publicipId = publicipId;
    }

    @Override
    public String toString() {
        return "UpdateInstanceRequestBody{" +
               "name='" + name + '\'' +
               ", description='" + description + '\'' +
               ", maintainBegin='" + maintainBegin + '\'' +
               ", maintainEnd='" + maintainEnd + '\'' +
               ", securityGroupId='" + securityGroupId + '\'' +
               ", enablePublicip=" + enablePublicip +
               ", publicipId='" + publicipId + '\'' +
               '}';
    }
}
