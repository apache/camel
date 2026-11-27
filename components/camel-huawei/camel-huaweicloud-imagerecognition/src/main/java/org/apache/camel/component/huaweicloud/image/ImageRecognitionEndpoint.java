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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Recognize celebrities and tag the objects, scenes and concepts in images with Huawei Cloud Image Recognition.
 */
@UriEndpoint(firstVersion = "3.12.0", scheme = "hwcloud-imagerecognition", title = "Huawei Cloud Image Recognition",
             syntax = "hwcloud-imagerecognition:operation",
             category = { Category.CLOUD, Category.MESSAGING }, producerOnly = true)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  description = "Not used, not even for the image: the image comes from the imageContent option"
                                                + " or the CamelHwCloudImageContent exchange property as base64, or from the"
                                                + " imageUrl option or the CamelHwCloudImageUrl exchange property as a URL"),
           producerOut = @BodyType(value = { Carrier.LIST, Carrier.POJO },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "celebrityRecognition",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<com.huaweicloud.sdk.image.v2.model.CelebrityRecognitionResultBody>"),
                                           @BodyType.When(option = "operation", value = "tagRecognition",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.huaweicloud.sdk.image.v2.model.ImageTaggingResponseResult") },
                                   description = "The result of the Huawei Cloud SDK: for celebrityRecognition a List with a"
                                                 + " CelebrityRecognitionResultBody per celebrity found, and for tagRecognition"
                                                 + " an ImageTaggingResponseResult with the tags"))
public class ImageRecognitionEndpoint extends DefaultEndpoint {
    @UriPath(description = "The operation that the producer performs: celebrityRecognition to recognize the celebrities in"
                           + " the image, or tagRecognition to return tags for the objects, scenes and concepts in the image.",
             displayName = "Operation name", label = "producer")
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "The access key and secret key as an"
                            + " org.apache.camel.component.huaweicloud.common.models.ServiceKeys. Its accessKey and its"
                            + " secretKey are each used when the option of the same name is not set.",
              displayName = "Service Configuration", security = "secret")
    @Metadata(required = false)
    private ServiceKeys serviceKeys;

    @UriParam(description = "The access key ID (AK) of the Huawei Cloud account, which signs the requests to Image Recognition."
                            + " If not set, the accessKey of the serviceKeys option is used.",
              displayName = "Account access key (AK)", security = "secret")
    @Metadata(required = true)
    private String accessKey;

    @UriParam(description = "The secret access key (SK) of the Huawei Cloud account, which signs the requests to Image"
                            + " Recognition. If not set, the secretKey of the serviceKeys option is used.",
              displayName = "Account secret key (SK)", security = "secret")
    @Metadata(required = true)
    private String secretKey;

    @UriParam(description = "The ID of the Huawei Cloud project that the requests to Image Recognition are made in.",
              displayName = "Project ID")
    @Metadata(required = true)
    private String projectId;

    @UriParam(description = "The host name or IP address of the proxy server for the requests to Image Recognition. If not"
                            + " set, no proxy is used.",
              displayName = "Proxy server host")
    @Metadata(required = false)
    private String proxyHost;

    @UriParam(description = "The port of the proxy server. It is used only when the proxyHost option is set.",
              displayName = "Proxy server port")
    @Metadata(required = false)
    private int proxyPort;

    @UriParam(description = "The user name for the authentication with the proxy server. It is used only when the proxyHost"
                            + " option is set.",
              displayName = "Proxy user", security = "secret")
    @Metadata(required = false)
    private String proxyUser;

    @UriParam(description = "The password for the authentication with the proxy server. It is used only when the proxyHost"
                            + " and proxyUser options are set.",
              displayName = "Proxy password", security = "secret")
    @Metadata(required = false)
    private String proxyPassword;

    @UriParam(description = "Whether to skip the verification of the TLS certificate of Image Recognition. This is unsafe,"
                            + " and meant for testing only.",
              displayName = "SSL Verification Ignored",
              defaultValue = "false", label = "security", security = "insecure:ssl")
    @Metadata(required = false)
    private boolean ignoreSslVerification;

    @UriParam(description = "The region ID of Image Recognition, for example cn-north-4. If the endpoint option is not set,"
                            + " the producer looks up the endpoint of this region in the Huawei Cloud SDK, and fails for a"
                            + " region that the SDK does not know.",
              displayName = "Service region")
    @Metadata(required = true)
    private String region;

    @UriParam(description = "The URL of Image Recognition, for example https://image.cn-north-4.myhuaweicloud.com. If set,"
                            + " the region option is not used.",
              displayName = "Service endpoint")
    @Metadata(required = false)
    private String endpoint;

    @UriParam(description = "The image as a Base64 string, in JPG, PNG or BMP format and at most 10 MB. The"
                            + " CamelHwCloudImageContent exchange property replaces this option. Set this option or imageUrl;"
                            + " if both are set, the producer sends both to Image Recognition.",
              displayName = "Image in Base64")
    @Metadata(required = false)
    private String imageContent;

    @UriParam(description = "The URL of the image: a public HTTP or HTTPS URL, or the URL of an object in Object Storage"
                            + " Service (OBS) that Image Recognition is authorized to read. The CamelHwCloudImageUrl exchange"
                            + " property replaces this option. Set this option or imageContent; if both are set, the producer"
                            + " sends both to Image Recognition.",
              displayName = "Image Url")
    @Metadata(required = false)
    private String imageUrl;

    @UriParam(description = "The language of the tags that tagRecognition returns: zh for Chinese or en for English. The"
                            + " CamelHwCloudImageTagLanguage exchange property replaces this option. The producer rejects"
                            + " any other value.",
              displayName = "Tag Language", defaultValue = "zh")
    @Metadata(required = false)
    private String tagLanguage = ImageRecognitionConstants.TAG_LANGUAGE_ZH;

    @UriParam(description = "The minimum confidence score of a returned result: from 0 to 100 for tagRecognition (60 if not"
                            + " set), and from 0 to 1 for celebrityRecognition (0.48 if not set). The"
                            + " CamelHwCloudImageThreshold exchange property replaces this option. The producer rejects a"
                            + " value outside the range of the operation.",
              displayName = "Threshold of confidence")
    @Metadata(required = false)
    private float threshold = -1;

    @UriParam(description = "The maximum number of tags that tagRecognition returns. The CamelHwCloudImageTagLimit exchange"
                            + " property replaces this option.",
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
