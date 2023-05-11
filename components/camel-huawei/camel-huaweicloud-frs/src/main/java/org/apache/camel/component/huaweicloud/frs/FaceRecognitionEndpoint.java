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
 * Face Recognition Service (FRS) is an intelligent service that uses computers to process, analyze, and understand
 * facial images based on human facial features.
 */
@UriEndpoint(firstVersion = "3.15.0", scheme = "hwcloud-frs", title = "Huawei Cloud Face Recognition Service (FRS)",
             syntax = "hwcloud-frs:operation",
             category = { Category.CLOUD, Category.MESSAGING }, producerOnly = true)
public class FaceRecognitionEndpoint extends DefaultEndpoint {
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
              description = "This param can be used when operation is faceDetection or faceVerification, indicating the Base64 character string converted from an image.\n"
                            + "Any one of imageBase64, imageUrl and imageFilePath needs to be set, and the priority is imageBase64 > imageUrl > imageFilePath.\n"
                            + "The Image size cannot exceed 10 MB. The image resolution of the narrow sides must be greater than 15 pixels, and that of the wide sides cannot exceed 4096 pixels.\n"
                            + "The supported image formats include JPG, PNG, and BMP. \n",
              displayName = "Image in Base64")
    @Metadata(required = false)
    private String imageBase64;

    @UriParam(description = "This param can be used when operation is faceDetection or faceVerification, indicating the URL of an image.\n"
                            + "Any one of imageBase64, imageUrl and imageFilePath needs to be set, and the priority is imageBase64 > imageUrl > imageFilePath.\n"
                            + "The options are as follows:\n"
                            + "1.HTTP/HTTPS URLs on the public network\n"
                            + "2.OBS URLs. To use OBS data, authorization is required, including service authorization, temporary authorization, and anonymous public authorization. For details, see Configuring the Access Permission of OBS. \n",
              displayName = "Image Url")
    @Metadata(required = false)
    private String imageUrl;

    @UriParam(description = "This param can be used when operation is faceDetection or faceVerification, indicating the local image file path.\n"
                            + "Any one of imageBase64, imageUrl and imageFilePath needs to be set, and the priority is imageBase64 > imageUrl > imageFilePath.\n"
                            + "Image size cannot exceed 8 MB, and it is recommended that the image size be less than 1 MB.",
              displayName = "Image File Path")
    @Metadata(required = false)
    private String imageFilePath;

    @UriParam(
              description = "This param can be used when operation is faceVerification, indicating the Base64 character string converted from the other image.\n"
                            + "It needs to be configured if imageBase64 is set.\n"
                            + "The image size cannot exceed 10 MB. The image resolution of the narrow sides must be greater than 15 pixels, and that of the wide sides cannot exceed 4096 pixels.\n"
                            + "The supported image formats include JPG, PNG, and BMP.",
              displayName = "Another Image in Base64")
    @Metadata(required = false)
    private String anotherImageBase64;

    @UriParam(description = "This param can be used when operation is faceVerification, indicating the URL of the other image.\n"
                            + "It needs to be configured if imageUrl is set.\n"
                            + "The options are as follows:\n"
                            + "1.HTTP/HTTPS URLs on the public network\n"
                            + "2.OBS URLs. To use OBS data, authorization is required, including service authorization, temporary authorization, and anonymous public authorization. For details, see Configuring the Access Permission of OBS. \n",
              displayName = "Another Image Url")
    @Metadata(required = false)
    private String anotherImageUrl;

    @UriParam(description = "This param can be used when operation is faceVerification, indicating the local file path of the other image.\n"
                            + "It needs to be configured if imageFilePath is set.\n"
                            + "Image size cannot exceed 8 MB, and it is recommended that the image size be less than 1 MB.",
              displayName = "Another Image File Path")
    @Metadata(required = false)
    private String anotherImageFilePath;

    @UriParam(
              description = "This param can be used when operation is faceLiveDetection, indicating the Base64 character string converted from a video.\n"
                            + "Any one of videoBase64, videoUrl and videoFilePath needs to be set, and the priority is videoBase64 > videoUrl > videoFilePath.\n"
                            + "Requirements are as follows: \n"
                            + "1.The video size after Base64 encoding cannot exceed 8 MB. It is recommended that the video file be compressed to 200 KB to 2 MB on the client. \n"
                            + "2.The video duration must be 1 to 15 seconds. \n"
                            + "3.The recommended frame rate is 10 fps to 30 fps. \n"
                            + "4.The encapsulation format can be MP4, AVI, FLV, WEBM, ASF, or MOV. \n"
                            + "5.The video encoding format can be H.261, H.263, H.264, HEVC, VC-1, VP8, VP9, or WMV3.",
              displayName = "Video in Base64")
    @Metadata(required = false)
    private String videoBase64;

    @UriParam(description = "This param can be used when operation is faceLiveDetection, indicating the URL of a video.\n"
                            + "Any one of videoBase64, videoUrl and videoFilePath needs to be set, and the priority is videoBase64 > videoUrl > videoFilePath.\n"
                            + "Currently, only the URL of an OBS bucket on HUAWEI CLOUD is supported and FRS must have the permission to read data in the OBS bucket. For details about how to enable the read permission, see Service Authorization.\n"
                            + "The video requirements are as follows: \n"
                            + "1.The video size after Base64 encoding cannot exceed 8 MB.\n"
                            + "2.The video duration must be 1 to 15 seconds.\n"
                            + "3.The recommended frame rate is 10 fps to 30 fps.\n"
                            + "4.The encapsulation format can be MP4, AVI, FLV, WEBM, ASF, or MOV.\n"
                            + "5.The video encoding format can be H.261, H.263, H.264, HEVC, VC-1, VP8, VP9, or WMV3.",
              displayName = "Video Url")
    @Metadata(required = false)
    private String videoUrl;

    @UriParam(description = "This param can be used when operation is faceLiveDetection, indicating the local video file path.\n"
                            + "Any one of videoBase64, videoUrl and videoFilePath needs to be set, and the priority is videoBase64 > videoUrl > videoFilePath.\n"
                            + "The video requirements are as follows:\n"
                            + "1.The size of a video file cannot exceed 8 MB. It is recommended that the video file be compressed to 200 KB to 2 MB on the client.\n"
                            + "2.The video duration must be 1 to 15 seconds.\n"
                            + "3.The recommended frame rate is 10 fps to 30 fps.\n"
                            + "4.The encapsulation format can be MP4, AVI, FLV, WEBM, ASF, or MOV.\n"
                            + "5.The video encoding format can be H.261, H.263, H.264, HEVC, VC-1, VP8, VP9, or WMV3.",
              displayName = "Video File Path")
    @Metadata(required = false)
    private String videoFilePath;

    @UriParam(description = "This param is mandatory when the operation is faceLiveDetection, indicating the action code sequence list.\n"
                            + "Actions are separated by commas (,). Currently, the following actions are supported:\n"
                            + "1: Shake the head to the left.\n"
                            + "2: Shake the head to the right.\n"
                            + "3: Nod the head.\n"
                            + "4: Mouth movement.",
              displayName = "Actions")
    @Metadata(required = false)
    private String actions;

    @UriParam(description = "This param can be used when the operation is faceLiveDetection, indicating the action time array.\n"
                            + "The length of the array is the same as the number of actions.\n"
                            + "Each item contains the start time and end time of the action in the corresponding sequence. The unit is the milliseconds from the video start time.",
              displayName = "Action Time")
    @Metadata(required = false)
    private String actionTimes;

    private FrsClient frsClient;

    public FaceRecognitionEndpoint() {
    }

    public FaceRecognitionEndpoint(String uri, String operation, FaceRecognitionComponent component) {
        super(uri, component);
        this.operation = operation;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new FaceRecognitionProducer(this);
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
