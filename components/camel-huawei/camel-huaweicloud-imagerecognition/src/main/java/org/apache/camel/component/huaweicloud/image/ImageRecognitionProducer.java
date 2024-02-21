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

import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.http.HttpConfig;
import com.huaweicloud.sdk.core.utils.StringUtils;
import com.huaweicloud.sdk.image.v2.ImageClient;
import com.huaweicloud.sdk.image.v2.model.CelebrityRecognitionReq;
import com.huaweicloud.sdk.image.v2.model.ImageTaggingReq;
import com.huaweicloud.sdk.image.v2.model.RunCelebrityRecognitionRequest;
import com.huaweicloud.sdk.image.v2.model.RunCelebrityRecognitionResponse;
import com.huaweicloud.sdk.image.v2.model.RunImageTaggingRequest;
import com.huaweicloud.sdk.image.v2.model.RunImageTaggingResponse;
import com.huaweicloud.sdk.image.v2.region.ImageRegion;
import org.apache.camel.Exchange;
import org.apache.camel.component.huaweicloud.image.constants.ImageRecognitionConstants;
import org.apache.camel.component.huaweicloud.image.constants.ImageRecognitionProperties;
import org.apache.camel.component.huaweicloud.image.models.ClientConfigurations;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageRecognitionProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(ImageRecognitionProducer.class);

    private ImageClient imageClient;

    private ImageRecognitionEndpoint endpoint;

    public ImageRecognitionProducer(ImageRecognitionEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    /**
     * initialize ClientConfigurations
     *
     * @param  endpoint ImageRecognitionEndpoint
     * @return          ClientConfigurations
     */
    private ClientConfigurations initializeConfigurations(ImageRecognitionEndpoint endpoint) {
        ClientConfigurations clientConfigurations = new ClientConfigurations();

        clientConfigurations.setAccessKey(getAccessKey(endpoint));
        clientConfigurations.setSecretKey(getSecretKey(endpoint));
        clientConfigurations.setProjectId(getProjectId(endpoint));
        clientConfigurations.setEndpoint(getEndpoint(endpoint));

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
     * initialize image client. this is lazily initialized on the first message
     *
     * @param  endpoint
     * @param  clientConfigurations
     * @return
     */
    private ImageClient initializeSdkClient(ImageRecognitionEndpoint endpoint, ClientConfigurations clientConfigurations) {
        if (endpoint.getImageClient() != null) {
            LOG.info(
                    "Instance of ImageClient was set on the endpoint. Skipping creation of ImageClient from endpoint parameters");
            this.imageClient = endpoint.getImageClient();
            return endpoint.getImageClient();
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

        imageClient = ImageClient.newBuilder()
                .withCredential(credentials)
                .withHttpConfig(httpConfig)
                .withEndpoint(clientConfigurations.getEndpoint())
                .build();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Successfully initialized Image client");
        }
        return imageClient;
    }

    public void process(Exchange exchange) {

        ClientConfigurations clientConfigurations = initializeConfigurations(endpoint);

        if (imageClient == null) {
            initializeSdkClient(endpoint, clientConfigurations);
        }

        String operation = ((ImageRecognitionEndpoint) super.getEndpoint()).getOperation();
        if (StringUtils.isEmpty(operation)) {
            throw new IllegalStateException("operation name cannot be empty");
        }
        switch (operation) {
            case ImageRecognitionConstants.OPERATION_CELEBRITY_RECOGNITION:
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Performing celebrity recognition");
                }
                performCelebrityRecognitionOperation(exchange, clientConfigurations);
                break;
            case ImageRecognitionConstants.OPERATION_TAG_RECOGNITION:
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Performing tag recognition");
                }
                performTagRecognitionOperation(exchange, clientConfigurations);
                break;
            default:
                throw new UnsupportedOperationException(
                        "operation can only be either tagRecognition or celebrityRecognition");
        }
    }

    /**
     * perform celebrity recognition
     *
     * @param exchange camel exchange
     */
    private void performCelebrityRecognitionOperation(Exchange exchange, ClientConfigurations clientConfigurations) {
        updateClientConfigurations(exchange, clientConfigurations);

        CelebrityRecognitionReq reqBody = new CelebrityRecognitionReq().withImage(clientConfigurations.getImageContent())
                .withUrl(clientConfigurations.getImageUrl())
                .withThreshold(clientConfigurations.getThreshold());

        RunCelebrityRecognitionResponse response
                = this.imageClient.runCelebrityRecognition(new RunCelebrityRecognitionRequest().withBody(reqBody));

        exchange.getMessage().setBody(response.getResult());
    }

    /**
     * perform tag recognition
     *
     * @param exchange camel exchange
     */
    private void performTagRecognitionOperation(Exchange exchange, ClientConfigurations clientConfigurations) {
        updateClientConfigurations(exchange, clientConfigurations);

        ImageTaggingReq reqBody = new ImageTaggingReq().withImage(clientConfigurations.getImageContent())
                .withUrl(clientConfigurations.getImageUrl())
                .withThreshold(clientConfigurations.getThreshold())
                .withLanguage(clientConfigurations.getTagLanguage())
                .withLimit(clientConfigurations.getTagLimit());

        RunImageTaggingResponse response = this.imageClient.runImageTagging(new RunImageTaggingRequest().withBody(reqBody));

        exchange.getMessage().setBody(response.getResult());
    }

    /**
     * Update dynamic client configurations. Some endpoint parameters (imageContent, imageUrl, tagLanguage, tagLimit and
     * threshold) can also be passed via exchange properties, so they can be updated between each transaction. Since
     * they can change, we must clear the previous transaction and update these parameters with their new values
     *
     * @param exchange
     * @param clientConfigurations
     */
    private void updateClientConfigurations(Exchange exchange, ClientConfigurations clientConfigurations) {

        boolean isImageContentSet = true;
        boolean isImageUrlSet = true;

        String imageContent = exchange.getProperty(ImageRecognitionProperties.IMAGE_CONTENT, String.class);
        if (!StringUtils.isEmpty(imageContent)) {
            clientConfigurations.setImageContent(imageContent);
        } else if (!StringUtils.isEmpty(this.endpoint.getImageContent())) {
            clientConfigurations.setImageContent(this.endpoint.getImageContent());
        } else {
            isImageContentSet = false;
        }

        String imageUrl = exchange.getProperty(ImageRecognitionProperties.IMAGE_URL, String.class);
        if (!StringUtils.isEmpty(imageUrl)) {
            clientConfigurations.setImageUrl(imageUrl);
        } else if (!StringUtils.isEmpty(this.endpoint.getImageUrl())) {
            clientConfigurations.setImageUrl(this.endpoint.getImageUrl());
        } else {
            isImageUrlSet = false;
        }
        if (!isImageContentSet && !isImageUrlSet) {
            throw new IllegalArgumentException("either image content or image url should be set");
        }

        String tagLanguageProperty = exchange.getProperty(ImageRecognitionProperties.TAG_LANGUAGE, String.class);
        clientConfigurations.setTagLanguage(
                StringUtils.isEmpty(tagLanguageProperty) ? this.endpoint.getTagLanguage() : tagLanguageProperty);
        if (!ImageRecognitionConstants.TAG_LANGUAGE_ZH.equals(clientConfigurations.getTagLanguage())
                && !ImageRecognitionConstants.TAG_LANGUAGE_EN.equals(clientConfigurations.getTagLanguage())) {
            throw new IllegalArgumentException("tag language can only be 'zh' or 'en'");
        }

        Integer tagLimitProperty = exchange.getProperty(ImageRecognitionProperties.TAG_LIMIT, Integer.class);
        clientConfigurations.setTagLimit(tagLimitProperty == null ? endpoint.getTagLimit() : tagLimitProperty);

        Float thresholdProperty = exchange.getProperty(ImageRecognitionProperties.THRESHOLD, Float.class);
        clientConfigurations.setThreshold(thresholdProperty == null ? endpoint.getThreshold() : thresholdProperty);

        if (clientConfigurations.getThreshold() == -1) {
            clientConfigurations
                    .setThreshold(ImageRecognitionConstants.OPERATION_TAG_RECOGNITION.equals(endpoint.getOperation())
                            ? ImageRecognitionConstants.DEFAULT_TAG_RECOGNITION_THRESHOLD
                            : ImageRecognitionConstants.DEFAULT_CELEBRITY_RECOGNITION_THRESHOLD);
        }
        validateThresholdValue(clientConfigurations.getThreshold(), endpoint.getOperation());
    }

    /**
     * validate threshold value. for tagRecognition, threshold should be at 0~100. for celebrityRecognition, threshold
     * should be at 0~1.
     *
     * @param threshold
     * @param operation
     */
    private void validateThresholdValue(float threshold, String operation) {
        if (ImageRecognitionConstants.OPERATION_TAG_RECOGNITION.equals(operation)) {
            if (threshold < 0 || threshold > ImageRecognitionConstants.TAG_RECOGNITION_THRESHOLD_MAX) {
                throw new IllegalArgumentException("tag recognition threshold should be at 0~100");
            }
        } else {
            if (threshold < 0 || threshold > ImageRecognitionConstants.CELEBRITY_RECOGNITION_THRESHOLD_MAX) {
                throw new IllegalArgumentException("celebrity recognition threshold should be at 0~1");
            }
        }
    }

    private String getAccessKey(ImageRecognitionEndpoint endpoint) {
        if (!StringUtils.isEmpty(endpoint.getAccessKey())) {
            return endpoint.getAccessKey();
        } else if (endpoint.getServiceKeys() != null
                && !StringUtils.isEmpty(endpoint.getServiceKeys().getAccessKey())) {
            return endpoint.getServiceKeys().getAccessKey();
        } else {
            throw new IllegalArgumentException("authentication parameter 'access key (AK)' not found");
        }
    }

    private String getSecretKey(ImageRecognitionEndpoint endpoint) {
        if (!StringUtils.isEmpty(endpoint.getSecretKey())) {
            return endpoint.getSecretKey();
        } else if (endpoint.getServiceKeys() != null
                && !StringUtils.isEmpty(endpoint.getServiceKeys().getSecretKey())) {
            return endpoint.getServiceKeys().getSecretKey();
        } else {
            throw new IllegalArgumentException("authentication parameter 'secret key (SK)' not found");
        }
    }

    private String getProjectId(ImageRecognitionEndpoint endpoint) {
        if (StringUtils.isEmpty(endpoint.getProjectId())) {
            throw new IllegalArgumentException("Project id not found");
        }
        return endpoint.getProjectId();
    }

    private String getEndpoint(ImageRecognitionEndpoint endpoint) {
        if (!StringUtils.isEmpty(endpoint.getEndpoint())) {
            return endpoint.getEndpoint();
        }
        if (StringUtils.isEmpty(endpoint.getRegion())) {
            throw new IllegalArgumentException("either endpoint or region should be set");
        }
        return ImageRegion.valueOf(endpoint.getRegion()).getEndpoint();
    }

}
