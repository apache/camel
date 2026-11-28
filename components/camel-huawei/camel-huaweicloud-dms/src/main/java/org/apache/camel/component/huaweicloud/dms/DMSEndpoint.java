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
package org.apache.camel.component.huaweicloud.dms;

import java.util.Collection;
import java.util.List;

import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.http.HttpConfig;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.huaweicloud.common.models.ServiceKeys;
import org.apache.camel.component.huaweicloud.dms.models.DmsRegion;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Create, update, query, list and delete Kafka and RabbitMQ instances of Huawei Cloud Distributed Message Service
 * (DMS).
 */
@UriEndpoint(firstVersion = "3.12.0", scheme = "hwcloud-dms", title = "Huawei Distributed Message Service (DMS)",
             syntax = "hwcloud-dms:operation",
             category = { Category.CLOUD }, producerOnly = true)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "createInstance",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE,
                                                                 Carrier.MAP, Carrier.POJO, Carrier.NULL }),
                                          @BodyType.When(option = "operation", value = "updateInstance",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE,
                                                                 Carrier.MAP, Carrier.POJO }) },
                                  description = "Not used by deleteInstance, listInstances and queryInstance, which take the"
                                                + " instance id and the engine from the options or exchange properties such as"
                                                + " CamelHwCloudDmsInstanceId. createInstance takes a CreateInstanceRequestBody or"
                                                + " its JSON as text, bytes, a stream, a file or a Map (such as JSON unmarshalled"
                                                + " by an earlier step), and with no body (or an empty one) creates the instance"
                                                + " from the options: another body fails. updateInstance needs an"
                                                + " UpdateInstanceRequestBody or its JSON as text, bytes, a stream, a file or a"
                                                + " Map: another body fails"),
           producerOut = @BodyType(value = Carrier.UNCHANGED,
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "createInstance",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "listInstances",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "queryInstance",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String") },
                                   description = "The JSON of the response as text: the id of the new instance for"
                                                 + " createInstance, the array of the instances for listInstances and the instance"
                                                 + " for queryInstance. Unchanged after deleteInstance and updateInstance, which set"
                                                 + " the CamelHwCloudDmsInstanceDeleted or CamelHwCloudDmsInstanceUpdated exchange"
                                                 + " property to true. The CamelHwCloudDmsOperation exchange property overrides the"
                                                 + " operation"))
public class DMSEndpoint extends DefaultEndpoint {

    @UriPath(description = "The operation that the producer performs: createInstance, deleteInstance, listInstances,"
                           + " queryInstance or updateInstance. The CamelHwCloudDmsOperation exchange property"
                           + " replaces this option.",
             displayName = "Operation", label = "producer")
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "The region ID of DMS, for example cn-north-4. The client uses the endpoint of this region"
                            + " when the endpoint option is not set. Only af-south-1, ap-southeast-1, ap-southeast-2,"
                            + " ap-southeast-3, cn-east-2, cn-east-3, cn-north-1, cn-north-4, cn-south-1 and"
                            + " cn-southwest-2 are known: for another region, set the endpoint option.",
              displayName = "Service region")
    @Metadata(required = true)
    private String region;

    @UriParam(description = "The endpoint URL of DMS, for example https://dms.cn-north-4.myhuaweicloud.com. When set,"
                            + " the client uses it and the region option is not used. Without a scheme, https:// is"
                            + " added.",
              displayName = "Service endpoint")
    @Metadata(required = false)
    private String endpoint;

    @UriParam(description = "The ID of the Huawei Cloud project of the DMS instances. The producer uses it in the path"
                            + " of every request.",
              displayName = "Project ID")
    @Metadata(required = true)
    private String projectId;

    @UriParam(description = "The host name or IP address of the proxy server. The producer uses a proxy only when this"
                            + " option and the proxyPort option are set.",
              displayName = "Proxy server host")
    @Metadata(required = false)
    private String proxyHost;

    @UriParam(description = "The port of the proxy server. The producer uses a proxy only when this option is greater"
                            + " than 0 and the proxyHost option is set.",
              displayName = "Proxy server port")
    @Metadata(required = false)
    private int proxyPort;

    @UriParam(description = "The user name to authenticate with the proxy server. It is used only when the proxyHost"
                            + " and proxyPort options are set.",
              displayName = "Proxy user", security = "secret")
    @Metadata(required = false)
    private String proxyUser;

    @UriParam(description = "The password to authenticate with the proxy server. It is used only when the proxyUser"
                            + " option is set.",
              displayName = "Proxy password", security = "secret")
    @Metadata(required = false)
    private String proxyPassword;

    @UriParam(description = "Whether to skip the verification of the TLS certificate of the server. This makes the"
                            + " connection insecure: use it only for testing.",
              displayName = "SSL Verification Ignored", defaultValue = "false",
              label = "security", security = "insecure:ssl")
    @Metadata(required = false)
    private boolean ignoreSslVerification;

    @UriParam(description = "The AK/SK pair as an org.apache.camel.component.huaweicloud.common.models.ServiceKeys."
                            + " When set, its accessKey and its secretKey are used, and the accessKey and secretKey"
                            + " options are not.",
              displayName = "Service Configuration",
              security = "secret")
    @Metadata(required = false)
    private ServiceKeys serviceKeys;

    @UriParam(description = "The access key ID (AK) of the Huawei Cloud credentials. It is not used when the"
                            + " serviceKeys option is set.",
              displayName = "API access key (AK)", security = "secret")
    @Metadata(required = true)
    private String accessKey;

    @UriParam(description = "The secret access key (SK) of the Huawei Cloud credentials. It is not used when the"
                            + " serviceKeys option is set.",
              displayName = "API secret key (SK)", security = "secret")
    @Metadata(required = true)
    private String secretKey;

    @UriParam(description = "The message engine: kafka or rabbitmq. It is required for createInstance from the options,"
                            + " and for listInstances it lists only the instances of this engine, or all instances if"
                            + " not set. The CamelHwCloudDmsEngine exchange property replaces this option.",
              displayName = "Engine type",
              enums = "kafka,rabbitmq")
    @Metadata(required = false)
    private String engine;

    @UriParam(description = "The ID of the instance, required for deleteInstance, queryInstance and updateInstance. The"
                            + " CamelHwCloudDmsInstanceId exchange property replaces this option.",
              displayName = "Instance id")
    @Metadata(required = false)
    private String instanceId;

    @UriParam(description = "The name of the new instance, required for createInstance from the options. updateInstance"
                            + " takes the name from the body only. The CamelHwCloudDmsName exchange property replaces"
                            + " this option.",
              displayName = "Name")
    @Metadata(required = false)
    private String name;

    @UriParam(description = "The version of the message engine of the new instance, required for createInstance from"
                            + " the options. The CamelHwCloudDmsEngineVersion exchange property replaces this option.",
              displayName = "Engine version")
    @Metadata(required = false)
    private String engineVersion;

    @UriParam(description = "The baseline bandwidth of a new Kafka instance, for example 100MB. It is required for"
                            + " createInstance of a Kafka instance from the options. The CamelHwCloudDmsSpecification"
                            + " exchange property replaces this option.",
              displayName = "Engine version")
    @Metadata(required = false)
    private String specification;

    @UriParam(description = "The message storage space of the new instance in GB. It must be greater than 0 for"
                            + " createInstance from the options. The CamelHwCloudDmsStorageSpace exchange property"
                            + " replaces this option.",
              displayName = "Storage space")
    @Metadata(required = false)
    private int storageSpace;

    @UriParam(description = "The maximum number of partitions of a new Kafka instance. It must be greater than 0 for"
                            + " createInstance of a Kafka instance from the options. The CamelHwCloudDmsPartitionNum"
                            + " exchange property replaces this option.",
              displayName = "Partition Number")
    @Metadata(required = false)
    private int partitionNum;

    @UriParam(description = "The user name of a new RabbitMQ instance, required for createInstance of a RabbitMQ"
                            + " instance from the options. The CamelHwCloudDmsAccessUser exchange property replaces"
                            + " this option.",
              displayName = "Access user")
    @Metadata(required = false)
    private String accessUser;

    @UriParam(description = "The password of the accessUser of a new RabbitMQ instance, required for createInstance of"
                            + " a RabbitMQ instance from the options. The CamelHwCloudDmsPassword exchange property"
                            + " replaces this option.",
              displayName = "Password", security = "secret")
    @Metadata(required = false)
    private String password;

    @UriParam(description = "The ID of the VPC of the new instance, required for createInstance from the options. The"
                            + " CamelHwCloudDmsVpcId exchange property replaces this option.",
              displayName = "VPC ID")
    @Metadata(required = false)
    private String vpcId;

    @UriParam(description = "The ID of the security group of the new instance, required for createInstance from the"
                            + " options. The CamelHwCloudDmsSecurityGroupId exchange property replaces this option.",
              displayName = "Security group ID")
    @Metadata(required = false)
    private String securityGroupId;

    @UriParam(description = "The ID of the subnet of the new instance, required for createInstance from the options."
                            + " The CamelHwCloudDmsSubnetId exchange property replaces this option.",
              displayName = "Subnet ID")
    @Metadata(required = false)
    private String subnetId;

    @UriParam(description = "The IDs of the availability zones of the new instance, separated by commas, required for"
                            + " createInstance from the options. The CamelHwCloudDmsAvailableZones exchange property"
                            + " replaces this option.",
              displayName = "Available zones")
    @Metadata(required = false)
    private String availableZones;

    @UriParam(description = "The ID of the product (the instance flavor) of the new instance, required for"
                            + " createInstance from the options. The CamelHwCloudDmsProductId exchange property"
                            + " replaces this option.",
              displayName = "Product ID")
    @Metadata(required = false)
    private String productId;

    @UriParam(description = "The user name for logging in to Kafka Manager of a new Kafka instance, required for"
                            + " createInstance of a Kafka instance from the options. The"
                            + " CamelHwCloudDmsKafkaManagerUser exchange property replaces this option.",
              displayName = "Kafka manager username")
    @Metadata(required = false)
    private String kafkaManagerUser;

    @UriParam(description = "The password for logging in to Kafka Manager of a new Kafka instance, required for"
                            + " createInstance of a Kafka instance from the options. The"
                            + " CamelHwCloudDmsKafkaManagerPassword exchange property replaces this option.",
              displayName = "Kafka manager password", security = "secret")
    @Metadata(required = false)
    private String kafkaManagerPassword;

    @UriParam(description = "The storage I/O specification of the new instance, for example dms.physical.storage.high."
                            + " It is required for createInstance from the options. The CamelHwCloudDmsStorageSpecCode"
                            + " exchange property replaces this option.",
              displayName = "Storage specification code")
    @Metadata(required = false)
    private String storageSpecCode;

    private DmsClient dmsClient;

    public DMSEndpoint() {
    }

    public DMSEndpoint(String uri, String operation, DMSComponent component) {
        super(uri, component);
        this.operation = operation;
    }

    public Producer createProducer() throws Exception {
        return new DMSProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyUser() {
        return proxyUser;
    }

    public void setProxyUser(String proxyUser) {
        this.proxyUser = proxyUser;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    public boolean isIgnoreSslVerification() {
        return ignoreSslVerification;
    }

    public void setIgnoreSslVerification(boolean ignoreSslVerification) {
        this.ignoreSslVerification = ignoreSslVerification;
    }

    public ServiceKeys getServiceKeys() {
        return serviceKeys;
    }

    public void setServiceKeys(ServiceKeys serviceKeys) {
        this.serviceKeys = serviceKeys;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getAvailableZones() {
        return availableZones;
    }

    public Collection<String> getAvailableZonesAsList() {
        if (availableZones != null) {
            return List.of(availableZones.split(","));
        } else {
            return null;
        }
    }

    public void setAvailableZones(String availableZones) {
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

    public String getStorageSpecCode() {
        return storageSpecCode;
    }

    public void setStorageSpecCode(String storageSpecCode) {
        this.storageSpecCode = storageSpecCode;
    }

    public DmsClient getDmsClient() {
        return dmsClient;
    }

    public void setDmsClient(DmsClient dmsClient) {
        this.dmsClient = dmsClient;
    }

    public DmsClient initClient() {
        if (dmsClient != null) {
            return dmsClient;
        }

        // check for mandatory AK/SK in ServiceKeys object or in endpoint
        if (ObjectHelper.isEmpty(getServiceKeys()) && ObjectHelper.isEmpty(getAccessKey())) {
            throw new IllegalArgumentException("Authentication parameter 'access key (AK)' not found");
        }
        if (ObjectHelper.isEmpty(getServiceKeys()) && ObjectHelper.isEmpty(getSecretKey())) {
            throw new IllegalArgumentException("Authentication parameter 'secret key (SK)' not found");
        }

        // setup AK/SK credential information. AK/SK provided through ServiceKeys overrides the AK/SK passed through the endpoint
        BasicCredentials auth = new BasicCredentials()
                .withAk(getServiceKeys() != null
                        ? getServiceKeys().getAccessKey()
                        : getAccessKey())
                .withSk(getServiceKeys() != null
                        ? getServiceKeys().getSecretKey()
                        : getSecretKey())
                .withProjectId(getProjectId());

        // setup http information (including proxy information if provided)
        HttpConfig httpConfig = HttpConfig.getDefaultHttpConfig();
        httpConfig.withIgnoreSSLVerification(isIgnoreSslVerification());
        if (ObjectHelper.isNotEmpty(getProxyHost())
                && getProxyPort() > 0) {
            httpConfig.withProxyHost(getProxyHost())
                    .withProxyPort(getProxyPort());

            if (ObjectHelper.isNotEmpty(getProxyUser())) {
                httpConfig.withProxyUsername(getProxyUser());
                if (ObjectHelper.isNotEmpty(getProxyPassword())) {
                    httpConfig.withProxyPassword(getProxyPassword());
                }
            }
        }

        // Build DmsClient with mandatory region parameter.
        if (ObjectHelper.isNotEmpty(getEndpoint())) {
            return DmsClient.newBuilder()
                    .withCredential(auth)
                    .withHttpConfig(httpConfig)
                    .withEndpoint(getEndpoint())
                    .build();
        } else {
            return DmsClient.newBuilder()
                    .withCredential(auth)
                    .withHttpConfig(httpConfig)
                    .withRegion(DmsRegion.valueOf(getRegion()))
                    .build();
        }
    }
}
