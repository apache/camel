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
package org.apache.camel.component.minio;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class MinioConfiguration implements Cloneable {

    @UriParam(defaultValue = "false")
    boolean secure;
    private String bucketName;
    @UriParam
    private String accessKey;
    @UriParam
    private String secretKey;
    @UriParam(label = "consumer")
    private String fileName;
    @UriParam(label = "consumer")
    private String prefix;
    @UriParam(label = "producer")
    private String region;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean deleteAfterRead = true;
    @UriParam(label = "producer")
    private boolean deleteAfterWrite;
    @UriParam(label = "producer")
    private boolean multiPartUpload;
    @UriParam(label = "producer", defaultValue = "" + 25 * 1024 * 1024)
    private long partSize = 25 * 1024 * 1024;
    @UriParam
    private String policy;
    @UriParam(label = "producer")
    private String storageClass;
    @UriParam(label = "producer")
    private String serverSideEncryption;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean includeBody = true;
    @UriParam
    private boolean pathStyleAccess;
    @UriParam(label = "producer", enums = "copyObject,deleteBucket,listBuckets")
    private MinioOperations operation;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean autocloseBody = true;

    /**
     * Some description of this option(isSecure), and what it does
     */
    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    /**
     * Some description of this option(getAccessKey), and what it does
     */
    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    /**
     * Some description of this option(getSecretKey), and what it does
     */
    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * Some description of this option(getFileName), and what it does
     */
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * Some description of this option(getPrefix), and what it does
     */
    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Some description of this option(getRegion), and what it does
     */
    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    /**
     * Some description of this option(isDeleteAfterRead), and what it does
     */
    public boolean isDeleteAfterRead() {
        return deleteAfterRead;
    }

    public void setDeleteAfterRead(boolean deleteAfterRead) {
        this.deleteAfterRead = deleteAfterRead;
    }

    /**
     * Some description of this option(isDeleteAfterWrite), and what it does
     */
    public boolean isDeleteAfterWrite() {
        return deleteAfterWrite;
    }

    public void setDeleteAfterWrite(boolean deleteAfterWrite) {
        this.deleteAfterWrite = deleteAfterWrite;
    }

    /**
     * Some description of this option(isMultiPartUpload), and what it does
     */
    public boolean isMultiPartUpload() {
        return multiPartUpload;
    }

    public void setMultiPartUpload(boolean multiPartUpload) {
        this.multiPartUpload = multiPartUpload;
    }

    /**
     * Some description of this option(getPartSize), and what it does
     */
    public long getPartSize() {
        return partSize;
    }

    public void setPartSize(long partSize) {
        this.partSize = partSize;
    }

    /**
     * Some description of this option(getPolicy), and what it does
     */
    public String getPolicy() {
        return policy;
    }

    public void setPolicy(String policy) {
        this.policy = policy;
    }

    /**
     * Some description of this option(getStorageClass), and what it does
     */
    public String getStorageClass() {
        return storageClass;
    }

    public void setStorageClass(String storageClass) {
        this.storageClass = storageClass;
    }

    /**
     * Some description of this option(getServerSideEncryption), and what it does
     */
    public String getServerSideEncryption() {
        return serverSideEncryption;
    }

    public void setServerSideEncryption(String serverSideEncryption) {
        this.serverSideEncryption = serverSideEncryption;
    }

    /**
     * Some description of this option(isIncludeBody), and what it does
     */
    public boolean isIncludeBody() {
        return includeBody;
    }

    public void setIncludeBody(boolean includeBody) {
        this.includeBody = includeBody;
    }

    /**
     * Some description of this option(isPathStyleAccess), and what it does
     */
    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    public void setPathStyleAccess(boolean pathStyleAccess) {
        this.pathStyleAccess = pathStyleAccess;
    }

    /**
     * Some description of this option(getOperation), and what it does
     */
    public MinioOperations getOperation() {
        return operation;
    }

    public void setOperation(MinioOperations operation) {
        this.operation = operation;
    }

    /**
     * Some description of this option(isAutocloseBody), and what it does
     */
    public boolean isAutocloseBody() {
        return autocloseBody;
    }

    public void setAutocloseBody(boolean autocloseBody) {
        this.autocloseBody = autocloseBody;
    }
}
