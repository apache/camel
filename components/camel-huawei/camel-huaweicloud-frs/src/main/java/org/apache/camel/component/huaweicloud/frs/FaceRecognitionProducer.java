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
import org.apache.camel.component.huaweicloud.common.models.InputSourceType;
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
        updateFaceDetectionConfigurations(exchange, clientConfigurations);
        SdkResponse result;
        switch (clientConfigurations.getInputSourceType()) {
            case BASE64:
                FaceDetectBase64Req base64ReqBody
                        = new FaceDetectBase64Req().withImageBase64(clientConfigurations.getImageBase64());
                result = this.frsClient.detectFaceByBase64(new DetectFaceByBase64Request().withBody(base64ReqBody));
                break;
            case URL:
                FaceDetectUrlReq urlReqBody = new FaceDetectUrlReq().withImageUrl(clientConfigurations.getImageUrl());
                result = this.frsClient.detectFaceByUrl(new DetectFaceByUrlRequest().withBody(urlReqBody));
                break;
            default:
                try (FileInputStream inputStream = new FileInputStream(clientConfigurations.getImageFilePath())) {
                    DetectFaceByFileRequestBody fileReqBody = new DetectFaceByFileRequestBody().withImageFile(inputStream,
                            getFileName(clientConfigurations.getImageFilePath()));
                    result = this.frsClient.detectFaceByFile(new DetectFaceByFileRequest().withBody(fileReqBody));
                } catch (IOException e) {
                    throw new IllegalArgumentException(
                            String.format("Image file path is invalid: %s", clientConfigurations.getImageFilePath()));
                }
        }
        exchange.getMessage().setBody(result);
    }

    /**
     * compare two faces
     *
     * @param exchange camel exchange
     */
    private void performFaceVerificationOperation(Exchange exchange, ClientConfigurations clientConfigurations) {
        updateFaceVerificationConfigurations(exchange, clientConfigurations);
        SdkResponse result;
        switch (clientConfigurations.getInputSourceType()) {
            case BASE64:
                FaceCompareBase64Req base64ReqBody
                        = new FaceCompareBase64Req().withImage1Base64(clientConfigurations.getImageBase64())
                                .withImage2Base64(clientConfigurations.getAnotherImageBase64());
                result = this.frsClient.compareFaceByBase64(new CompareFaceByBase64Request().withBody(base64ReqBody));
                break;
            case URL:
                FaceCompareUrlReq urlReqBody = new FaceCompareUrlReq().withImage1Url(clientConfigurations.getImageUrl())
                        .withImage2Url(clientConfigurations.getAnotherImageUrl());
                result = this.frsClient.compareFaceByUrl(new CompareFaceByUrlRequest().withBody(urlReqBody));
                break;
            default:
                try (FileInputStream image1InputStream = new FileInputStream(clientConfigurations.getImageFilePath());
                     FileInputStream image2InputStream = new FileInputStream(clientConfigurations.getAnotherImageFilePath())) {
                    CompareFaceByFileRequestBody fileReqBody = new CompareFaceByFileRequestBody()
                            .withImage1File(image1InputStream, getFileName(clientConfigurations.getImageFilePath()))
                            .withImage2File(image2InputStream, getFileName(clientConfigurations.getAnotherImageFilePath()));
                    result = this.frsClient.compareFaceByFile(new CompareFaceByFileRequest().withBody(fileReqBody));
                } catch (IOException e) {
                    throw new IllegalArgumentException(
                            String.format("Image file paths are invalid: %s, %s", clientConfigurations.getImageFilePath(),
                                    clientConfigurations.getAnotherImageFilePath()));
                }
        }
        exchange.getMessage().setBody(result);
    }

    /**
     * detect alive faces in a video
     *
     * @param exchange camel exchange
     */
    private void performLiveDetectOperation(Exchange exchange, ClientConfigurations clientConfigurations) {
        updateLiveDetectConfigurations(exchange, clientConfigurations);
        SdkResponse result;
        switch (clientConfigurations.getInputSourceType()) {
            case BASE64:
                LiveDetectBase64Req base64ReqBody = new LiveDetectBase64Req()
                        .withVideoBase64(clientConfigurations.getVideoBase64())
                        .withActions(clientConfigurations.getActions()).withActionTime(clientConfigurations.getActionTimes());
                result = this.frsClient.detectLiveByBase64(new DetectLiveByBase64Request().withBody(base64ReqBody));
                break;
            case URL:
                LiveDetectUrlReq urlReqBody = new LiveDetectUrlReq().withVideoUrl(clientConfigurations.getVideoUrl())
                        .withActions(clientConfigurations.getActions()).withActionTime(clientConfigurations.getActionTimes());
                result = this.frsClient.detectLiveByUrl(new DetectLiveByUrlRequest().withBody(urlReqBody));
                break;
            default:
                try (FileInputStream inputStream = new FileInputStream(clientConfigurations.getVideoFilePath())) {
                    DetectLiveByFileRequestBody fileReqBody = new DetectLiveByFileRequestBody()
                            .withVideoFile(inputStream, getFileName(clientConfigurations.getVideoFilePath()))
                            .withActions(clientConfigurations.getActions())
                            .withActionTime(clientConfigurations.getActionTimes());
                    result = this.frsClient.detectLiveByFile(new DetectLiveByFileRequest().withBody(fileReqBody));
                } catch (IOException e) {
                    throw new IllegalArgumentException(
                            String.format("Video file path is invalid: %s", clientConfigurations.getVideoFilePath()));
                }
        }
        exchange.getMessage().setBody(result);
    }

    private String getFileName(String filePath) {
        return new File(filePath).getName();
    }

    private void updateFaceDetectionConfigurations(Exchange exchange, ClientConfigurations clientConfigurations) {
        String imageBase64 = exchange.getProperty(FaceRecognitionProperties.FACE_IMAGE_BASE64, String.class);
        clientConfigurations.setImageBase64(StringUtils.isEmpty(imageBase64) ? this.endpoint.getImageBase64() : imageBase64);
        if (!StringUtils.isEmpty(clientConfigurations.getImageBase64())) {
            clientConfigurations.setInputSourceType(InputSourceType.BASE64);
            return;
        }
        String imageUrl = exchange.getProperty(FaceRecognitionProperties.FACE_IMAGE_URL, String.class);
        clientConfigurations.setImageUrl(StringUtils.isEmpty(imageUrl) ? this.endpoint.getImageUrl() : imageUrl);
        if (!StringUtils.isEmpty(clientConfigurations.getImageUrl())) {
            clientConfigurations.setInputSourceType(InputSourceType.URL);
            return;
        }
        String imageFilePath = exchange.getProperty(FaceRecognitionProperties.FACE_IMAGE_FILE_PATH, String.class);
        clientConfigurations
                .setImageFilePath(StringUtils.isEmpty(imageFilePath) ? this.endpoint.getImageFilePath() : imageFilePath);
        if (!StringUtils.isEmpty(clientConfigurations.getImageFilePath())) {
            clientConfigurations.setInputSourceType(InputSourceType.FILE_PATH);
            return;
        }
        throw new IllegalArgumentException("any one of image base64, url and filePath needs to be set");
    }

    private void updateFaceVerificationConfigurations(Exchange exchange, ClientConfigurations clientConfigurations) {
        String image1Base64 = exchange.getProperty(FaceRecognitionProperties.FACE_IMAGE_BASE64, String.class);
        String image2Base64 = exchange.getProperty(FaceRecognitionProperties.ANOTHER_FACE_IMAGE_BASE64, String.class);
        clientConfigurations.setImageBase64(StringUtils.isEmpty(image1Base64) ? this.endpoint.getImageBase64() : image1Base64);
        clientConfigurations.setAnotherImageBase64(
                StringUtils.isEmpty(image2Base64) ? this.endpoint.getAnotherImageBase64() : image2Base64);
        if (!StringUtils.isEmpty(clientConfigurations.getImageBase64())
                && !StringUtils.isEmpty(clientConfigurations.getAnotherImageBase64())) {
            clientConfigurations.setInputSourceType(InputSourceType.BASE64);
            return;
        }
        String image1Url = exchange.getProperty(FaceRecognitionProperties.FACE_IMAGE_URL, String.class);
        String image2Url = exchange.getProperty(FaceRecognitionProperties.ANOTHER_FACE_IMAGE_URL, String.class);
        clientConfigurations.setImageUrl(StringUtils.isEmpty(image1Url) ? this.endpoint.getImageUrl() : image1Url);
        clientConfigurations
                .setAnotherImageUrl(StringUtils.isEmpty(image2Url) ? this.endpoint.getAnotherImageUrl() : image2Url);
        if (!StringUtils.isEmpty(clientConfigurations.getImageUrl())
                && !StringUtils.isEmpty(clientConfigurations.getAnotherImageUrl())) {
            clientConfigurations.setInputSourceType(InputSourceType.URL);
            return;
        }
        String image1FilePath = exchange.getProperty(FaceRecognitionProperties.FACE_IMAGE_FILE_PATH, String.class);
        String image2FilePath = exchange.getProperty(FaceRecognitionProperties.ANOTHER_FACE_IMAGE_FILE_PATH, String.class);
        clientConfigurations
                .setImageFilePath(StringUtils.isEmpty(image1FilePath) ? this.endpoint.getImageFilePath() : image1FilePath);
        clientConfigurations.setAnotherImageFilePath(
                StringUtils.isEmpty(image2FilePath) ? this.endpoint.getAnotherImageFilePath() : image2FilePath);
        if (!StringUtils.isEmpty(clientConfigurations.getImageFilePath())
                && !StringUtils.isEmpty(clientConfigurations.getAnotherImageFilePath())) {
            clientConfigurations.setInputSourceType(InputSourceType.FILE_PATH);
            return;
        }
        throw new IllegalArgumentException("any one of image base64, url and filePath needs to be set");
    }

    private void updateLiveDetectConfigurations(Exchange exchange, ClientConfigurations clientConfigurations) {
        updateVideoSource(exchange, clientConfigurations);
        String actions = exchange.getProperty(FaceRecognitionProperties.FACE_VIDEO_ACTIONS, String.class);
        clientConfigurations.setActions(StringUtils.isEmpty(actions) ? this.endpoint.getActions() : actions);
        if (StringUtils.isEmpty(clientConfigurations.getActions())) {
            throw new IllegalArgumentException("actions needs to be set");
        }
        String actionTimes = exchange.getProperty(FaceRecognitionProperties.FACE_VIDEO_ACTION_TIMES, String.class);
        clientConfigurations.setActionTimes(
                StringUtils.isEmpty(actionTimes) ? this.endpoint.getActionTimes() : actionTimes);
    }

    private void updateVideoSource(Exchange exchange, ClientConfigurations clientConfigurations) {
        String videoBase64 = exchange.getProperty(FaceRecognitionProperties.FACE_VIDEO_BASE64, String.class);
        clientConfigurations.setVideoBase64(StringUtils.isEmpty(videoBase64) ? this.endpoint.getVideoBase64() : videoBase64);
        if (!StringUtils.isEmpty(clientConfigurations.getVideoBase64())) {
            clientConfigurations.setInputSourceType(InputSourceType.BASE64);
            return;
        }
        String videoUrl = exchange.getProperty(FaceRecognitionProperties.FACE_VIDEO_URL, String.class);
        clientConfigurations.setVideoUrl(StringUtils.isEmpty(videoUrl) ? this.endpoint.getVideoUrl() : videoUrl);
        if (!StringUtils.isEmpty(clientConfigurations.getVideoUrl())) {
            clientConfigurations.setInputSourceType(InputSourceType.URL);
            return;
        }
        String videoFilePath = exchange.getProperty(FaceRecognitionProperties.FACE_VIDEO_FILE_PATH, String.class);
        clientConfigurations
                .setVideoFilePath(StringUtils.isEmpty(videoFilePath) ? this.endpoint.getVideoFilePath() : videoFilePath);
        if (!StringUtils.isEmpty(clientConfigurations.getVideoFilePath())) {
            clientConfigurations.setInputSourceType(InputSourceType.FILE_PATH);
            return;
        }
        throw new IllegalArgumentException("any one of video base64, url and filePath needs to be set");
    }

    private String getAccessKey(FaceRecognitionEndpoint endpoint) {
        if (!StringUtils.isEmpty(endpoint.getAccessKey())) {
            return endpoint.getAccessKey();
        }
        if (endpoint.getServiceKeys() != null && !StringUtils.isEmpty(endpoint.getServiceKeys().getAccessKey())) {
            return endpoint.getServiceKeys().getAccessKey();
        }
        throw new IllegalArgumentException("authentication parameter access key (AK) not found");
    }

    private String getSecretKey(FaceRecognitionEndpoint endpoint) {
        if (!StringUtils.isEmpty(endpoint.getSecretKey())) {
            return endpoint.getSecretKey();
        }
        if (endpoint.getServiceKeys() != null && !StringUtils.isEmpty(endpoint.getServiceKeys().getSecretKey())) {
            return endpoint.getServiceKeys().getSecretKey();
        }
        throw new IllegalArgumentException("authentication parameter secret key (SK) not found");
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
