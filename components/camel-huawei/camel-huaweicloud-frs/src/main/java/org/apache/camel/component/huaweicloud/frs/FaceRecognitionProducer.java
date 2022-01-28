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

import java.io.*;

import com.huaweicloud.sdk.core.SdkResponse;
import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.http.HttpConfig;
import com.huaweicloud.sdk.core.utils.StringUtils;
import com.huaweicloud.sdk.frs.v2.FrsClient;
import com.huaweicloud.sdk.frs.v2.model.CompareFaceByBase64Request;
import com.huaweicloud.sdk.frs.v2.model.CompareFaceByFileRequest;
import com.huaweicloud.sdk.frs.v2.model.CompareFaceByFileRequestBody;
import com.huaweicloud.sdk.frs.v2.model.CompareFaceByUrlRequest;
import com.huaweicloud.sdk.frs.v2.model.DetectFaceByBase64Request;
import com.huaweicloud.sdk.frs.v2.model.DetectFaceByFileRequest;
import com.huaweicloud.sdk.frs.v2.model.DetectFaceByFileRequestBody;
import com.huaweicloud.sdk.frs.v2.model.DetectFaceByUrlRequest;
import com.huaweicloud.sdk.frs.v2.model.DetectLiveByBase64Request;
import com.huaweicloud.sdk.frs.v2.model.DetectLiveByFileRequest;
import com.huaweicloud.sdk.frs.v2.model.DetectLiveByFileRequestBody;
import com.huaweicloud.sdk.frs.v2.model.DetectLiveByUrlRequest;
import com.huaweicloud.sdk.frs.v2.model.FaceCompareBase64Req;
import com.huaweicloud.sdk.frs.v2.model.FaceCompareUrlReq;
import com.huaweicloud.sdk.frs.v2.model.FaceDetectBase64Req;
import com.huaweicloud.sdk.frs.v2.model.FaceDetectUrlReq;
import com.huaweicloud.sdk.frs.v2.model.LiveDetectBase64Req;
import com.huaweicloud.sdk.frs.v2.model.LiveDetectUrlReq;
import com.huaweicloud.sdk.frs.v2.region.FrsRegion;
import org.apache.camel.Exchange;
import org.apache.camel.component.huaweicloud.frs.constants.FaceRecognitionConstants;
import org.apache.camel.component.huaweicloud.frs.constants.FaceRecognitionProperties;
import org.apache.camel.component.huaweicloud.frs.models.ClientConfigurations;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FaceRecognitionProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(FaceRecognitionProducer.class);

    private FrsClient frsClient;

    private FaceRecognitionEndpoint endpoint;

    public FaceRecognitionProducer(FaceRecognitionEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    public void process(Exchange exchange) {
        ClientConfigurations clientConfigurations = initializeConfigurations(endpoint);
        if (frsClient == null) {
            initializeSdkClient(endpoint, clientConfigurations);
        }
        String operation = endpoint.getOperation();
        if (StringUtils.isEmpty(operation)) {
            throw new IllegalStateException("operation cannot be empty");
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Performing frs operation: {}", operation);
        }
        switch (operation) {
            case FaceRecognitionConstants.OPERATION_FACE_DETECTION:
                performFaceDetectionOperation(exchange, clientConfigurations);
                break;
            case FaceRecognitionConstants.OPERATION_FACE_VERIFICATION:
                performFaceVerificationOperation(exchange, clientConfigurations);
                break;
            case FaceRecognitionConstants.OPERATION_FACE_LIVE_DETECT:
                performLiveDetectOperation(exchange, clientConfigurations);
                break;
            default:
                throw new UnsupportedOperationException(
                        "operation needs to be faceDetection, faceVerification or faceLiveDetection");
        }
    }

    /**
     * initialize clientConfigurations
     *
     * @param  endpoint FrsEndpoint
     * @return          ClientConfigurations
     */
    private ClientConfigurations initializeConfigurations(FaceRecognitionEndpoint endpoint) {
        ClientConfigurations clientConfigurations = new ClientConfigurations();
        clientConfigurations.setAccessKey(getAccessKey(endpoint));
        clientConfigurations.setSecretKey(getSecretKey(endpoint));
        clientConfigurations.setProjectId(getProjectId(endpoint));
        clientConfigurations.setEndpoint(getEndpoint(endpoint));

        if (StringUtils.isEmpty(endpoint.getOperation())) {
            throw new IllegalArgumentException("operation needs to be set");
        }
        clientConfigurations.setIgnoreSslVerification(endpoint.isIgnoreSslVerification());
        if (clientConfigurations.isIgnoreSslVerification()) {
            LOG.warn("SSL verification is ignored. This is unsafe in production environment");
        }
        if (!StringUtils.isEmpty(endpoint.getProxyHost())) {
            clientConfigurations.setProxyHost(endpoint.getProxyHost());
            clientConfigurations.setProxyPort(endpoint.getProxyPort());
            clientConfigurations.setProxyUser(endpoint.getProxyUser());
            clientConfigurations.setProxyPassword(endpoint.getProxyPassword());
        }
        return clientConfigurations;
    }

    /**
     * initialize frs client. This is lazily initialized on the first message
     *
     * @param endpoint             camel frs endpoint
     * @param clientConfigurations FrsClient configurations
     */
    private void initializeSdkClient(FaceRecognitionEndpoint endpoint, ClientConfigurations clientConfigurations) {
        if (endpoint.getFrsClient() != null) {
            LOG.info(
                    "Instance of FrsClient was set on the endpoint. Skip creation of FrsClient from endpoint parameters");
            this.frsClient = endpoint.getFrsClient();
            return;
        }
        HttpConfig httpConfig
                = HttpConfig.getDefaultHttpConfig().withIgnoreSSLVerification(clientConfigurations.isIgnoreSslVerification());
        if (!StringUtils.isEmpty(clientConfigurations.getProxyHost())) {
            httpConfig.setProxyHost(clientConfigurations.getProxyHost());
            httpConfig.setProxyPort(clientConfigurations.getProxyPort());
            if (!StringUtils.isEmpty(clientConfigurations.getProxyUser())) {
                httpConfig.setProxyUsername(clientConfigurations.getProxyUser());
                httpConfig.setProxyPassword(clientConfigurations.getProxyPassword());
            }
        }
        BasicCredentials credentials = new BasicCredentials().withAk(clientConfigurations.getAccessKey())
                .withSk(clientConfigurations.getSecretKey())
                .withProjectId(clientConfigurations.getProjectId());

        frsClient = FrsClient.newBuilder()
                .withCredential(credentials)
                .withHttpConfig(httpConfig)
                .withEndpoint(clientConfigurations.getEndpoint())
                .build();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Successfully initialized FRS client");
        }
    }

    /**
     * detect all faces in an image
     *
     * @param exchange camel exchange
     */
    private void performFaceDetectionOperation(Exchange exchange, ClientConfigurations clientConfigurations) {
        updateClientConfigurations(exchange, clientConfigurations);
        SdkResponse result;
        if (!StringUtils.isEmpty(clientConfigurations.getImageBase64())) {
            FaceDetectBase64Req reqBody = new FaceDetectBase64Req().withImageBase64(clientConfigurations.getImageBase64());
            result = this.frsClient.detectFaceByBase64(new DetectFaceByBase64Request().withBody(reqBody));
        } else if (!StringUtils.isEmpty(clientConfigurations.getImageUrl())) {
            FaceDetectUrlReq reqBody = new FaceDetectUrlReq().withImageUrl(clientConfigurations.getImageUrl());
            result = this.frsClient.detectFaceByUrl(new DetectFaceByUrlRequest().withBody(reqBody));
        } else {
            File imageFile = getFile(clientConfigurations.getImageFilePath());
            DetectFaceByFileRequestBody reqBody;
            try {
                reqBody = new DetectFaceByFileRequestBody().withImageFile(new FileInputStream(imageFile), imageFile.getName());
            } catch (FileNotFoundException e) {
                throw new IllegalArgumentException(
                        String.format("Image file not found: %s", clientConfigurations.getImageFilePath()));
            }
            result = this.frsClient.detectFaceByFile(new DetectFaceByFileRequest().withBody(reqBody));
        }
        exchange.getMessage().setBody(result);
    }

    /**
     * compare two faces
     *
     * @param exchange camel exchange
     */
    private void performFaceVerificationOperation(Exchange exchange, ClientConfigurations clientConfigurations) {
        updateClientConfigurations(exchange, clientConfigurations);

        SdkResponse result;
        if (!StringUtils.isEmpty(clientConfigurations.getImageBase64())
                && !StringUtils.isEmpty(clientConfigurations.getAnotherImageBase64())) {
            FaceCompareBase64Req reqBody = new FaceCompareBase64Req().withImage1Base64(clientConfigurations.getImageBase64())
                    .withImage2Base64(clientConfigurations.getAnotherImageBase64());
            result = this.frsClient.compareFaceByBase64(new CompareFaceByBase64Request().withBody(reqBody));
        } else if (!StringUtils.isEmpty(clientConfigurations.getImageUrl())
                && !StringUtils.isEmpty(clientConfigurations.getAnotherImageUrl())) {
            FaceCompareUrlReq reqBody = new FaceCompareUrlReq().withImage1Url(clientConfigurations.getImageUrl())
                    .withImage2Url(clientConfigurations.getAnotherImageUrl());
            result = this.frsClient.compareFaceByUrl(new CompareFaceByUrlRequest().withBody(reqBody));
        } else {
            File image1File = getFile(clientConfigurations.getImageFilePath());
            File image2File = getFile(clientConfigurations.getAnotherImageFilePath());
            CompareFaceByFileRequestBody reqBody;
            try {
                reqBody = new CompareFaceByFileRequestBody()
                        .withImage1File(new FileInputStream(image1File), image1File.getName())
                        .withImage2File(new FileInputStream(image2File), image2File.getName());
            } catch (FileNotFoundException e) {
                throw new IllegalArgumentException(
                        String.format("Image file not found: %s", clientConfigurations.getImageFilePath()));
            }
            result = this.frsClient.compareFaceByFile(new CompareFaceByFileRequest().withBody(reqBody));
        }
        exchange.getMessage().setBody(result);
    }

    /**
     * detect alive faces in a video
     *
     * @param exchange camel exchange
     */
    private void performLiveDetectOperation(Exchange exchange, ClientConfigurations clientConfigurations) {
        updateClientConfigurations(exchange, clientConfigurations);
        SdkResponse result;
        if (!StringUtils.isEmpty(clientConfigurations.getVideoBase64())) {
            LiveDetectBase64Req reqBody = new LiveDetectBase64Req().withVideoBase64(clientConfigurations.getVideoBase64())
                    .withActions(clientConfigurations.getActions()).withActionTime(clientConfigurations.getActionTimes());
            result = this.frsClient.detectLiveByBase64(new DetectLiveByBase64Request().withBody(reqBody));
        } else if (!StringUtils.isEmpty(clientConfigurations.getVideoUrl())) {
            LiveDetectUrlReq reqBody = new LiveDetectUrlReq().withVideoUrl(clientConfigurations.getVideoUrl())
                    .withActions(clientConfigurations.getActions()).withActionTime(clientConfigurations.getActionTimes());
            result = this.frsClient.detectLiveByUrl(new DetectLiveByUrlRequest().withBody(reqBody));
        } else {
            File videoFile = getFile(clientConfigurations.getVideoFilePath());
            DetectLiveByFileRequestBody reqBody;
            try {
                reqBody = new DetectLiveByFileRequestBody().withVideoFile(new FileInputStream(videoFile), videoFile.getName())
                        .withActions(clientConfigurations.getActions()).withActionTime(clientConfigurations.getActionTimes());
            } catch (FileNotFoundException e) {
                throw new IllegalArgumentException(
                        String.format("Video file not found: %s", clientConfigurations.getImageFilePath()));
            }
            result = this.frsClient.detectLiveByFile(new DetectLiveByFileRequest().withBody(reqBody));
        }
        exchange.getMessage().setBody(result);
    }

    private File getFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException(String.format("File path is invalid: %s", file));
        }
        return file;
    }

    /**
     * Update dynamic client configurations. Some endpoint parameters (imageContent, imageUrl, tagLanguage, tagLimit and
     * threshold) can also be passed via exchange properties, so they can be updated between each transaction. Since
     * they can change, we must clear the previous transaction and update these parameters with their new values
     *
     * @param exchange             camel exchange
     * @param clientConfigurations frs client configurations
     */
    private void updateClientConfigurations(Exchange exchange, ClientConfigurations clientConfigurations) {
        if (FaceRecognitionConstants.OPERATION_FACE_DETECTION.equals(endpoint.getOperation())) {
            updateFaceDetectionConfigurations(exchange, clientConfigurations);
        } else if (FaceRecognitionConstants.OPERATION_FACE_VERIFICATION.equals(endpoint.getOperation())) {
            updateFaceVerificationConfigurations(exchange, clientConfigurations);
        } else if (FaceRecognitionConstants.OPERATION_FACE_LIVE_DETECT.equals(endpoint.getOperation())) {
            updateLiveDetectConfigurations(exchange, clientConfigurations);
        }
    }

    private void updateFaceDetectionConfigurations(Exchange exchange, ClientConfigurations clientConfigurations) {
        boolean isImageBase64Set = true;
        boolean isImageUrlSet = true;
        boolean isImageFilePathSet = true;

        String imageBase64 = exchange.getProperty(FaceRecognitionProperties.FACE_IMAGE_BASE64, String.class);
        if (!StringUtils.isEmpty(imageBase64)) {
            clientConfigurations.setImageBase64(imageBase64);
        } else if (!StringUtils.isEmpty(this.endpoint.getImageBase64())) {
            clientConfigurations.setImageBase64(this.endpoint.getImageBase64());
        } else {
            isImageBase64Set = false;
        }

        String imageUrl = exchange.getProperty(FaceRecognitionProperties.FACE_IMAGE_URL, String.class);
        if (!StringUtils.isEmpty(imageUrl)) {
            clientConfigurations.setImageUrl(imageUrl);
        } else if (!StringUtils.isEmpty(this.endpoint.getImageUrl())) {
            clientConfigurations.setImageUrl(this.endpoint.getImageUrl());
        } else {
            isImageUrlSet = false;
        }

        String imageFilePath = exchange.getProperty(FaceRecognitionProperties.FACE_IMAGE_FILE_PATH, String.class);
        if (!StringUtils.isEmpty(imageFilePath)) {
            clientConfigurations.setImageFilePath(imageFilePath);
        } else if (!StringUtils.isEmpty(this.endpoint.getImageFilePath())) {
            clientConfigurations.setImageFilePath(this.endpoint.getImageFilePath());
        } else {
            isImageFilePathSet = false;
        }
        if (!isImageBase64Set && !isImageUrlSet && !isImageFilePathSet) {
            throw new IllegalArgumentException("any one of image base64, url and filePath needs to be set");
        }
    }

    private void updateFaceVerificationConfigurations(Exchange exchange, ClientConfigurations clientConfigurations) {
        boolean isImageBase64Set = true;
        boolean isImageUrlSet = true;
        boolean isImageFilePathSet = true;

        String image1Base64 = exchange.getProperty(FaceRecognitionProperties.FACE_IMAGE_BASE64, String.class);
        String image2Base64 = exchange.getProperty(FaceRecognitionProperties.ANOTHER_FACE_IMAGE_BASE64, String.class);
        if (!StringUtils.isEmpty(image1Base64) && !StringUtils.isEmpty(image2Base64)) {
            clientConfigurations.setImageBase64(image1Base64);
            clientConfigurations.setAnotherImageBase64(image2Base64);
        } else if (!StringUtils.isEmpty(this.endpoint.getImageBase64())
                && !StringUtils.isEmpty(this.endpoint.getAnotherImageBase64())) {
            clientConfigurations.setImageBase64(this.endpoint.getImageBase64());
            clientConfigurations.setAnotherImageBase64(this.endpoint.getAnotherImageBase64());
        } else {
            isImageBase64Set = false;
        }

        String image1Url = exchange.getProperty(FaceRecognitionProperties.FACE_IMAGE_URL, String.class);
        String image2Url = exchange.getProperty(FaceRecognitionProperties.ANOTHER_FACE_IMAGE_URL, String.class);
        if (!StringUtils.isEmpty(image1Url) && !StringUtils.isEmpty(image2Url)) {
            clientConfigurations.setImageUrl(image1Url);
            clientConfigurations.setAnotherImageUrl(image2Url);
        } else if (!StringUtils.isEmpty(this.endpoint.getImageUrl())
                && !StringUtils.isEmpty(this.endpoint.getAnotherImageUrl())) {
            clientConfigurations.setImageUrl(this.endpoint.getImageUrl());
            clientConfigurations.setAnotherImageUrl(this.endpoint.getAnotherImageUrl());
        } else {
            isImageUrlSet = false;
        }

        String image1FilePath = exchange.getProperty(FaceRecognitionProperties.FACE_IMAGE_FILE_PATH, String.class);
        String image2FilePath = exchange.getProperty(FaceRecognitionProperties.ANOTHER_FACE_IMAGE_FILE_PATH, String.class);
        if (!StringUtils.isEmpty(image1FilePath) && !StringUtils.isEmpty(image2FilePath)) {
            clientConfigurations.setImageFilePath(image1FilePath);
            clientConfigurations.setAnotherImageFilePath(image2FilePath);
        } else if (!StringUtils.isEmpty(this.endpoint.getImageFilePath())
                && !StringUtils.isEmpty(this.endpoint.getAnotherImageFilePath())) {
            clientConfigurations.setImageFilePath(this.endpoint.getImageFilePath());
            clientConfigurations.setAnotherImageFilePath(this.endpoint.getAnotherImageFilePath());
        } else {
            isImageFilePathSet = false;
        }

        if (!isImageBase64Set && !isImageUrlSet && !isImageFilePathSet) {
            throw new IllegalArgumentException("any one of image base64, url and filePath needs to be set");
        }
    }

    private void updateLiveDetectConfigurations(Exchange exchange, ClientConfigurations clientConfigurations) {
        boolean isVideoBase64Set = true;
        boolean isVideoUrlSet = true;
        boolean isVideoFilePathSet = true;

        String videoBase64 = exchange.getProperty(FaceRecognitionProperties.FACE_VIDEO_BASE64, String.class);
        if (!StringUtils.isEmpty(videoBase64)) {
            clientConfigurations.setVideoBase64(videoBase64);
        } else if (!StringUtils.isEmpty(this.endpoint.getVideoBase64())) {
            clientConfigurations.setVideoBase64(this.endpoint.getVideoBase64());
        } else {
            isVideoBase64Set = false;
        }

        String videoUrl = exchange.getProperty(FaceRecognitionProperties.FACE_VIDEO_URL, String.class);
        if (!StringUtils.isEmpty(videoUrl)) {
            clientConfigurations.setVideoUrl(videoUrl);
        } else if (!StringUtils.isEmpty(this.endpoint.getVideoUrl())) {
            clientConfigurations.setVideoUrl(this.endpoint.getVideoUrl());
        } else {
            isVideoUrlSet = false;
        }

        String videoFilePath = exchange.getProperty(FaceRecognitionProperties.FACE_VIDEO_FILE_PATH, String.class);
        if (!StringUtils.isEmpty(videoFilePath)) {
            clientConfigurations.setVideoFilePath(videoFilePath);
        } else if (!StringUtils.isEmpty(this.endpoint.getVideoFilePath())) {
            clientConfigurations.setVideoFilePath(this.endpoint.getVideoFilePath());
        } else {
            isVideoFilePathSet = false;
        }

        if (!isVideoBase64Set && !isVideoUrlSet && !isVideoFilePathSet) {
            throw new IllegalArgumentException("any one of video base64, url and filePath needs to be set");
        }
        String actions = exchange.getProperty(FaceRecognitionProperties.FACE_VIDEO_ACTIONS, String.class);
        if (!StringUtils.isEmpty(actions)) {
            clientConfigurations.setActions(actions);
        } else if (!StringUtils.isEmpty(this.endpoint.getActions())) {
            clientConfigurations.setActions(this.endpoint.getActions());
        } else {
            throw new IllegalArgumentException("actions needs to be set");
        }

        String actionTimes = exchange.getProperty(FaceRecognitionProperties.FACE_VIDEO_ACTION_TIMES, String.class);
        clientConfigurations.setActionTimes(
                StringUtils.isEmpty(actionTimes) ? this.endpoint.getActionTimes() : actionTimes);

    }

    private String getAccessKey(FaceRecognitionEndpoint endpoint) {
        if (!StringUtils.isEmpty(endpoint.getAccessKey())) {
            return endpoint.getAccessKey();
        } else if (endpoint.getServiceKeys() != null
                && !StringUtils.isEmpty(endpoint.getServiceKeys().getAccessKey())) {
            return endpoint.getServiceKeys().getAccessKey();
        } else {
            throw new IllegalArgumentException("authentication parameter access key (AK) not found");
        }
    }

    private String getSecretKey(FaceRecognitionEndpoint endpoint) {
        if (!StringUtils.isEmpty(endpoint.getSecretKey())) {
            return endpoint.getSecretKey();
        } else if (endpoint.getServiceKeys() != null
                && !StringUtils.isEmpty(endpoint.getServiceKeys().getSecretKey())) {
            return endpoint.getServiceKeys().getSecretKey();
        } else {
            throw new IllegalArgumentException("authentication parameter secret key (SK) not found");
        }
    }

    private String getProjectId(FaceRecognitionEndpoint endpoint) {
        if (StringUtils.isEmpty(endpoint.getProjectId())) {
            throw new IllegalArgumentException("Project id not found");
        }
        return endpoint.getProjectId();
    }

    private String getEndpoint(FaceRecognitionEndpoint endpoint) {
        if (!StringUtils.isEmpty(endpoint.getEndpoint())) {
            return endpoint.getEndpoint();
        }
        if (StringUtils.isEmpty(endpoint.getRegion())) {
            throw new IllegalArgumentException("either endpoint or region needs to be set");
        }
        return FrsRegion.valueOf(endpoint.getRegion()).getEndpoint();
    }
}
