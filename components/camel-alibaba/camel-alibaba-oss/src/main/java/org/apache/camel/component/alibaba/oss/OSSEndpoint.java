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
package org.apache.camel.component.alibaba.oss;

import com.aliyun.sdk.service.oss2.OSSClient;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.alibaba.common.models.ServiceKeys;
import org.apache.camel.component.alibaba.oss.constants.OSSHeaders;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;

/**
 * Alibaba Cloud Object Storage Service (OSS) component
 */
@UriEndpoint(firstVersion = "4.23.0", scheme = "alibaba-oss", title = "Alibaba Object Storage Service (OSS)",
             syntax = "alibaba-oss:bucketName",
             category = { Category.CLOUD }, headersClass = OSSHeaders.class)
public class OSSEndpoint extends ScheduledPollEndpoint {

    @UriPath(description = "Name of bucket to perform operation on", displayName = "Bucket Name")
    private String bucketName;

    @UriParam(description = "Operation to be performed", displayName = "Operation", label = "producer",
              enums = "listBuckets,listObjects,putObject,getObject,deleteObject,copyObject,headObject")
    private String operation;

    @UriParam(description = "OSS service region", displayName = "Service region")
    @Metadata(required = true)
    private String region;

    @UriParam(description = "OSS endpoint URL. Carries higher precedence than region based client initialization",
              displayName = "Endpoint url")
    private String endpoint;

    @UriParam(description = "Configuration object for cloud service authentication", displayName = "Service Configuration",
              secret = true, security = "secret", label = "security")
    private ServiceKeys serviceKeys;

    @UriParam(description = "Access key for the cloud user", displayName = "API access key (AK)",
              secret = true, security = "secret", label = "security")
    @Metadata(required = true)
    private String accessKey;

    @UriParam(description = "Secret key for the cloud user", displayName = "API secret key (SK)",
              secret = true, security = "secret", label = "security")
    @Metadata(required = true)
    private String secretKey;

    @UriParam(description = "Name of object to perform operation with", displayName = "Object Name")
    private String objectName;

    @UriParam(description = "The object name prefix used for filtering objects to be listed", displayName = "Prefix",
              label = "consumer")
    private String prefix;

    @UriParam(description = "The maximum number of keys returned when listing objects", displayName = "Max Keys",
              label = "consumer,producer")
    private Integer maxKeys;

    @UriParam(description = "Determines if objects should be deleted after they have been retrieved",
              displayName = "Delete after read", defaultValue = "false", label = "consumer")
    private boolean deleteAfterRead;

    @UriParam(description = "The maximum number of messages to poll at each polling", displayName = "Maximum messages per poll",
              defaultValue = "10", label = "consumer")
    private int maxMessagesPerPoll = 10;

    @UriParam(description = "An autowired OSS client", displayName = "OSS Client", label = "advanced")
    @Metadata(autowired = true)
    private OSSClient ossClient;

    private boolean autowiredOssClient;

    public OSSEndpoint() {
    }

    public OSSEndpoint(String uri, String bucketName, OSSComponent component) {
        super(uri, component);
        this.bucketName = bucketName;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new OSSProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        OSSConsumer consumer = new OSSConsumer(this, processor);
        configureConsumer(consumer);
        consumer.setMaxMessagesPerPoll(maxMessagesPerPoll);
        return consumer;
    }

    /**
     * Initialize and return an OSS client
     */
    public OSSClient initClient() {
        if (ossClient != null) {
            return ossClient;
        }

        ossClient = OSSUtils.createClient(this);
        return ossClient;
    }

    @Override
    protected void doStop() throws Exception {
        if (ossClient != null && !autowiredOssClient) {
            ossClient.close();
            ossClient = null;
        }
        super.doStop();
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

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public Integer getMaxKeys() {
        return maxKeys;
    }

    public void setMaxKeys(Integer maxKeys) {
        this.maxKeys = maxKeys;
    }

    public boolean isDeleteAfterRead() {
        return deleteAfterRead;
    }

    public void setDeleteAfterRead(boolean deleteAfterRead) {
        this.deleteAfterRead = deleteAfterRead;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public OSSClient getOssClient() {
        return ossClient;
    }

    public void setOssClient(OSSClient ossClient) {
        this.ossClient = ossClient;
        this.autowiredOssClient = ossClient != null;
    }
}
