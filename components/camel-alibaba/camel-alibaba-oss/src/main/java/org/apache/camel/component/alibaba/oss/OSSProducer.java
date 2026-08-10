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

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.BucketSummary;
import com.aliyun.sdk.service.oss2.models.CopyObjectRequest;
import com.aliyun.sdk.service.oss2.models.CopyObjectResult;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.DeleteObjectResult;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectResult;
import com.aliyun.sdk.service.oss2.models.HeadObjectRequest;
import com.aliyun.sdk.service.oss2.models.HeadObjectResult;
import com.aliyun.sdk.service.oss2.models.ListBucketsRequest;
import com.aliyun.sdk.service.oss2.models.ListBucketsResult;
import com.aliyun.sdk.service.oss2.models.ListObjectsRequest;
import com.aliyun.sdk.service.oss2.models.ListObjectsResult;
import com.aliyun.sdk.service.oss2.models.ObjectSummary;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectResult;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import com.google.gson.Gson;
import org.apache.camel.Exchange;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.alibaba.oss.constants.OSSOperations;
import org.apache.camel.component.alibaba.oss.constants.OSSProperties;
import org.apache.camel.component.alibaba.oss.models.ClientConfigurations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OSSProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(OSSProducer.class);

    private final OSSEndpoint endpoint;
    private OSSClient ossClient;
    private Gson gson;

    public OSSProducer(OSSEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        this.gson = new Gson();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        ClientConfigurations clientConfigurations = new ClientConfigurations();

        if (ossClient == null) {
            this.ossClient = endpoint.initClient();
        }

        updateClientConfigs(exchange, clientConfigurations);

        switch (clientConfigurations.getOperation()) {
            case OSSOperations.LIST_BUCKETS:
                listBuckets(exchange);
                break;
            case OSSOperations.LIST_OBJECTS:
                listObjects(exchange, clientConfigurations);
                break;
            case OSSOperations.PUT_OBJECT:
                putObject(exchange, clientConfigurations);
                break;
            case OSSOperations.GET_OBJECT:
                getObject(exchange, clientConfigurations);
                break;
            case OSSOperations.DELETE_OBJECT:
                deleteObject(exchange, clientConfigurations);
                break;
            case OSSOperations.COPY_OBJECT:
                copyObject(exchange, clientConfigurations);
                break;
            case OSSOperations.HEAD_OBJECT:
                headObject(exchange, clientConfigurations);
                break;
            default:
                throw new UnsupportedOperationException(
                        String.format("%s is not a supported operation", clientConfigurations.getOperation()));
        }
    }

    private void putObject(Exchange exchange, ClientConfigurations clientConfigurations) throws Exception {
        Object body = exchange.getMessage().getBody();

        if (ObjectHelper.isEmpty(clientConfigurations.getBucketName())) {
            throw new IllegalArgumentException("Bucket name is mandatory to put objects into bucket");
        }

        if (body instanceof WrappedFile<?> wf) {
            body = wf.getFile();
        }

        if (ObjectHelper.isEmpty(clientConfigurations.getObjectName()) && !(body instanceof File)) {
            throw new IllegalArgumentException("Object name is mandatory when body is not a file");
        }

        PutObjectRequest.Builder requestBuilder = PutObjectRequest.newBuilder()
                .bucket(clientConfigurations.getBucketName());

        if (body instanceof File file) {
            String objectName = ObjectHelper.isEmpty(clientConfigurations.getObjectName())
                    ? file.getName()
                    : clientConfigurations.getObjectName();
            requestBuilder.key(objectName);
            PutObjectResult result = ossClient.putObjectFromFile(requestBuilder.build(), file);
            exchange.getMessage()
                    .setBody(gson.toJson(toPutObjectMap(result, clientConfigurations.getBucketName(), objectName)));
        } else if (body instanceof String stringBody) {
            requestBuilder.key(clientConfigurations.getObjectName())
                    .body(BinaryData.fromString(stringBody));
            PutObjectResult result = ossClient.putObject(requestBuilder.build());
            exchange.getMessage().setBody(gson.toJson(toPutObjectMap(result, clientConfigurations.getBucketName(),
                    clientConfigurations.getObjectName())));
        } else if (body instanceof InputStream inputStream) {
            requestBuilder.key(clientConfigurations.getObjectName())
                    .body(BinaryData.fromStream(inputStream));
            PutObjectResult result = ossClient.putObject(requestBuilder.build());
            exchange.getMessage().setBody(gson.toJson(toPutObjectMap(result, clientConfigurations.getBucketName(),
                    clientConfigurations.getObjectName())));
        } else if (body instanceof byte[] bytes) {
            requestBuilder.key(clientConfigurations.getObjectName())
                    .body(BinaryData.fromBytes(bytes));
            PutObjectResult result = ossClient.putObject(requestBuilder.build());
            exchange.getMessage().setBody(gson.toJson(toPutObjectMap(result, clientConfigurations.getBucketName(),
                    clientConfigurations.getObjectName())));
        } else {
            InputStream is = exchange.getMessage().getMandatoryBody(InputStream.class);
            requestBuilder.key(clientConfigurations.getObjectName())
                    .body(BinaryData.fromStream(is));
            PutObjectResult result = ossClient.putObject(requestBuilder.build());
            exchange.getMessage().setBody(gson.toJson(toPutObjectMap(result, clientConfigurations.getBucketName(),
                    clientConfigurations.getObjectName())));
        }
    }

    private Map<String, Object> toPutObjectMap(PutObjectResult result, String bucketName, String objectName) {
        Map<String, Object> map = new HashMap<>();
        map.put("bucketName", bucketName);
        map.put("objectKey", objectName);
        map.put("eTag", result.eTag());
        map.put("contentMd5", result.contentMd5());
        map.put("versionId", result.versionId());
        map.put("statusCode", result.statusCode());
        return map;
    }

    private void getObject(Exchange exchange, ClientConfigurations clientConfigurations) throws Exception {
        if (ObjectHelper.isEmpty(clientConfigurations.getBucketName())
                || ObjectHelper.isEmpty(clientConfigurations.getObjectName())) {
            throw new IllegalArgumentException("Bucket and object names are mandatory to get objects");
        }

        LOG.debug("Downloading OSS object {} from bucket {}", clientConfigurations.getObjectName(),
                clientConfigurations.getBucketName());

        GetObjectResult result = ossClient.getObject(GetObjectRequest.newBuilder()
                .bucket(clientConfigurations.getBucketName())
                .key(clientConfigurations.getObjectName())
                .build());

        OSSUtils.mapOssObject(exchange, clientConfigurations.getBucketName(), clientConfigurations.getObjectName(), result);
    }

    private void listBuckets(Exchange exchange) {
        ListBucketsResult response = ossClient.listBuckets(ListBucketsRequest.newBuilder().build());
        List<Map<String, Object>> buckets = new ArrayList<>();
        if (response.buckets() != null) {
            for (BucketSummary bucket : response.buckets()) {
                Map<String, Object> bucketMap = new HashMap<>();
                bucketMap.put("name", bucket.name());
                bucketMap.put("region", bucket.region());
                bucketMap.put("storageClass", bucket.storageClass());
                buckets.add(bucketMap);
            }
        }
        exchange.getMessage().setBody(gson.toJson(buckets));
    }

    private void listObjects(Exchange exchange, ClientConfigurations clientConfigurations) {
        if (ObjectHelper.isEmpty(clientConfigurations.getBucketName())) {
            throw new IllegalArgumentException("Bucket name is mandatory to list objects");
        }

        ListObjectsRequest.Builder requestBuilder = ListObjectsRequest.newBuilder()
                .bucket(clientConfigurations.getBucketName());

        if (ObjectHelper.isNotEmpty(clientConfigurations.getPrefix())) {
            requestBuilder.prefix(clientConfigurations.getPrefix());
        }
        if (clientConfigurations.getMaxKeys() != null) {
            requestBuilder.maxKeys(clientConfigurations.getMaxKeys().longValue());
        }

        List<Map<String, Object>> objects = new ArrayList<>();
        ListObjectsResult result;
        ListObjectsRequest request = requestBuilder.build();
        do {
            result = ossClient.listObjects(request);
            if (result.contents() != null) {
                for (ObjectSummary summary : result.contents()) {
                    Map<String, Object> objectMap = new HashMap<>();
                    objectMap.put("bucketName", clientConfigurations.getBucketName());
                    objectMap.put("objectKey", summary.key());
                    objectMap.put("size", summary.size());
                    objectMap.put("eTag", summary.eTag());
                    objectMap.put("lastModified", summary.lastModified() != null ? summary.lastModified().toString() : null);
                    objects.add(objectMap);
                }
            }
            if (Boolean.TRUE.equals(result.isTruncated()) && result.nextMarker() != null) {
                request = request.toBuilder().marker(result.nextMarker()).build();
            } else {
                break;
            }
        } while (Boolean.TRUE.equals(result.isTruncated()));

        exchange.getMessage().setBody(gson.toJson(objects));
    }

    private void deleteObject(Exchange exchange, ClientConfigurations clientConfigurations) {
        if (ObjectHelper.isEmpty(clientConfigurations.getBucketName())
                || ObjectHelper.isEmpty(clientConfigurations.getObjectName())) {
            throw new IllegalArgumentException("Bucket and object names are mandatory to delete objects");
        }

        DeleteObjectResult result = ossClient.deleteObject(DeleteObjectRequest.newBuilder()
                .bucket(clientConfigurations.getBucketName())
                .key(clientConfigurations.getObjectName())
                .build());

        Map<String, Object> map = new HashMap<>();
        map.put("statusCode", result.statusCode());
        map.put("requestId", result.requestId());
        map.put("deleteMarker", result.deleteMarker());
        map.put("versionId", result.versionId());
        exchange.getMessage().setBody(gson.toJson(map));
    }

    private void copyObject(Exchange exchange, ClientConfigurations clientConfigurations) {
        if (ObjectHelper.isEmpty(clientConfigurations.getSourceBucketName())
                || ObjectHelper.isEmpty(clientConfigurations.getSourceObjectName())
                || ObjectHelper.isEmpty(clientConfigurations.getBucketName())
                || ObjectHelper.isEmpty(clientConfigurations.getObjectName())) {
            throw new IllegalArgumentException(
                    "Source bucket, source object, destination bucket and destination object names are mandatory to copy objects");
        }

        CopyObjectResult result = ossClient.copyObject(CopyObjectRequest.newBuilder()
                .sourceBucket(clientConfigurations.getSourceBucketName())
                .sourceKey(clientConfigurations.getSourceObjectName())
                .bucket(clientConfigurations.getBucketName())
                .key(clientConfigurations.getObjectName())
                .build());

        Map<String, Object> map = new HashMap<>();
        map.put("eTag", result.eTag());
        map.put("lastModified", result.lastModified());
        map.put("statusCode", result.statusCode());
        map.put("requestId", result.requestId());
        exchange.getMessage().setBody(gson.toJson(map));
    }

    private void headObject(Exchange exchange, ClientConfigurations clientConfigurations) {
        if (ObjectHelper.isEmpty(clientConfigurations.getBucketName())
                || ObjectHelper.isEmpty(clientConfigurations.getObjectName())) {
            throw new IllegalArgumentException("Bucket and object names are mandatory to head objects");
        }

        HeadObjectResult result = ossClient.headObject(HeadObjectRequest.newBuilder()
                .bucket(clientConfigurations.getBucketName())
                .key(clientConfigurations.getObjectName())
                .build());

        Map<String, Object> map = new HashMap<>();
        map.put("eTag", result.eTag());
        map.put("contentLength", result.contentLength());
        map.put("contentType", result.contentType());
        map.put("contentMd5", result.contentMd5());
        map.put("lastModified", result.lastModified());
        map.put("storageClass", result.storageClass());
        map.put("metadata", result.metadata());
        map.put("statusCode", result.statusCode());
        map.put("requestId", result.requestId());
        exchange.getMessage().setBody(gson.toJson(map));
    }

    private void updateClientConfigs(Exchange exchange, ClientConfigurations clientConfigurations) {
        if (ObjectHelper.isEmpty(exchange.getProperty(OSSProperties.OPERATION))
                && ObjectHelper.isEmpty(endpoint.getOperation())) {
            LOG.error("No operation name given. Cannot proceed with OSS operations.");
            throw new IllegalArgumentException("Operation name not found");
        } else {
            clientConfigurations.setOperation(
                    ObjectHelper.isNotEmpty(exchange.getProperty(OSSProperties.OPERATION))
                            ? (String) exchange.getProperty(OSSProperties.OPERATION)
                            : endpoint.getOperation());
        }

        if (ObjectHelper.isNotEmpty(exchange.getProperty(OSSProperties.BUCKET_NAME))
                || ObjectHelper.isNotEmpty(endpoint.getBucketName())) {
            clientConfigurations.setBucketName(
                    ObjectHelper.isNotEmpty(exchange.getProperty(OSSProperties.BUCKET_NAME))
                            ? (String) exchange.getProperty(OSSProperties.BUCKET_NAME)
                            : endpoint.getBucketName());
        }

        if (ObjectHelper.isNotEmpty(exchange.getProperty(OSSProperties.OBJECT_NAME))
                || ObjectHelper.isNotEmpty(endpoint.getObjectName())) {
            clientConfigurations.setObjectName(
                    ObjectHelper.isNotEmpty(exchange.getProperty(OSSProperties.OBJECT_NAME))
                            ? (String) exchange.getProperty(OSSProperties.OBJECT_NAME)
                            : endpoint.getObjectName());
        }

        if (ObjectHelper.isNotEmpty(exchange.getProperty(OSSProperties.SOURCE_BUCKET_NAME))) {
            clientConfigurations.setSourceBucketName((String) exchange.getProperty(OSSProperties.SOURCE_BUCKET_NAME));
        }

        if (ObjectHelper.isNotEmpty(exchange.getProperty(OSSProperties.SOURCE_OBJECT_NAME))) {
            clientConfigurations.setSourceObjectName((String) exchange.getProperty(OSSProperties.SOURCE_OBJECT_NAME));
        }

        if (ObjectHelper.isNotEmpty(exchange.getProperty(OSSProperties.PREFIX))
                || ObjectHelper.isNotEmpty(endpoint.getPrefix())) {
            clientConfigurations.setPrefix(
                    ObjectHelper.isNotEmpty(exchange.getProperty(OSSProperties.PREFIX))
                            ? (String) exchange.getProperty(OSSProperties.PREFIX)
                            : endpoint.getPrefix());
        }

        if (ObjectHelper.isNotEmpty(exchange.getProperty(OSSProperties.MAX_KEYS))
                || ObjectHelper.isNotEmpty(endpoint.getMaxKeys())) {
            clientConfigurations.setMaxKeys(
                    ObjectHelper.isNotEmpty(exchange.getProperty(OSSProperties.MAX_KEYS))
                            ? (Integer) exchange.getProperty(OSSProperties.MAX_KEYS)
                            : endpoint.getMaxKeys());
        }
    }
}
