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

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Create instance request body object
 */
public class CreateInstanceRequestBody {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "name")
    private String name;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "description")
    private String description;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "engine")
    private String engine;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "engine_version")
    private String engineVersion;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "specification")
    private String specification;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "storage_space")
    private Integer storageSpace;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "partition_num")
    private Integer partitionNum;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "access_user")
    private String accessUser;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "password")
    private String password;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "vpc_id")
    private String vpcId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "security_group_id")
    private String securityGroupId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "subnet_id")
    private String subnetId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "available_zones")
    private List<String> availableZones;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "product_id")
    private String productId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "kafka_manager_user")
    private String kafkaManagerUser;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "kafka_manager_password")
    private String kafkaManagerPassword;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "maintain_begin")
    private String maintainBegin;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "maintain_end")
    private String maintainEnd;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "enable_publicip")
    private Boolean enablePublicip;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "public_bandwidth")
    private String publicBandwidth;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "publicip_id")
    private String publicipId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "ssl_enable")
    private Boolean sslEnable;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "retention_policy")
    private String retentionPolicy;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "connector_enable")
    private Boolean connectorEnable;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "enable_auto_topic")
    private Boolean enableAutoTopic;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "storage_spec_code")
    private String storageSpecCode;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "enterprise_project_id")
    private String enterpriseProjectId;

    public CreateInstanceRequestBody withName(String name) {
        this.name = name;
        return this;
    }

    public CreateInstanceRequestBody withDescription(String description) {
        this.description = description;
        return this;
    }

    public CreateInstanceRequestBody withEngine(String engine) {
        this.engine = engine;
        return this;
    }

    public CreateInstanceRequestBody withEngineVersion(String engineVersion) {
        this.engineVersion = engineVersion;
        return this;
    }

    public CreateInstanceRequestBody withSpecification(String specification) {
        this.specification = specification;
        return this;
    }

    public CreateInstanceRequestBody withStorageSpace(Integer storageSpace) {
        this.storageSpace = storageSpace;
        return this;
    }

    public CreateInstanceRequestBody withPartitionNum(Integer partitionNum) {
        this.partitionNum = partitionNum;
        return this;
    }

    public CreateInstanceRequestBody withAccessUser(String accessUser) {
        this.accessUser = accessUser;
        return this;
    }

    public CreateInstanceRequestBody withPassword(String password) {
        this.password = password;
        return this;
    }

    public CreateInstanceRequestBody withVpcId(String vpcId) {
        this.vpcId = vpcId;
        return this;
    }

    public CreateInstanceRequestBody withSecurityGroupId(String securityGroupId) {
        this.securityGroupId = securityGroupId;
        return this;
    }

    public CreateInstanceRequestBody withSubnetId(String subnetId) {
        this.subnetId = subnetId;
        return this;
    }

    public CreateInstanceRequestBody withAvailableZones(List<String> availableZones) {
        this.availableZones = availableZones;
        return this;
    }

    public CreateInstanceRequestBody withProductId(String productId) {
        this.productId = productId;
        return this;
    }

    public CreateInstanceRequestBody withKafkaManagerUser(String kafkaManagerUser) {
        this.kafkaManagerUser = kafkaManagerUser;
        return this;
    }

    public CreateInstanceRequestBody withKafkaManagerPassword(String kafkaManagerPassword) {
        this.kafkaManagerPassword = kafkaManagerPassword;
        return this;
    }

    public CreateInstanceRequestBody withMaintainBegin(String maintainBegin) {
        this.maintainBegin = maintainBegin;
        return this;
    }

    public CreateInstanceRequestBody withMaintainEnd(String maintainEnd) {
        this.maintainEnd = maintainEnd;
        return this;
    }

    public CreateInstanceRequestBody withEnablePublicip(Boolean enablePublicip) {
        this.enablePublicip = enablePublicip;
        return this;
    }

    public CreateInstanceRequestBody withPublicBandwidth(String publicBandwidth) {
        this.publicBandwidth = publicBandwidth;
        return this;
    }

    public CreateInstanceRequestBody withPublicipId(String publicipId) {
        this.publicipId = publicipId;
        return this;
    }

    public CreateInstanceRequestBody withSslEnable(Boolean sslEnable) {
        this.sslEnable = sslEnable;
        return this;
    }

    public CreateInstanceRequestBody withRetentionPolicy(String retentionPolicy) {
        this.retentionPolicy = retentionPolicy;
        return this;
    }

    public CreateInstanceRequestBody withConnectorEnable(Boolean connectorEnable) {
        this.connectorEnable = connectorEnable;
        return this;
    }

    public CreateInstanceRequestBody withEnableAutoTopic(Boolean enableAutoTopic) {
        this.enableAutoTopic = enableAutoTopic;
        return this;
    }

    public CreateInstanceRequestBody withStorageSpecCode(String storageSpecCode) {
        this.storageSpecCode = storageSpecCode;
        return this;
    }

    public CreateInstanceRequestBody withEnterpriseProjectId(String enterpriseProjectId) {
        this.enterpriseProjectId = enterpriseProjectId;
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

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getEngineVersion() {
        return engineVersion;
    }

    public void setEngineVersion(String engineVersion) {
        this.engineVersion = engineVersion;
    }

    public String getSpecification() {
        return specification;
    }

    public void setSpecification(String specification) {
        this.specification = specification;
    }

    public Integer getStorageSpace() {
        return storageSpace;
    }

    public void setStorageSpace(Integer storageSpace) {
        this.storageSpace = storageSpace;
    }

    public Integer getPartitionNum() {
        return partitionNum;
    }

    public void setPartitionNum(Integer partitionNum) {
        this.partitionNum = partitionNum;
    }

    public String getAccessUser() {
        return accessUser;
    }

    public void setAccessUser(String accessUser) {
        this.accessUser = accessUser;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getVpcId() {
        return vpcId;
    }

    public void setVpcId(String vpcId) {
        this.vpcId = vpcId;
    }

    public String getSecurityGroupId() {
        return securityGroupId;
    }

    public void setSecurityGroupId(String securityGroupId) {
        this.securityGroupId = securityGroupId;
    }

    public String getSubnetId() {
        return subnetId;
    }

    public void setSubnetId(String subnetId) {
        this.subnetId = subnetId;
    }

    public List<String> getAvailableZones() {
        return availableZones;
    }

    public void setAvailableZones(List<String> availableZones) {
        this.availableZones = availableZones;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getKafkaManagerUser() {
        return kafkaManagerUser;
    }

    public void setKafkaManagerUser(String kafkaManagerUser) {
        this.kafkaManagerUser = kafkaManagerUser;
    }

    public String getKafkaManagerPassword() {
        return kafkaManagerPassword;
    }

    public void setKafkaManagerPassword(String kafkaManagerPassword) {
        this.kafkaManagerPassword = kafkaManagerPassword;
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

    public Boolean getEnablePublicip() {
        return enablePublicip;
    }

    public void setEnablePublicip(Boolean enablePublicip) {
        this.enablePublicip = enablePublicip;
    }

    public String getPublicBandwidth() {
        return publicBandwidth;
    }

    public void setPublicBandwidth(String publicBandwidth) {
        this.publicBandwidth = publicBandwidth;
    }

    public String getPublicipId() {
        return publicipId;
    }

    public void setPublicipId(String publicipId) {
        this.publicipId = publicipId;
    }

    public Boolean getSslEnable() {
        return sslEnable;
    }

    public void setSslEnable(Boolean sslEnable) {
        this.sslEnable = sslEnable;
    }

    public String getRetentionPolicy() {
        return retentionPolicy;
    }

    public void setRetentionPolicy(String retentionPolicy) {
        this.retentionPolicy = retentionPolicy;
    }

    public Boolean getConnectorEnable() {
        return connectorEnable;
    }

    public void setConnectorEnable(Boolean connectorEnable) {
        this.connectorEnable = connectorEnable;
    }

    public Boolean getEnableAutoTopic() {
        return enableAutoTopic;
    }

    public void setEnableAutoTopic(Boolean enableAutoTopic) {
        this.enableAutoTopic = enableAutoTopic;
    }

    public String getStorageSpecCode() {
        return storageSpecCode;
    }

    public void setStorageSpecCode(String storageSpecCode) {
        this.storageSpecCode = storageSpecCode;
    }

    public String getEnterpriseProjectId() {
        return enterpriseProjectId;
    }

    public void setEnterpriseProjectId(String enterpriseProjectId) {
        this.enterpriseProjectId = enterpriseProjectId;
    }

    public CreateInstanceRequestBody addAvailableZone(String availableZone) {
        if (this.availableZones == null) {
            this.availableZones = new ArrayList<>();
        }
        this.availableZones.add(availableZone);
        return this;
    }

    @Override
    public String toString() {
        return "CreateInstanceRequestBody{" +
               "name='" + name + '\'' +
               ", description='" + description + '\'' +
               ", engine='" + engine + '\'' +
               ", engineVersion='" + engineVersion + '\'' +
               ", specification='" + specification + '\'' +
               ", storageSpace=" + storageSpace +
               ", partitionNum=" + partitionNum +
               ", accessUser='" + accessUser + '\'' +
               ", password='" + password + '\'' +
               ", vpcId='" + vpcId + '\'' +
               ", securityGroupId='" + securityGroupId + '\'' +
               ", subnetId='" + subnetId + '\'' +
               ", availableZones=" + availableZones +
               ", productId='" + productId + '\'' +
               ", kafkaManagerUser='" + kafkaManagerUser + '\'' +
               ", kafkaManagerPassword='" + kafkaManagerPassword + '\'' +
               ", maintainBegin='" + maintainBegin + '\'' +
               ", maintainEnd='" + maintainEnd + '\'' +
               ", enablePublicip=" + enablePublicip +
               ", publicBandwidth='" + publicBandwidth + '\'' +
               ", publicipId='" + publicipId + '\'' +
               ", sslEnable=" + sslEnable +
               ", retentionPolicy='" + retentionPolicy + '\'' +
               ", connectorEnable=" + connectorEnable +
               ", enableAutoTopic=" + enableAutoTopic +
               ", storageSpecCode='" + storageSpecCode + '\'' +
               ", enterpriseProjectId='" + enterpriseProjectId + '\'' +
               '}';
    }
}
