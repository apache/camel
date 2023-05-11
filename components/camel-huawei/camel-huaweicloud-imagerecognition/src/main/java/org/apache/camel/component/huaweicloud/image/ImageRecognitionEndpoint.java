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
package org.apache.camel.component.huaweicloud.image;

import com.huaweicloud.sdk.image.v2.ImageClient;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.huaweicloud.common.models.ServiceKeys;
import org.apache.camel.component.huaweicloud.image.constants.ImageRecognitionConstants;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * To identify objects, scenes, and concepts in images on Huawei Cloud
 */
@UriEndpoint(firstVersion = "3.12.0", scheme = "hwcloud-imagerecognition", title = "Huawei Cloud Image Recognition",
             syntax = "hwcloud-imagerecognition:operation",
             category = { Category.CLOUD, Category.MESSAGING }, producerOnly = true)
public class ImageRecognitionEndpoint extends DefaultEndpoint {
    @UriPath(
             description = "Name of Image Recognition operation to perform, including celebrityRecognition and tagRecognition",
             displayName = "Operation name", label = "producer")
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "Configuration object for cloud service authentication",
              displayName = "Service Configuration", secret = true)
    @Metadata(required = false)
    private ServiceKeys serviceKeys;

    @UriParam(description = "Access key for the cloud user", displayName = "Account access key (AK)", secret = true)
    @Metadata(required = true)
    private String accessKey;

    @UriParam(description = "Secret key for the cloud user", displayName = "Account secret key (SK)", secret = true)
    @Metadata(required = true)
    private String secretKey;

    @UriParam(description = "Cloud project ID", displayName = "Project ID")
    @Metadata(required = true)
    private String projectId;

    @UriParam(description = "Proxy server ip/hostname", displayName = "Proxy server host")
    @Metadata(required = false)
    private String proxyHost;

    @UriParam(description = "Proxy server port", displayName = "Proxy server port")
    @Metadata(required = false)
    private int proxyPort;

    @UriParam(description = "Proxy authentication user", displayName = "Proxy user", secret = true)
    @Metadata(required = false)
    private String proxyUser;

    @UriParam(description = "Proxy authentication password", displayName = "Proxy password", secret = true)
    @Metadata(required = false)
    private String proxyPassword;

    @UriParam(description = "Ignore SSL verification", displayName = "SSL Verification Ignored",
              defaultValue = "false", label = "security")
    @Metadata(required = false)
    private boolean ignoreSslVerification;

    @UriParam(
              description = "Image Recognition service region. Currently only cn-north-1 and cn-north-4 are supported. This is lower precedence than endpoint based configuration.",
              displayName = "Service region")
    @Metadata(required = true)
    private String region;

    @UriParam(
              description = "Fully qualified Image Recognition service url. Carries higher precedence than region based configuration.",
              displayName = "Service endpoint")
    @Metadata(required = false)
    private String endpoint;

    @UriParam(
              description = "Indicates the Base64 character string converted from the image. The size cannot exceed 10 MB. The image resolution of the narrow sides must be greater than 15 pixels, and that of the wide sides cannot exceed 4096 pixels."
                            + "The supported image formats include JPG, PNG, and BMP. \n"
                            + "Configure either this parameter or imageUrl, and this one carries higher precedence than imageUrl.",
              displayName = "Image in Base64")
    @Metadata(required = false)
    private String imageContent;

    @UriParam(description = "Indicates the URL of an image. The options are as follows:\n"
                            + "HTTP/HTTPS URLs on the public network\n"
                            + "OBS URLs. To use OBS data, authorization is required, including service authorization, temporary authorization, and anonymous public authorization. For details, see Configuring the Access Permission of OBS. \n"
                            + "Configure either this parameter or imageContent, and this one carries lower precedence than imageContent.",
              displayName = "Image Url")
    @Metadata(required = false)
    private String imageUrl;

    @UriParam(
              description = "Indicates the language of the returned tags when the operation is tagRecognition, including zh and en.",
              displayName = "Tag Language", defaultValue = "zh")
    @Metadata(required = false)
    private String tagLanguage = ImageRecognitionConstants.TAG_LANGUAGE_ZH;

    @UriParam(description = "Indicates the threshold of confidence.\n"
                            + "When the operation is tagRecognition, this parameter ranges from 0 to 100. Tags whose confidence score is lower than the threshold will not be returned. The default value is 60.\n"
                            + "When the operation is celebrityRecognition, this parameter ranges from 0 to 1. Labels whose confidence score is lower than the threshold will not be returned. The default value is 0.48.",
              displayName = "Threshold of confidence")
    @Metadata(required = false)
    private float threshold = -1;

    @UriParam(description = "Indicates the maximum number of the returned tags when the operation is tagRecognition.",
              displayName = "Tag Limit", defaultValue = "50")
    @Metadata(required = false)
    private int tagLimit = ImageRecognitionConstants.DEFAULT_TAG_LIMIT;

    private ImageClient imageClient;

    public ImageRecognitionEndpoint() {
    }

    public ImageRecognitionEndpoint(String uri, String operation, ImageRecognitionComponent component) {
        super(uri, component);
        this.operation = operation;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new ImageRecognitionProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("consumer endpoint is not supported");
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
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

    public String getImageContent() {
        return imageContent;
    }

    public void setImageContent(String imageContent) {
        this.imageContent = imageContent;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getTagLanguage() {
        return tagLanguage;
    }

    public void setTagLanguage(String tagLanguage) {
        this.tagLanguage = tagLanguage;
    }

    public float getThreshold() {
        return threshold;
    }

    public void setThreshold(float threshold) {
        this.threshold = threshold;
    }

    public int getTagLimit() {
        return tagLimit;
    }

    public void setTagLimit(int tagLimit) {
        this.tagLimit = tagLimit;
    }

    public ImageClient getImageClient() {
        return imageClient;
    }

    public void setImageClient(ImageClient imageClient) {
        this.imageClient = imageClient;
    }

    public boolean isIgnoreSslVerification() {
        return ignoreSslVerification;
    }

    public void setIgnoreSslVerification(boolean ignoreSslVerification) {
        this.ignoreSslVerification = ignoreSslVerification;
    }
}
