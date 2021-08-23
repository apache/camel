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
import com.huaweicloud.sdk.core.SdkResponse;

/**
 * DMS instance object
 */
public class DmsInstance extends SdkResponse {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "name")
    private String name;

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
    private int storageSpace;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "partition_num")
    private int partitionNum;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "used_storage_space")
    private int usedStorageSpace;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "connect_address")
    private String connectAddress;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "port")
    private int port;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "status")
    private String status;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "description")
    private String description;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "instance_id")
    private String instanceId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "resource_spec_code")
    private String resourceSpecCode;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "type")
    private String type;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "charging_mode")
    private int chargingMode;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "vpc_id")
    private String vpcId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "vpc_name")
    private String vpcName;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "created_at")
    private String createdAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "error_code")
    private String errorCode;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "product_id")
    private String productId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "security_group_id")
    private String securityGroupId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "security_group_name")
    private String securityGroupName;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "subnet_id")
    private String subnetId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "subnet_name")
    private String subnetName;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "subnet_cidr")
    private String subnetCidr;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "available_zones")
    private List<String> availableZones;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "user_id")
    private String userId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "user_name")
    private String userName;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "access_user")
    private String accessUser;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "order_id")
    private String orderId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "maintain_begin")
    private String maintainBegin;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "maintain_end")
    private String maintainEnd;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "enable_publicip")
    private boolean enablePublicip;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "publicip_address")
    private String publicipAddress;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "publicip_id")
    private String publicipId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "management_connect_address")
    private String managementConnectAddress;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "ssl_enable")
    private boolean sslEnable;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "enterprise_project_id")
    private String enterpriseProjectId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "is_logical_volume")
    private boolean logicalVolume;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "extend_times")
    private int extendTimes;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "enable_auto_topic")
    private boolean enableAutoTopic;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public int getStorageSpace() {
        return storageSpace;
    }

    public void setStorageSpace(int storageSpace) {
        this.storageSpace = storageSpace;
    }

    public int getPartitionNum() {
        return partitionNum;
    }

    public void setPartitionNum(int partitionNum) {
        this.partitionNum = partitionNum;
    }

    public int getUsedStorageSpace() {
        return usedStorageSpace;
    }

    public void setUsedStorageSpace(int usedStorageSpace) {
        this.usedStorageSpace = usedStorageSpace;
    }

    public String getConnectAddress() {
        return connectAddress;
    }

    public void setConnectAddress(String connectAddress) {
        this.connectAddress = connectAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getResourceSpecCode() {
        return resourceSpecCode;
    }

    public void setResourceSpecCode(String resourceSpecCode) {
        this.resourceSpecCode = resourceSpecCode;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getChargingMode() {
        return chargingMode;
    }

    public void setChargingMode(int chargingMode) {
        this.chargingMode = chargingMode;
    }

    public String getVpcId() {
        return vpcId;
    }

    public void setVpcId(String vpcId) {
        this.vpcId = vpcId;
    }

    public String getVpcName() {
        return vpcName;
    }

    public void setVpcName(String vpcName) {
        this.vpcName = vpcName;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getSecurityGroupId() {
        return securityGroupId;
    }

    public void setSecurityGroupId(String securityGroupId) {
        this.securityGroupId = securityGroupId;
    }

    public String getSecurityGroupName() {
        return securityGroupName;
    }

    public void setSecurityGroupName(String securityGroupName) {
        this.securityGroupName = securityGroupName;
    }

    public String getSubnetId() {
        return subnetId;
    }

    public void setSubnetId(String subnetId) {
        this.subnetId = subnetId;
    }

    public String getSubnetName() {
        return subnetName;
    }

    public void setSubnetName(String subnetName) {
        this.subnetName = subnetName;
    }

    public String getSubnetCidr() {
        return subnetCidr;
    }

    public void setSubnetCidr(String subnetCidr) {
        this.subnetCidr = subnetCidr;
    }

    public DmsInstance addAvailableZone(String availableZone) {
        if (this.availableZones == null) {
            this.availableZones = new ArrayList<>();
        }
        this.availableZones.add(availableZone);
        return this;
    }

    public List<String> getAvailableZones() {
        return availableZones;
    }

    public void setAvailableZones(List<String> availableZones) {
        this.availableZones = availableZones;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getAccessUser() {
        return accessUser;
    }

    public void setAccessUser(String accessUser) {
        this.accessUser = accessUser;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
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

    public boolean isEnablePublicip() {
        return enablePublicip;
    }

    public void setEnablePublicip(boolean enablePublicip) {
        this.enablePublicip = enablePublicip;
    }

    public String getPublicipAddress() {
        return publicipAddress;
    }

    public void setPublicipAddress(String publicipAddress) {
        this.publicipAddress = publicipAddress;
    }

    public String getPublicipId() {
        return publicipId;
    }

    public void setPublicipId(String publicipId) {
        this.publicipId = publicipId;
    }

    public String getManagementConnectAddress() {
        return managementConnectAddress;
    }

    public void setManagementConnectAddress(String managementConnectAddress) {
        this.managementConnectAddress = managementConnectAddress;
    }

    public boolean isSslEnable() {
        return sslEnable;
    }

    public void setSslEnable(boolean sslEnable) {
        this.sslEnable = sslEnable;
    }

    public String getEnterpriseProjectId() {
        return enterpriseProjectId;
    }

    public void setEnterpriseProjectId(String enterpriseProjectId) {
        this.enterpriseProjectId = enterpriseProjectId;
    }

    public boolean isLogicalVolume() {
        return logicalVolume;
    }

    public void setLogicalVolume(boolean logicalVolume) {
        this.logicalVolume = logicalVolume;
    }

    public int getExtendTimes() {
        return extendTimes;
    }

    public void setExtendTimes(int extendTimes) {
        this.extendTimes = extendTimes;
    }

    public boolean isEnableAutoTopic() {
        return enableAutoTopic;
    }

    public void setEnableAutoTopic(boolean enableAutoTopic) {
        this.enableAutoTopic = enableAutoTopic;
    }

    public DmsInstance withName(String name) {
        this.name = name;
        return this;
    }

    public DmsInstance withEngine(String engine) {
        this.engine = engine;
        return this;
    }

    public DmsInstance withEngineVersion(String engineVersion) {
        this.engineVersion = engineVersion;
        return this;
    }

    public DmsInstance withSpecification(String specification) {
        this.specification = specification;
        return this;
    }

    public DmsInstance withStorageSpace(int storageSpace) {
        this.storageSpace = storageSpace;
        return this;
    }

    public DmsInstance withPartitionNum(int partitionNum) {
        this.partitionNum = partitionNum;
        return this;
    }

    public DmsInstance withUsedStorageSpace(int usedStorageSpace) {
        this.usedStorageSpace = usedStorageSpace;
        return this;
    }

    public DmsInstance withConnectAddress(String connectAddress) {
        this.connectAddress = connectAddress;
        return this;
    }

    public DmsInstance withPort(int port) {
        this.port = port;
        return this;
    }

    public DmsInstance withStatus(String status) {
        this.status = status;
        return this;
    }

    public DmsInstance withDescription(String description) {
        this.description = description;
        return this;
    }

    public DmsInstance withInstanceId(String instanceId) {
        this.instanceId = instanceId;
        return this;
    }

    public DmsInstance withResourceSpecCode(String resourceSpecCode) {
        this.resourceSpecCode = resourceSpecCode;
        return this;
    }

    public DmsInstance withType(String type) {
        this.type = type;
        return this;
    }

    public DmsInstance withChargingMode(int chargingMode) {
        this.chargingMode = chargingMode;
        return this;
    }

    public DmsInstance withVpcId(String vpcId) {
        this.vpcId = vpcId;
        return this;
    }

    public DmsInstance withVpcName(String vpcName) {
        this.vpcName = vpcName;
        return this;
    }

    public DmsInstance withCreatedAt(String createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public DmsInstance withErrorCode(String errorCode) {
        this.errorCode = errorCode;
        return this;
    }

    public DmsInstance withProductId(String productId) {
        this.productId = productId;
        return this;
    }

    public DmsInstance withSecurityGroupId(String securityGroupId) {
        this.securityGroupId = securityGroupId;
        return this;
    }

    public DmsInstance withSecurityGroupName(String securityGroupName) {
        this.securityGroupName = securityGroupName;
        return this;
    }

    public DmsInstance withSubnetId(String subnetId) {
        this.subnetId = subnetId;
        return this;
    }

    public DmsInstance withSubnetName(String subnetName) {
        this.subnetName = subnetName;
        return this;
    }

    public DmsInstance withSubnetCidr(String subnetCidr) {
        this.subnetCidr = subnetCidr;
        return this;
    }

    public DmsInstance withAvailableZones(List<String> availableZones) {
        this.availableZones = availableZones;
        return this;
    }

    public DmsInstance withUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public DmsInstance withUserName(String userName) {
        this.userName = userName;
        return this;
    }

    public DmsInstance withAccessUser(String accessUser) {
        this.accessUser = accessUser;
        return this;
    }

    public DmsInstance withOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public DmsInstance withMaintainBegin(String maintainBegin) {
        this.maintainBegin = maintainBegin;
        return this;
    }

    public DmsInstance withMaintainEnd(String maintainEnd) {
        this.maintainEnd = maintainEnd;
        return this;
    }

    public DmsInstance withEnablePublicip(boolean enablePublicip) {
        this.enablePublicip = enablePublicip;
        return this;
    }

    public DmsInstance withPublicipAddress(String publicipAddress) {
        this.publicipAddress = publicipAddress;
        return this;
    }

    public DmsInstance withPublicipId(String publicipId) {
        this.publicipId = publicipId;
        return this;
    }

    public DmsInstance withManagementConnectAddress(String managementConnectAddress) {
        this.managementConnectAddress = managementConnectAddress;
        return this;
    }

    public DmsInstance withSslEnable(boolean sslEnable) {
        this.sslEnable = sslEnable;
        return this;
    }

    public DmsInstance withEnterpriseProjectId(String enterpriseProjectId) {
        this.enterpriseProjectId = enterpriseProjectId;
        return this;
    }

    public DmsInstance withLogicalVolume(boolean logicalVolume) {
        this.logicalVolume = logicalVolume;
        return this;
    }

    public DmsInstance withExtendTimes(int extendTimes) {
        this.extendTimes = extendTimes;
        return this;
    }

    public DmsInstance withEnableAutoTopic(boolean enableAutoTopic) {
        this.enableAutoTopic = enableAutoTopic;
        return this;
    }

    @Override
    public String toString() {
        return "DmsInstance{" +
               "name='" + name + '\'' +
               ", engine='" + engine + '\'' +
               ", engineVersion='" + engineVersion + '\'' +
               ", specification='" + specification + '\'' +
               ", storageSpace=" + storageSpace +
               ", partitionNum=" + partitionNum +
               ", usedStorageSpace=" + usedStorageSpace +
               ", connectAddress='" + connectAddress + '\'' +
               ", port=" + port +
               ", status='" + status + '\'' +
               ", description='" + description + '\'' +
               ", instanceId='" + instanceId + '\'' +
               ", resourceSpecCode='" + resourceSpecCode + '\'' +
               ", type='" + type + '\'' +
               ", chargingMode=" + chargingMode +
               ", vpcId='" + vpcId + '\'' +
               ", vpcName='" + vpcName + '\'' +
               ", createdAt='" + createdAt + '\'' +
               ", errorCode='" + errorCode + '\'' +
               ", productId='" + productId + '\'' +
               ", securityGroupId='" + securityGroupId + '\'' +
               ", securityGroupName='" + securityGroupName + '\'' +
               ", subnetId='" + subnetId + '\'' +
               ", subnetName='" + subnetName + '\'' +
               ", subnetCidr='" + subnetCidr + '\'' +
               ", availableZones=" + availableZones +
               ", userId='" + userId + '\'' +
               ", userName='" + userName + '\'' +
               ", accessUser='" + accessUser + '\'' +
               ", orderId='" + orderId + '\'' +
               ", maintainBegin='" + maintainBegin + '\'' +
               ", maintainEnd='" + maintainEnd + '\'' +
               ", enablePublicip=" + enablePublicip +
               ", publicipAddress='" + publicipAddress + '\'' +
               ", publicipId='" + publicipId + '\'' +
               ", managementConnectAddress='" + managementConnectAddress + '\'' +
               ", sslEnable=" + sslEnable +
               ", enterpriseProjectId='" + enterpriseProjectId + '\'' +
               ", logicalVolume=" + logicalVolume +
               ", extendTimes=" + extendTimes +
               ", enableAutoTopic=" + enableAutoTopic +
               '}';
    }
}
