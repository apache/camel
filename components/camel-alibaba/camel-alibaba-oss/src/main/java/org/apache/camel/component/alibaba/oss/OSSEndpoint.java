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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;

/**
 * Store and retrieve objects in Alibaba Cloud Object Storage Service (OSS).
 */
@UriEndpoint(firstVersion = "4.23.0", scheme = "alibaba-oss", title = "Alibaba Object Storage Service (OSS)",
             syntax = "alibaba-oss:bucketName",
             category = { Category.CLOUD }, headersClass = OSSHeaders.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.BYTES, javaType = "byte[]",
                                   byOption = @BodyType.When(option = "includeBody", value = "false",
                                                             carriers = Carrier.STREAM),
                                   description = "The content of the object as a byte array, one message per object, with"
                                                 + " its bucket, key and metadata in headers. With includeBody=false the"
                                                 + " stream of the content of the object, which is closed when the exchange"
                                                 + " is done"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = @BodyType.When(option = "operation", value = "putObject",
                                                            carriers = {
                                                                    Carrier.TEXT, Carrier.BYTES, Carrier.STREAM,
                                                                    Carrier.FILE }),
                                  description = "The content to upload for putObject: text, bytes, a stream or a file (a"
                                                + " file without objectName is stored under its file name). A Map, a List"
                                                + " or a POJO fails: marshal it first. The other operations do not read"
                                                + " the body, they take their input from options and headers"),
           producerOut = @BodyType(value = Carrier.MAP,
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "getObject", carriers = Carrier.BYTES,
                                                          javaType = "byte[]"),
                                           @BodyType.When(option = "operation", value = "listObjects", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<java.util.Map<String, Object>>"),
                                           @BodyType.When(option = "operation", value = "listBuckets", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<java.util.Map<String, Object>>") },
                                   description = "A Map of the result for putObject, deleteObject, copyObject and"
                                                 + " headObject, such as the eTag and the status code. getObject gives the"
                                                 + " content of the object as a byte array with its metadata in headers,"
                                                 + " listObjects and listBuckets a List of a Map per object or bucket"))
public class OSSEndpoint extends ScheduledPollEndpoint {

    @UriPath(description = "The name of the bucket, or of the destination bucket for copyObject. The CamelAlibabaOssBucketName"
                           + " exchange property replaces this option for the producer, and a header of the same name replaces"
                           + " both.",
             displayName = "Bucket Name")
    private String bucketName;

    @UriParam(description = "The operation that the producer performs. The CamelAlibabaOssOperation exchange property replaces"
                            + " this option, and a header of the same name replaces both.",
              displayName = "Operation", label = "producer",
              enums = "listBuckets,listObjects,putObject,getObject,deleteObject,copyObject,headObject")
    private String operation;

    @UriParam(description = "The region ID of Object Storage Service, for example cn-hangzhou. The SDK uses it to sign"
                            + " requests, and to build the endpoint when the endpoint option is not set.",
              displayName = "Service region")
    @Metadata(required = true)
    private String region;

    @UriParam(description = "The endpoint of Object Storage Service, for example oss-cn-hangzhou.aliyuncs.com. If not set, the"
                            + " SDK builds it from the region option.",
              displayName = "Endpoint url")
    private String endpoint;

    @UriParam(description = "The AccessKey pair as an org.apache.camel.component.alibaba.common.models.ServiceKeys. Its"
                            + " accessKey and its secretKey are each used when the option of the same name is not set.",
              displayName = "Service Configuration",
              security = "secret", label = "security")
    private ServiceKeys serviceKeys;

    @UriParam(description = "The AccessKey ID of the AccessKey pair for Object Storage Service. If not set, the accessKey of"
                            + " the serviceKeys option is used.",
              displayName = "API access key (AK)", security = "secret",
              label = "security")
    @Metadata(required = true)
    private String accessKey;

    @UriParam(description = "The AccessKey secret of the AccessKey pair for Object Storage Service. If not set, the secretKey"
                            + " of the serviceKeys option is used.",
              displayName = "API secret key (SK)", security = "secret",
              label = "security")
    @Metadata(required = true)
    private String secretKey;

    @UriParam(description = "The name of the object for the producer, or of the destination object for copyObject. The"
                            + " CamelAlibabaOssObjectName exchange property replaces this option, and a header of the same name"
                            + " replaces both.",
              displayName = "Object Name")
    private String objectName;

    @UriParam(description = "The prefix of the names of the objects to list, for the consumer and for listObjects. The"
                            + " CamelAlibabaOssPrefix exchange property replaces this option for the producer, and a header of"
                            + " the same name replaces both.",
              displayName = "Prefix",
              label = "consumer")
    private String prefix;

    @UriParam(description = "The maximum number of objects that the producer returns for listObjects, or all of them if no"
                            + " value is set. Object Storage Service accepts a value of at most 1000. The"
                            + " CamelAlibabaOssMaxKeys exchange property replaces this option, and a header of the same name"
                            + " replaces both.",
              displayName = "Max Keys",
              label = "consumer,producer")
    private Integer maxKeys;

    @UriParam(description = "Whether to delete the object from the bucket after it is processed successfully. If false, the"
                            + " consumer consumes the object again in a later poll.",
              displayName = "Delete after read", defaultValue = "false", label = "consumer")
    private boolean deleteAfterRead;

    @UriParam(description = "The maximum number of objects that the consumer lists in one poll. Object Storage Service accepts"
                            + " a value of at most 1000. The consumer treats a value of 0 or less as 10.",
              displayName = "Maximum messages per poll",
              defaultValue = "10", label = "consumer")
    private int maxMessagesPerPoll = 10;

    @UriParam(description = "If true, the content of each consumed object is read into a byte array, which is the body."
                            + " If false, the body is the stream of the content of the object, which is closed when the"
                            + " exchange is done. For large objects, also disable stream caching for the route, or let"
                            + " stream caching spool to disk, so that an object is not read into memory",
              displayName = "Include Body", defaultValue = "true", label = "consumer")
    private boolean includeBody = true;

    @UriParam(description = "To use a custom com.aliyun.sdk.service.oss2.OSSClient. When this option is set or autowired, the"
                            + " region, endpoint, accessKey, secretKey, and serviceKeys options are not used. The Camel"
                            + " endpoint does not close a client that is set or autowired.",
              displayName = "OSS Client", label = "advanced")
    @Metadata(autowired = true)
    private volatile OSSClient ossClient;

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
     * Returns the client that the producer and the consumer send their requests with. If no client is set or autowired,
     * the first call creates it from the options of this endpoint, while it holds the lock of this endpoint: the calls
     * made at the same time wait for this creation, and the calls made once the client exists do not take the lock. A
     * failed creation keeps no client, so each call that waited for it, and each later call, tries again.
     */
    public OSSClient initClient() {
        OSSClient answer = ossClient;
        if (answer == null) {
            lock.lock();
            try {
                answer = ossClient;
                if (answer == null) {
                    answer = createClient();
                    ossClient = answer;
                }
            } finally {
                lock.unlock();
            }
        }
        return answer;
    }

    OSSClient createClient() {
        return OSSUtils.createClient(this);
    }

    @Override
    protected void doStop() throws Exception {
        lock.lock();
        try {
            if (ossClient != null && !autowiredOssClient) {
                ossClient.close();
                ossClient = null;
            }
        } finally {
            lock.unlock();
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

    public boolean isIncludeBody() {
        return includeBody;
    }

    public void setIncludeBody(boolean includeBody) {
        this.includeBody = includeBody;
    }

    public OSSClient getOssClient() {
        return ossClient;
    }

    public void setOssClient(OSSClient ossClient) {
        lock.lock();
        try {
            this.ossClient = ossClient;
            this.autowiredOssClient = ossClient != null;
        } finally {
            lock.unlock();
        }
    }
}
