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
package org.apache.camel.component.huaweicloud.frs;

import com.huaweicloud.sdk.frs.v2.FrsClient;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.huaweicloud.common.models.ServiceKeys;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Face Recognition Service (FRS) is an intelligent service that uses computers to process, analyze, and understand facial images based on human facial features.
 */
@UriEndpoint(firstVersion = "3.15.0", scheme = "hwcloud-frs", title = "Huawei Cloud Face Recognition Service",
             syntax = "hwcloud-frs:operation",
             category = { Category.CLOUD, Category.MESSAGING }, producerOnly = true)
public class FrsEndpoint extends DefaultEndpoint {
    @UriPath(
             description = "Name of Face Recognition operation to perform, including faceDetection, faceVerification and faceLiveDetection",
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
              description = "Face Recognition service region. Currently only cn-north-1 and cn-north-4 are supported. This is lower precedence than endpoint based configuration.",
              displayName = "Service region")
    @Metadata(required = true)
    private String region;

    @UriParam(
              description = "Fully qualified Face Recognition service url. Carries higher precedence than region based configuration.",
              displayName = "Service endpoint")
    @Metadata(required = false)
    private String endpoint;

    @UriParam(
              description = "Indicates the Base64 character string converted from the image. The size cannot exceed 10 MB. The image resolution of the narrow sides must be greater than 15 pixels, and that of the wide sides cannot exceed 4096 pixels."
                            + "The supported image formats include JPG, PNG, and BMP. \n"
                            + "Either imageBase64, imageUrl, or imageFilePath is mandatory, and the priority is imageBase64 > imageUrl > imageFilePath.",
              displayName = "imageContent")
    @Metadata(required = false)
    private String imageBase64;

    @UriParam(description = "Indicates the URL of an image. The options are as follows:\n"
                            + "HTTP/HTTPS URLs on the public network\n"
                            + "OBS URLs. To use OBS data, authorization is required, including service authorization, temporary authorization, and anonymous public authorization. For details, see Configuring the Access Permission of OBS. \n"
                            + "Either imageBase64, imageUrl, or imageFilePath is mandatory, and the priority is imageBase64 > imageUrl > imageFilePath.",
              displayName = "imageUrl")
    @Metadata(required = false)
    private String imageUrl;

    @UriParam(description = "Indicates the local image file path, whose size cannot exceed 8 MB. It is recommended that the image size be less than 1 MB."
                            + "Any one of imageBase64, imageUrl and imageFilePath is mandatory, and the priority is imageBase64 > imageUrl > imageFilePath.",
              displayName = "imageFilePath")
    @Metadata(required = false)
    private String imageFilePath;

    @UriParam(
              description = "Indicates the Base64 character string converted from the other image when the operation is faceVerification. The size cannot exceed 10 MB."
                            + " The image resolution of the narrow sides must be greater than 15 pixels, and that of the wide sides cannot exceed 4096 pixels."
                            + "The supported image formats include JPG, PNG, and BMP. \n"
                            + "This parameter must be configured if imageBase64 is set.",
              displayName = "anotherImageBase64")
    @Metadata(required = false)
    private String anotherImageBase64;

    @UriParam(description = "Indicates the URL of the other image when the operation is faceVerification. The options are as follows:\n"
                            + "HTTP/HTTPS URLs on the public network\n"
                            + "OBS URLs. To use OBS data, authorization is required, including service authorization, temporary authorization, and anonymous public authorization. For details, see Configuring the Access Permission of OBS. \n"
                            + "This parameter must be configured if imageUrl is set.",
              displayName = "anotherImageUrl")
    @Metadata(required = false)
    private String anotherImageUrl;

    @UriParam(description = "Indicates the local file path of the other image when the operation is faceVerification, whose size cannot exceed 8 MB. It is recommended that the image size be less than 1 MB."
                            + "This parameter must be configured if imageFilePath is set.",
              displayName = "anotherImageFilePath")
    @Metadata(required = false)
    private String anotherImageFilePath;

    @UriParam(
              description = "Indicates the Base64 character string converted from the video when the operation is faceLiveDetection. Its requirements are as follows: \n"
                            + "The video size after Base64 encoding cannot exceed 8 MB. It is recommended that the video file be compressed to 200 KB to 2 MB on the client. \n"
                            + "The video duration must be 1 to 15 seconds. \n"
                            + "The recommended frame rate is 10 fps to 30 fps. \n"
                            + "The encapsulation format can be MP4, AVI, FLV, WEBM, ASF, or MOV. \n"
                            + "The video encoding format can be H.261, H.263, H.264, HEVC, VC-1, VP8, VP9, or WMV3.",
              displayName = "videoBase64")
    @Metadata(required = false)
    private String videoBase64;

    @UriParam(description = "Indicates the URL of a video when the operation is faceLiveDetection. Currently, only the URL of an OBS bucket on HUAWEI CLOUD is supported and FRS must have the permission to read data in the OBS bucket.\n"
                            + "For details about how to enable the read permission, see Service Authorization. The video requirements are as follows: \n"
                            + "The video size after Base64 encoding cannot exceed 8 MB.\n"
                            + "The video duration must be 1 to 15 seconds.\n"
                            + "The recommended frame rate is 10 fps to 30 fps.\n"
                            + "The encapsulation format can be MP4, AVI, FLV, WEBM, ASF, or MOV.\n"
                            + "The video encoding format can be H.261, H.263, H.264, HEVC, VC-1, VP8, VP9, or WMV3.",
              displayName = "videoUrl")
    @Metadata(required = false)
    private String videoUrl;

    @UriParam(description = "Indicates the local video file path when the operation is faceLiveDetection. The video requirements are as follows:\n"
                            + "The size of a video file cannot exceed 8 MB. It is recommended that the video file be compressed to 200 KB to 2 MB on the client.\n"
                            + "The video duration must be 1 to 15 seconds.\n"
                            + "The recommended frame rate is 10 fps to 30 fps.\n"
                            + "The encapsulation format can be MP4, AVI, FLV, WEBM, ASF, or MOV.\n"
                            + "The video encoding format can be H.261, H.263, H.264, HEVC, VC-1, VP8, VP9, or WMV3.",
              displayName = "videoFilePath")
    @Metadata(required = false)
    private String videoFilePath;

    @UriParam(description = "Indicates the action code sequence list when the operation is faceLiveDetection. Actions are separated by commas (,). Currently, the following actions are supported:\n"
                            + "1: Shake the head to the left.\n"
                            + "2: Shake the head to the right.\n"
                            + "3: Nod the head.\n"
                            + "T4: Mouth movement",
              displayName = "actions")
    @Metadata(required = false)
    private String actions;

    @UriParam(description = "Indicates the string of the action time array when the operation is faceLiveDetection. The length of the array is the same as the number of actions."
                            + "Each item contains the start time and end time of the action in the corresponding sequence. The unit is the milliseconds from the video start time.",
              displayName = "actionTime")
    @Metadata(required = false)
    private String actionTimes;

    private FrsClient frsClient;

    public FrsEndpoint() {
    }

    public FrsEndpoint(String uri, String operation, FrsComponent component) {
        super(uri, component);
        this.operation = operation;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new FrsProducer(this);
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

    public boolean isIgnoreSslVerification() {
        return ignoreSslVerification;
    }

    public void setIgnoreSslVerification(boolean ignoreSslVerification) {
        this.ignoreSslVerification = ignoreSslVerification;
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

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImageFilePath() {
        return imageFilePath;
    }

    public void setImageFilePath(String imageFilePath) {
        this.imageFilePath = imageFilePath;
    }

    public String getAnotherImageBase64() {
        return anotherImageBase64;
    }

    public void setAnotherImageBase64(String anotherImageBase64) {
        this.anotherImageBase64 = anotherImageBase64;
    }

    public String getAnotherImageUrl() {
        return anotherImageUrl;
    }

    public void setAnotherImageUrl(String anotherImageUrl) {
        this.anotherImageUrl = anotherImageUrl;
    }

    public String getAnotherImageFilePath() {
        return anotherImageFilePath;
    }

    public void setAnotherImageFilePath(String anotherImageFilePath) {
        this.anotherImageFilePath = anotherImageFilePath;
    }

    public String getVideoBase64() {
        return videoBase64;
    }

    public void setVideoBase64(String videoBase64) {
        this.videoBase64 = videoBase64;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getVideoFilePath() {
        return videoFilePath;
    }

    public void setVideoFilePath(String videoFilePath) {
        this.videoFilePath = videoFilePath;
    }

    public String getActions() {
        return actions;
    }

    public void setActions(String actions) {
        this.actions = actions;
    }

    public String getActionTimes() {
        return actionTimes;
    }

    public void setActionTimes(String actionTimes) {
        this.actionTimes = actionTimes;
    }

    public FrsClient getFrsClient() {
        return frsClient;
    }

    public void setFrsClient(FrsClient frsClient) {
        this.frsClient = frsClient;
    }

}
